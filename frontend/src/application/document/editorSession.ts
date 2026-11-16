import type { InputTraceBatch } from '../../domain/statistics/statistics';
import type { Block } from '../../domain/document/block';
import { blocksEqual, withoutHash } from '../../domain/document/block';
import type { AgentDocument, DocumentMeta, SaveConflict } from '../../domain/document/document';
import type { ConflictChoice } from '../../domain/document/merge';
import { conflictingBlocks, mergeAfterConflict } from '../../domain/document/merge';
import type { Clock } from '../shared/ports/Clock';
import { isApiError } from '../shared/ports/errors';
import { StaleDocumentError } from './ports/DocumentGateway';
import type { CurrentDocument, DocumentGateway, MetaPatch } from './ports/DocumentGateway';

export type InputBatch = { readonly trace: InputTraceBatch; accepted(): void; unchanged(): void };
export type InputKind = 'body' | keyof DocumentMeta;

/**
 * Drawing input frozen at the moment the user pressed save. It rides only with the content save that holds that
 * drawing (and its retries); input drawn afterwards waits for the next drawing save.
 */
export type InputReservation = { readonly id: string };

/** The drawing block a reservation's input made: its block id and the asset pair it was given. */
export type DrawingPlacement = {
  readonly blockId: string;
  readonly svgAssetId: string;
  readonly sceneAssetId: string;
};

/** Input of one open drawing dialog, kept apart from the body so an earlier body save cannot adopt it. */
export type DrawingInputRecorder = {
  /** A real scene change at `at`; view moves, zoom, selection and the initial normalization are not input. */
  mark(drawingKey: string, at: Date): void;
  /** Freezes what was recorded so far, or null when nothing was. */
  reserve(drawingKey: string): InputReservation | null;
  /** Drops that dialog's input that never reached a successful save. */
  discard(drawingKey: string): void;
};

/**
 * Drawing input frozen for one placement. It rides with a content save only while the body sent holds that block with
 * that pair, so a drawing that was removed, or replaced by the other side of a conflict, never lends its time.
 */
export type DrawingInput = { readonly reservation: InputReservation; readonly placement: DrawingPlacement };

export type ContentSaveOptions = { readonly drawingInput?: DrawingInput };

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
  readonly revisionNameSuggestion: number | null;
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

function holds(blocks: readonly Block[], placement: DrawingPlacement): boolean {
  return blocks.some(
    (block) =>
      block.type === 'drawing' &&
      block.id === placement.blockId &&
      block.svgAssetId === placement.svgAssetId &&
      block.sceneAssetId === placement.sceneAssetId,
  );
}

function sameReservations(a: readonly InputReservation[], b: readonly InputReservation[]): boolean {
  return a.length === b.length && a.every((reservation, index) => reservation === b[index]);
}

