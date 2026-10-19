import type { Block } from '../../domain/document/block';
import { blocksEqual, withoutHash } from '../../domain/document/block';
import type { AgentDocument, DocumentMeta, SaveConflict } from '../../domain/document/document';
import type { ConflictChoice } from '../../domain/document/merge';
import { mergeAfterConflict } from '../../domain/document/merge';
import type { Clock } from '../shared/ports/Clock';
import { isApiError } from '../shared/ports/errors';
import type { DocumentGateway, MetaPatch } from './ports/DocumentGateway';

export type SaveStatus = 'saved' | 'pending' | 'saving' | 'conflict' | 'error';

/** How the last queued save ended, for callers that must not continue on unsaved content. */
export type SaveOutcome = 'saved' | 'conflict' | 'error';

export type EditorSessionState = {
  readonly revision: number;
  readonly meta: DocumentMeta;
  readonly status: SaveStatus;
  /** The last save was folded into the previous revision by the server. */
  readonly amended: boolean;
  readonly conflicts: readonly SaveConflict[];
  readonly errorMessage: string | null;
  readonly lastSavedAt: Date | null;
};

/**
 * The server stored a body that differs from what this client sent (it kept other writers' blocks).
 * `submitted` is what was sent; `server` is what is stored now.
 */
export type ServerMerge = { readonly submitted: readonly Block[]; readonly server: readonly Block[] };

function metaOf(document: AgentDocument): DocumentMeta {
  return {
    title: document.title,
    path: document.path,
    status: document.status,
    tags: document.tags,
    assignee: document.assignee,
  };
}

function sameBlocks(a: readonly Block[], b: readonly Block[]): boolean {
  return a.length === b.length && a.every((block, index) => blocksEqual(block, b[index] as Block));
}

function errorText(error: unknown, fallback: string): string {
  if (isApiError(error, 'conflict'))
    return '다른 변경과 겹쳐 저장하지 못했습니다. 새로고침 후 다시 시도하세요.';
  if (isApiError(error, 'network')) return '서버에 연결할 수 없습니다. 연결되면 다시 저장합니다.';
  return error instanceof Error && error.message !== '' ? error.message : fallback;
}

/**
 * Autosave session for one open document. Every request carries the revision this client last saw,
 * requests run one at a time, and content saves are coalesced so only the newest block list is sent.
 * When the server merged other writers' changes into a save, the session adopts the stored body and
 * rebases unsent edits onto it, so the next save does not undo those changes.
 * On a conflict the user's input is kept and saving pauses until the user picks a side per block.
 */
export class DocumentEditorSession {
  private readonly gateway: DocumentGateway;
  private readonly clock: Clock;
  private readonly documentId: string;
  private state: EditorSessionState;
  private base: readonly Block[];
  private savedMeta: DocumentMeta;
  private latest: readonly Block[] | null = null;
  private contentScheduled = false;
  /** The user typed after the last request was sent (the editor's autosave delay may still be running). */
  private editedSinceSend = false;
  private chain: Promise<void> = Promise.resolve();
  private readonly listeners = new Set<() => void>();
  private readonly mergeListeners = new Set<(merge: ServerMerge) => void>();

  constructor(gateway: DocumentGateway, clock: Clock, document: AgentDocument) {
    this.gateway = gateway;
    this.clock = clock;
    this.documentId = document.id;
    this.base = document.blocks.map(withoutHash);
    this.savedMeta = metaOf(document);
    this.state = {
      revision: document.revision,
      meta: this.savedMeta,
      status: 'saved',
      amended: false,
      conflicts: [],
      errorMessage: null,
      lastSavedAt: null,
    };
  }

  readonly getState = (): EditorSessionState => this.state;

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  /** Called when the stored body changed under the editor; the editor applies it to untouched blocks. */
  readonly onServerMerge = (listener: (merge: ServerMerge) => void): (() => void) => {
    this.mergeListeners.add(listener);
    return () => this.mergeListeners.delete(listener);
  };

  /** Blocks as the server last confirmed them. */
  baseBlocks(): readonly Block[] {
    return this.base;
  }

  /** Blocks the user currently has, including unsaved edits. */
  currentBlocks(): readonly Block[] {
    return this.latest ?? this.base;
  }

  /** Waits until every queued request has finished and reports how the content ended up. */
  async idle(): Promise<SaveOutcome> {
    await this.chain;
    if (this.state.status === 'conflict') return 'conflict';
    return this.state.status === 'error' ? 'error' : 'saved';
  }

