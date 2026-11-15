import type { Block } from '../../domain/document/block';
import { blocksEqual, withoutHash } from '../../domain/document/block';
import type { AgentDocument, DocumentMeta, SaveConflict } from '../../domain/document/document';
import type { ConflictChoice } from '../../domain/document/merge';
import { conflictingBlocks, mergeAfterConflict } from '../../domain/document/merge';
import type { Clock } from '../shared/ports/Clock';
import { isApiError } from '../shared/ports/errors';
import { StaleDocumentError } from './ports/DocumentGateway';
import type { CurrentDocument, DocumentGateway, MetaPatch } from './ports/DocumentGateway';

export type SaveStatus = 'saved' | 'pending' | 'saving' | 'conflict' | 'error';

/** How the last queued save ended, for callers that must not continue on unsaved content. */
export type SaveOutcome = 'saved' | 'conflict' | 'error';

export type EditorSessionState = {
  readonly version: number;
  readonly revision: number;
  readonly meta: DocumentMeta;
  readonly status: SaveStatus;
  readonly conflicts: readonly SaveConflict[];
  readonly errorMessage: string | null;
  readonly lastSavedAt: Date | null;
};

/**
 * A rebased body for the editor. Apply it only to blocks unchanged since `submitted`.
 */
export type ServerMerge = { readonly submitted: readonly Block[]; readonly server: readonly Block[] };