const WRITES_CLOSED = '편집 권한을 확인하는 동안에는 저장하지 않습니다.';

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
  private suggestNameOnConfirmation = false;
  private inputBatch:
    ((kinds: readonly InputKind[], reservations: readonly InputReservation[]) => InputBatch | null) | null =
    null;
  private bodyBatch: InputBatch | null = null;
  private bodyBatchReservations: readonly InputReservation[] = [];
  /** Drawing input waiting for a successful save of a body that holds its placement. */
  private drawingInputs: readonly DrawingInput[] = [];
  /** Closed while the caller may not write: local input keeps building up and nothing is sent. */
  private writable = true;
  private inputSequence = 0;
  private bodyBatchSequence: number | null = null;
  private readonly metaBases = new Map<keyof DocumentMeta, { value: unknown; version: number }>();
  private pendingMeta: MetaPatch = {};
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
      revisionNameSuggestion: null,
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

  /** `reservations` are drawing input to send with this batch; empty for metadata and bodies without a drawing. */
  setInputBatches(
    source: (kinds: readonly InputKind[], reservations: readonly InputReservation[]) => InputBatch | null,
  ): void {
    this.inputBatch = source;
  }

  /**
   * Opens or closes server writes, for example when edit permission is taken away. Closing never drops local
   * input; reopening sends nothing by itself, so the caller refreshes first and the next save goes out as usual.
   */
  setWritable(writable: boolean): void {
    this.writable = writable;
  }

  isWritable(): boolean {
    return this.writable;
  }

  private hasUnsavedInput(): boolean {
    return (this.latest !== null && !sameBlocks(this.latest, this.base)) || this.metaBases.size > 0;
  }

  /** Capture the original field baseline before the header's debounce or blur. */
  markMetaEdited(patch: MetaPatch): void {
    for (const key of Object.keys(patch) as (keyof DocumentMeta)[]) {
      if (!this.metaBases.has(key))
        this.metaBases.set(key, { value: this.savedMeta[key], version: this.state.version });
    }
    this.pendingMeta = { ...this.pendingMeta, ...patch };
    this.lastInputAt = this.clock.now();
    this.update({
      meta: { ...this.state.meta, ...patch },
      status: this.state.status === 'saved' ? 'pending' : this.state.status,
    });
  }

  cancelMetaEdited(key: keyof DocumentMeta): void {
    this.pendingMeta = Object.fromEntries(
      Object.entries(this.pendingMeta).filter(([field]) => field !== key),
    );
    this.metaBases.delete(key);
    this.update({
      meta: { ...this.savedMeta, ...this.pendingMeta },
      status:
        this.metaBases.size === 0 && (this.latest === null || sameBlocks(this.latest, this.base))
          ? 'saved'
          : this.state.status,
    });
  }

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
    if (this.state.status === 'error' || (!this.writable && this.hasUnsavedInput())) return 'error';
    return 'saved';
  }

  /** Marks the document unsaved as soon as the user types, before the autosave delay runs out. */
  markEdited(blocks?: readonly Block[]): void {
    this.inputSequence += 1;
    if (blocks !== undefined) this.latest = blocks;
    this.lastInputAt = this.clock.now();
    this.editedSinceSend = true;
    if (this.state.status === 'saved') this.update({ status: 'pending' });
  }

  saveContent(blocks: readonly Block[], options: ContentSaveOptions = {}): Promise<SaveOutcome> {
    this.latest = blocks;
    const drawing = options.drawingInput;
    if (
      drawing !== undefined &&
      !this.drawingInputs.some((entry) => entry.reservation === drawing.reservation)
    )
      this.drawingInputs = [...this.drawingInputs, drawing];
    if (!this.writable) {
      if (this.state.status === 'saved') this.update({ status: 'pending' });
      return Promise.resolve('error');
    }
    if (this.state.status === 'conflict') return this.idle();
    if (this.state.status !== 'saving') this.update({ status: 'pending' });
    if (!this.contentScheduled) {
      this.contentScheduled = true;
      void this.enqueue(() => this.flushContent());
    }
    return this.idle();
  }

  async flushMeta(): Promise<boolean> {
    if (Object.keys(this.pendingMeta).length === 0) return true;
    return this.patchMeta(this.pendingMeta);
  }

  /** Optimistic metadata change; rolled back to the last saved value when the server refuses it. */
  patchMeta(patch: MetaPatch): Promise<boolean> {
    if (!Object.keys(patch).every((key) => this.metaBases.has(key as keyof DocumentMeta)))
      this.markMetaEdited(patch);
    if (!this.writable) {
      this.update({ meta: { ...this.state.meta, ...patch } });
      return Promise.resolve(false);
    }
    // Share baseline cells: an earlier own success must advance already queued writes too.
    const baselines = new Map(this.metaBases);
    const batch = this.inputBatch?.(Object.keys(patch) as (keyof DocumentMeta)[], []) ?? null;
    const input = { ...this.pendingMeta };
    this.update({ meta: { ...this.state.meta, ...patch } });
    let saved = false;
    return this.enqueue(async () => {
      if (!this.writable) return;
      try {
        const previousStatus = this.savedMeta.status;
        const document = await this.saveMeta(patch, baselines, batch);
        for (const key of Object.keys(patch) as (keyof DocumentMeta)[]) {
          if (JSON.stringify(this.pendingMeta[key]) === JSON.stringify(input[key])) {
            this.pendingMeta = Object.fromEntries(
              Object.entries(this.pendingMeta).filter(([field]) => field !== key),
            );
            this.metaBases.delete(key);
          } else {
            const baseline = this.metaBases.get(key);
            if (baseline !== undefined) {
              baseline.value = document[key];
              baseline.version = document.version;
            }
          }
        }
        if (previousStatus !== 'confirmed' && document.status === 'confirmed')
          this.suggestNameOnConfirmation = true;
        if (document.status !== 'confirmed') this.suggestNameOnConfirmation = false;
        this.acceptDocumentBody(document);
        this.savedMeta = metaOf(document);
        this.update({
          version: document.version,
          revision: document.revision,
          meta: { ...this.savedMeta, ...this.pendingMeta },
          status:
            this.getState().status === 'conflict'
              ? 'conflict'
              : (this.latest !== null && !sameBlocks(this.latest, this.base)) || this.metaBases.size > 0
                ? 'pending'
                : 'saved',
          errorMessage: null,
        });
        saved = true;
      } catch (error) {
        // Refused after writes closed (often for that very reason): keep the draft instead of rolling it back.
        if (!this.isWritable()) {
          this.update({
            status: this.getState().status === 'conflict' ? 'conflict' : 'error',
            errorMessage: errorText(error, '문서 정보를 저장하지 못했습니다.'),
          });
          return;
        }
        this.update({
          meta: {
            ...this.savedMeta,
            ...Object.fromEntries(
              Object.entries(this.pendingMeta).filter(
                ([key, value]) => JSON.stringify(value) !== JSON.stringify(input[key as keyof DocumentMeta]),
              ),
            ),
          },
          status: this.getState().status === 'conflict' ? 'conflict' : 'error',
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
  async refreshMeta(): Promise<void> {
    try {
      await this.refreshRemote();
    } catch (error) {
      this.update({ errorMessage: errorText(error, '문서 정보를 다시 읽지 못했습니다.') });
    }
  }

  refreshRemote(): Promise<void> {
    return this.enqueue(async () => {
      if (this.state.status === 'conflict') return;
      const document = await this.gateway.get(this.documentId);
      if (
        document.id !== this.documentId ||
        document.version < this.state.version ||
        this.getState().status === 'conflict'
      )
        return;
      const previousBase = this.base;
      const mine = this.currentBlocks();
      const theirs = document.blocks.map(withoutHash);
      const conflicts = conflictingBlocks(previousBase, mine, theirs);
      if (document.version > this.state.version) this.finalizableVersion = null;
      if (conflicts.length > 0) {
        this.update({
          status: 'conflict',
          conflicts: conflicts.map((blockId) => ({
            blockId,
            currentHash: null,
            current: theirs.find((block) => block.id === blockId) ?? null,
            currentMarkdown: null,
          })),
        });
        return;
      }
      this.adopt(previousBase, theirs);
      this.contentBaseVersion = document.version;
      this.savedMeta = metaOf(document);
      this.update({
        version: document.version,
        revision: document.revision,
        meta: { ...this.savedMeta, ...this.pendingMeta },
      });
    });
  }

  /** Applies the user's choice per conflicting block and saves the merged result. */
  async resolveConflicts(
    choices: ReadonlyMap<string, ConflictChoice>,
  ): Promise<{ readonly blocks: readonly Block[]; readonly outcome: SaveOutcome }> {
    if (!this.writable) return { blocks: this.currentBlocks(), outcome: 'error' };
    const resolution: { merged: readonly Block[] | null } = { merged: null };
    await this.enqueue(async () => {
      // Writes can close after the call but before the queue gets here, or while the stored body is read: then
      // nothing is read or replaced, and the conflict, its blocks and the base stay as they were.
      if (!this.isWritable()) return;
      const current = await this.gateway.get(this.documentId);
      if (!this.isWritable()) return;
      const theirs = current.blocks.map(withoutHash);
      const submitted = this.currentBlocks();
      const merged = mergeAfterConflict(this.base, submitted, theirs, choices);
      resolution.merged = merged;
      for (const listener of this.mergeListeners) listener({ submitted, server: merged });
      this.base = theirs;
      this.contentBaseVersion = current.version;
      this.savedMeta = metaOf(current);
      this.update({
        version: current.version,
        revision: current.revision,
        meta: { ...this.savedMeta, ...this.pendingMeta },
        status: 'pending',
        conflicts: [],
      });
    });
    const merged = resolution.merged;
    if (merged === null) return { blocks: this.currentBlocks(), outcome: 'error' };
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
    if (attempt === 0) this.contentScheduled = false;
    if (!this.writable) {
      // A retry after a stale save is not sent once writes close; the input stays for after the refresh.
      if (attempt > 0) this.update({ status: 'pending' });
      return;
    }
    if (attempt === 0) this.editedSinceSend = false;
    const blocks = this.latest;
    // Drawing input rides only with a body that holds its drawing exactly as it was saved.
    const reservations =
      blocks === null
        ? []
        : this.drawingInputs
            .filter((entry) => holds(blocks, entry.placement))
            .map((entry) => entry.reservation);
    const batch =
      this.bodyBatchSequence === this.inputSequence &&
      sameReservations(this.bodyBatchReservations, reservations)
        ? this.bodyBatch
        : (this.inputBatch?.(['body'], reservations) ?? null);
    this.bodyBatch = batch;
    this.bodyBatchSequence = this.inputSequence;
    this.bodyBatchReservations = reservations;
    if (blocks === null || this.state.status === 'conflict') return;
    if (sameBlocks(blocks, this.base)) {
      batch?.unchanged();
      this.settleBodyBatch(reservations);
      this.update({ status: this.metaBases.size > 0 ? 'pending' : 'saved', errorMessage: null });
      return;
    }
    this.update({ status: 'saving' });
    try {
      const requestedVersion = this.contentBaseVersion;
      const result = await this.gateway.saveContent(this.documentId, requestedVersion, blocks, batch?.trace);
      if (result.kind === 'conflict') {
        // Writes closed while this was in flight: keep the input and the batch, and let the caller refresh.
        if (!this.isWritable()) {
          this.update({ status: 'pending' });
          return;
        }
        if (attempt >= 3) throw new Error('문서가 계속 바뀌고 있습니다. 다시 저장하세요.');
        await this.rebaseContent(result.currentDocument, result.currentVersion, attempt + 1);
        return;
      }
      if (result.version > requestedVersion) batch?.accepted();
      else batch?.unchanged();
      this.settleBodyBatch(reservations);
      const server = result.blocks.map(withoutHash);
      this.base = server;
      this.contentBaseVersion = result.version;
      if (result.version > requestedVersion) this.finalizableVersion = result.version;
      this.update({
        revision: result.revision,
        version: result.version,
        status: this.hasUnsentEdits() || this.metaBases.size > 0 ? 'pending' : 'saved',
        errorMessage: null,
        lastSavedAt: this.clock.now(),
      });
    } catch (error) {
      this.update({ status: 'error', errorMessage: errorText(error, '저장하지 못했습니다.') });
    }
  }

  /**
   * The batch ended in a response that adopted or dropped it, and with it the drawing input it carried. Drawing input
   * that was not sent stays while the editor still holds its drawing (a later save sends it); when the drawing is gone
   * from the body too, its frozen input alone is dropped, leaving input drawn after it and other drawings' input.
   */
  private settleBodyBatch(sent: readonly InputReservation[]): void {
    this.bodyBatch = null;
    this.bodyBatchSequence = null;
    this.bodyBatchReservations = [];
    const current = this.currentBlocks();
    const gone = this.drawingInputs.filter(
      (entry) => !sent.includes(entry.reservation) && !holds(current, entry.placement),
    );
    this.drawingInputs = this.drawingInputs.filter(
      (entry) => !sent.includes(entry.reservation) && !gone.includes(entry),
    );
    if (gone.length > 0)
      this.inputBatch?.(
        [],
        gone.map((entry) => entry.reservation),
      )?.unchanged();
  }

  /** Pending editor input still belongs to the old body, even when metadata advanced the version. */
  private acceptDocumentBody(document: CurrentDocument): void {
    if (this.state.status !== 'saved') return;
    const server = document.blocks.map(withoutHash);
    if (!sameBlocks(server, this.base)) this.adopt(this.base, server);
    this.contentBaseVersion = document.version;
  }

  private async saveMeta(
    patch: MetaPatch,
    baselines: ReadonlyMap<keyof DocumentMeta, { value: unknown; version: number }>,
    batch: InputBatch | null,
  ): Promise<AgentDocument> {
    const base = this.savedMeta;
    try {
      const version = Math.min(this.state.version, ...[...baselines.values()].map((entry) => entry.version));
      return await this.writeMeta(version, patch, batch);
    } catch (error) {
      if (!(error instanceof StaleDocumentError)) throw error;
      const current = error.currentDocument;
      const keys = Object.keys(patch) as (keyof DocumentMeta)[];
      const safe = keys.every(
        (key) =>
          JSON.stringify(baselines.has(key) ? baselines.get(key)?.value : base[key]) ===
            JSON.stringify(current[key]) || JSON.stringify(patch[key]) === JSON.stringify(current[key]),
      );
      if (!safe) throw error;
      // The read made only to retry is no reason to write once writes closed: keep the draft, base and version.
      if (!this.isWritable()) throw new Error(WRITES_CLOSED, { cause: error });
      this.acceptDocumentBody(current);
      this.savedMeta = metaOf(current);
      this.update({ version: error.currentVersion, revision: current.revision });
      return this.writeMeta(error.currentVersion, patch, batch);
    }
  }

  private async writeMeta(
    version: number,
    patch: MetaPatch,
    batch: InputBatch | null,
  ): Promise<AgentDocument> {
    if (!this.isWritable()) throw new Error(WRITES_CLOSED);
    const document = await this.gateway.patch(this.documentId, version, patch, batch?.trace);
    if (document.version > version) {
      this.finalizableVersion = document.version;
      batch?.accepted();
    } else batch?.unchanged();
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
    this.update({ version, revision: current.revision, meta: { ...this.savedMeta, ...this.pendingMeta } });
    await this.flushContent(attempt);
  }

  dismissRevisionNameSuggestion(): void {
    this.update({ revisionNameSuggestion: null });
  }

  hasPendingRevision(): boolean {
    return this.finalizableVersion !== null;
  }

  finalizeDelay(): number {
    return Math.max(0, 30_000 - (this.clock.now().getTime() - this.lastInputAt.getTime()));
  }

  /** Confirms pending web input after all content requests have succeeded. */
  finalize(): Promise<void> {
    if (!this.writable) return Promise.resolve();
    return this.enqueue(async () => {
      const version = this.finalizableVersion;
      if (version === null || this.state.status !== 'saved' || !this.writable) return;
      try {
        const current = await this.gateway.finalize(this.documentId, version);
        if (this.finalizableVersion === version) this.finalizableVersion = null;
        const suggestion =
          this.suggestNameOnConfirmation &&
          this.savedMeta.status === 'confirmed' &&
          current.version === version &&
          current.revision > this.state.revision
            ? current.revision
            : null;
        if (suggestion !== null) this.suggestNameOnConfirmation = false;
        this.update({
          revision: current.revision,
          revisionNameSuggestion: suggestion ?? this.state.revisionNameSuggestion,
        });
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