  /** Marks the document unsaved as soon as the user types, before the autosave delay runs out. */
  markEdited(): void {
    this.editedSinceSend = true;
    if (this.state.status === 'saved') this.update({ status: 'pending' });
  }

  saveContent(blocks: readonly Block[]): Promise<SaveOutcome> {
    this.latest = blocks;
    if (this.state.status === 'conflict') return this.idle();
    if (this.state.status !== 'saving') this.update({ status: 'pending' });
    if (!this.contentScheduled) {
      this.contentScheduled = true;
      void this.enqueue(() => this.flushContent());
    }
    return this.idle();
  }

  /** Optimistic metadata change; rolled back to the last saved value when the server refuses it. */
  patchMeta(patch: MetaPatch): Promise<boolean> {
    this.update({ meta: { ...this.state.meta, ...patch } });
    let saved = false;
    return this.enqueue(async () => {
      try {
        const document = await this.gateway.patch(this.documentId, this.state.revision, patch);
        const server = document.blocks.map(withoutHash);
        if (!sameBlocks(server, this.base)) this.adopt(this.base, server);
        this.savedMeta = metaOf(document);
        this.update({ revision: document.revision, meta: this.savedMeta, errorMessage: null });
        saved = true;
      } catch (error) {
        this.update({
          meta: this.savedMeta,
          errorMessage: errorText(error, '문서 정보를 저장하지 못했습니다.'),
        });
      }
    }).then(() => saved);
  }

  /** Applies the user's choice per conflicting block and saves the merged result. */
  async resolveConflicts(
    choices: ReadonlyMap<string, ConflictChoice>,
  ): Promise<{ readonly blocks: readonly Block[]; readonly outcome: SaveOutcome }> {
    let merged: readonly Block[] = this.currentBlocks();
    await this.enqueue(async () => {
      const current = await this.gateway.get(this.documentId);
      const theirs = current.blocks.map(withoutHash);
      merged = mergeAfterConflict(this.base, this.currentBlocks(), theirs, choices);
      this.base = theirs;
      this.savedMeta = metaOf(current);
      this.update({ revision: current.revision, meta: this.savedMeta, status: 'pending', conflicts: [] });
    });
    const outcome = await this.saveContent(merged);
    return { blocks: merged, outcome };
  }

  /** Takes the stored body as the new base and rebases edits that were not sent yet onto it. */
  private adopt(submitted: readonly Block[], server: readonly Block[]): void {
    this.base = server;
    const latest = this.latest;
    this.latest =
      latest === null || latest === submitted
        ? server
        : mergeAfterConflict(submitted, latest, server, new Map());
    for (const listener of this.mergeListeners) listener({ submitted, server });
  }

  private async flushContent(): Promise<void> {
    this.contentScheduled = false;
    this.editedSinceSend = false;
    const blocks = this.latest;
    if (blocks === null || this.state.status === 'conflict') return;
    if (sameBlocks(blocks, this.base)) {
      this.update({ status: 'saved', errorMessage: null });
      return;
    }
    this.update({ status: 'saving' });
    try {
      const result = await this.gateway.saveContent(this.documentId, this.state.revision, blocks);
      if (result.kind === 'conflict') {
        this.update({ status: 'conflict', conflicts: result.conflicts });
        return;
      }
      const server = result.blocks.map(withoutHash);
      if (result.merged) this.adopt(blocks, server);
      else this.base = server;
      this.update({
        revision: result.revision,
        amended: result.amended,
        status: this.hasUnsentEdits() ? 'pending' : 'saved',
        errorMessage: null,
        lastSavedAt: this.clock.now(),
      });
    } catch (error) {
      this.update({ status: 'error', errorMessage: errorText(error, '저장하지 못했습니다.') });
    }
  }

  /** Edits arrived while the request was in flight (read after an await, so not narrowed away). */
  private hasUnsentEdits(): boolean {
    return this.contentScheduled || this.editedSinceSend;
  }

  private enqueue(task: () => Promise<void>): Promise<void> {
    this.chain = this.chain.then(task, task);
    return this.chain;
  }

  private update(patch: Partial<EditorSessionState>): void {
    this.state = { ...this.state, ...patch };
    for (const listener of this.listeners) listener();
  }
}

export function createEditorSession(
  gateway: DocumentGateway,
  clock: Clock,
  document: AgentDocument,
): DocumentEditorSession {
  return new DocumentEditorSession(gateway, clock, document);
}