function metaOf(document: CurrentDocument): DocumentMeta {
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
 * Autosave session for one open document. Every request carries the version this client last saw,
 * requests run one at a time, and content saves are coalesced so only the newest block list is sent.
 * Stale writes are merged here against the current document before retrying.
 * On a conflict the user's input is kept and saving pauses until the user picks a side per block.
 */
export class DocumentEditorSession {
  private readonly gateway: DocumentGateway;
  private readonly clock: Clock;
  private readonly documentId: string;
  private lastInputAt: Date;
  private finalizableVersion: number | null = null;
  private contentBaseVersion: number;
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
    this.lastInputAt = clock.now();
    this.contentBaseVersion = document.version;
    this.base = document.blocks.map(withoutHash);
    this.savedMeta = metaOf(document);
    this.state = {
      version: document.version,
      revision: document.revision,
      meta: this.savedMeta,
      status: 'saved',
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
  markEdited(blocks?: readonly Block[]): void {
    if (blocks !== undefined) this.latest = blocks;
    this.lastInputAt = this.clock.now();
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
    this.lastInputAt = this.clock.now();
    this.update({ meta: { ...this.state.meta, ...patch } });
    let saved = false;
    return this.enqueue(async () => {
      try {
        const document = await this.saveMeta(patch);
        this.acceptDocumentBody(document);
        this.savedMeta = metaOf(document);
        this.update({
          version: document.version,
          revision: document.revision,
          meta: this.savedMeta,
          errorMessage: null,
        });
        saved = true;
      } catch (error) {
        this.update({
          meta: this.savedMeta,
          errorMessage: errorText(error, '문서 정보를 저장하지 못했습니다.'),
        });
      }
    }).then(() => saved);
  }

  /**
   * Reads the stored header again after a change made elsewhere, such as a move in the sidebar that took this
   * document along. A stored body that differs becomes the new base like a merged save. During a conflict nothing
   * is read: resolving it reads the current document anyway.
   */
  refreshMeta(): Promise<void> {
    return this.enqueue(async () => {
      if (this.state.status === 'conflict') return;
      try {
        const document = await this.gateway.get(this.documentId);
        this.acceptDocumentBody(document);
        this.savedMeta = metaOf(document);
        this.update({ version: document.version, revision: document.revision, meta: this.savedMeta });
      } catch (error) {
        this.update({ errorMessage: errorText(error, '문서 정보를 다시 읽지 못했습니다.') });
      }
    });
  }

  /** Applies the user's choice per conflicting block and saves the merged result. */
  async resolveConflicts(
    choices: ReadonlyMap<string, ConflictChoice>,
  ): Promise<{ readonly blocks: readonly Block[]; readonly outcome: SaveOutcome }> {
    let merged: readonly Block[] = this.currentBlocks();
    await this.enqueue(async () => {
      const current = await this.gateway.get(this.documentId);
      const theirs = current.blocks.map(withoutHash);
      const submitted = this.currentBlocks();
      merged = mergeAfterConflict(this.base, submitted, theirs, choices);
      for (const listener of this.mergeListeners) listener({ submitted, server: merged });
      this.base = theirs;
      this.contentBaseVersion = current.version;
      this.savedMeta = metaOf(current);
      this.update({
        version: current.version,
        revision: current.revision,
        meta: this.savedMeta,
        status: 'pending',
        conflicts: [],
      });
    });
    const outcome = await this.saveContent(merged);
    return { blocks: merged, outcome };
  }

  /** Takes the stored body as the new base and rebases edits that were not sent yet onto it. */
  private adopt(submitted: readonly Block[], server: readonly Block[]): void {
    const ids = conflictingBlocks(submitted, this.currentBlocks(), server);
    if (ids.length > 0) {
      this.update({
        status: 'conflict',
        conflicts: ids.map((blockId) => ({
          blockId,
          currentHash: null,
          current: server.find((block) => block.id === blockId) ?? null,
          currentMarkdown: null,
        })),
      });
      return;
    }
    this.base = server;
    const latest = this.latest;
    this.latest =
      latest === null || latest === submitted
        ? server
        : mergeAfterConflict(submitted, latest, server, new Map());
    for (const listener of this.mergeListeners) listener({ submitted, server });
  }

  private async flushContent(attempt = 0): Promise<void> {
    if (attempt === 0) {
      this.contentScheduled = false;
      this.editedSinceSend = false;
    }
    const blocks = this.latest;
    if (blocks === null || this.state.status === 'conflict') return;
    if (sameBlocks(blocks, this.base)) {
      this.update({ status: 'saved', errorMessage: null });
      return;
    }
    this.update({ status: 'saving' });
    try {
      const requestedVersion = this.contentBaseVersion;
      const result = await this.gateway.saveContent(this.documentId, requestedVersion, blocks);
      if (result.kind === 'conflict') {
        if (attempt >= 3) throw new Error('문서가 계속 바뀌고 있습니다. 다시 저장하세요.');
        await this.rebaseContent(result.currentDocument, result.currentVersion, attempt + 1);
        return;
      }
      const server = result.blocks.map(withoutHash);
      this.base = server;
      this.contentBaseVersion = result.version;
      if (result.version > requestedVersion) this.finalizableVersion = result.version;
      this.update({
        revision: result.revision,
        version: result.version,
        status: this.hasUnsentEdits() ? 'pending' : 'saved',
        errorMessage: null,
        lastSavedAt: this.clock.now(),
      });
    } catch (error) {
      this.update({ status: 'error', errorMessage: errorText(error, '저장하지 못했습니다.') });
    }
  }

  /** Pending editor input still belongs to the old body, even when metadata advanced the version. */
  private acceptDocumentBody(document: CurrentDocument): void {
    if (this.state.status !== 'saved') return;
    const server = document.blocks.map(withoutHash);
    if (!sameBlocks(server, this.base)) this.adopt(this.base, server);
    this.contentBaseVersion = document.version;
  }

  private async saveMeta(patch: MetaPatch): Promise<AgentDocument> {
    const base = this.savedMeta;
    try {
      return await this.writeMeta(this.state.version, patch);
    } catch (error) {
      if (!(error instanceof StaleDocumentError)) throw error;
      const current = error.currentDocument;
      const keys = Object.keys(patch) as (keyof DocumentMeta)[];
      const safe = keys.every(
        (key) =>
          JSON.stringify(base[key]) === JSON.stringify(current[key]) ||
          JSON.stringify(patch[key]) === JSON.stringify(current[key]),
      );
      if (!safe) throw error;
      this.acceptDocumentBody(current);
      this.savedMeta = metaOf(current);
      this.update({ version: error.currentVersion, revision: current.revision });
      return this.writeMeta(error.currentVersion, patch);
    }
  }

  private async writeMeta(version: number, patch: MetaPatch): Promise<AgentDocument> {
    const document = await this.gateway.patch(this.documentId, version, patch);
    if (document.version > version) this.finalizableVersion = document.version;
    return document;
  }

  private async rebaseContent(current: CurrentDocument, version: number, attempt: number): Promise<void> {
    const theirs = current.blocks.map(withoutHash);
    const mine = this.currentBlocks();
    const ids = conflictingBlocks(this.base, mine, theirs);
    if (ids.length > 0) {
      const conflicts = ids.map((blockId) => {
        const block = current.blocks.find((candidate) => candidate.id === blockId);
        return {
          blockId,
          currentHash: block?.hash ?? null,
          current: block === undefined ? null : withoutHash(block),
          currentMarkdown: null,
        };
      });
      this.update({ status: 'conflict', conflicts });
      return;
    }
    this.adopt(this.base, theirs);
    this.savedMeta = metaOf(current);
    this.contentBaseVersion = version;
    this.update({ version, revision: current.revision, meta: this.savedMeta });
    await this.flushContent(attempt);
  }

  hasPendingRevision(): boolean {
    return this.finalizableVersion !== null;
  }

  finalizeDelay(): number {
    return Math.max(0, 30_000 - (this.clock.now().getTime() - this.lastInputAt.getTime()));
  }

  /** Confirms pending web input after all content requests have succeeded. */
  finalize(): Promise<void> {
    return this.enqueue(async () => {
      const version = this.finalizableVersion;
      if (version === null || this.state.status !== 'saved') return;
      try {
        const current = await this.gateway.finalize(this.documentId, version);
        if (this.finalizableVersion === version) this.finalizableVersion = null;
        this.update({ revision: current.revision });
      } catch (error) {
        this.update({ errorMessage: errorText(error, '변경 기록을 확정하지 못했습니다.') });
      }
    });
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
