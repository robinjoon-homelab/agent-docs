import type {
  DocumentFilter,
  DocumentGateway,
  MetaPatch,
  NewDocument,
  RevisionFilter,
  SaveContentResult,
} from '../../../../application/document/ports/DocumentGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type { Block, StoredBlock } from '../../../../domain/document/block';
import { blocksEqual, withoutHash } from '../../../../domain/document/block';
import { mergeAfterConflict } from '../../../../domain/document/merge';
import { blockPreview } from '../../../../domain/document/blockText';
import type {
  AgentDocument,
  BlockDiff,
  CellRef,
  DocumentDiff,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  SaveConflict,
  TrashedDocument,
} from '../../../../domain/document/document';
import type { Actor } from '../../../../domain/shared/actor';
import type { MemoryStore, StoredRevision } from '../shared/store';
import { respond } from '../shared/store';

const AMEND_WINDOW_MS = 30_000;

function hashOf(block: Block): string {
  let hash = 0x811c9dc5;
  for (const char of JSON.stringify(withoutHash(block))) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, '0');
}

function withHashes(blocks: readonly Block[]): StoredBlock[] {
  return blocks.map((block) => ({ ...withoutHash(block), hash: hashOf(block) }));
}

function changedCells(before: Block, after: Block): CellRef[] {
  if (before.type !== 'table' || after.type !== 'table') return [];
  const cells: CellRef[] = [];
  for (const row of after.rows) {
    const old = before.rows.find((candidate) => candidate.id === row.id);
    after.columns.forEach((column, index) => {
      const oldIndex = before.columns.findIndex((candidate) => candidate.id === column.id);
      if (old === undefined || oldIndex === -1 || old.cells[oldIndex] !== row.cells[index]) {
        cells.push({ rowId: row.id, columnId: column.id });
      }
    });
  }
  return cells;
}

function computeDiff(from: readonly Block[], to: readonly Block[]): BlockDiff[] {
  const fromById = new Map(from.map((block) => [block.id, block]));
  const toIds = new Set(to.map((block) => block.id));
  const changes: BlockDiff[] = [];
  for (const block of to) {
    const before = fromById.get(block.id);
    if (before === undefined)
      changes.push({ blockId: block.id, change: 'added', before: null, after: block, changedCells: [] });
    else if (!blocksEqual(before, block)) {
      changes.push({
        blockId: block.id,
        change: 'modified',
        before,
        after: block,
        changedCells: changedCells(before, block),
      });
    }
  }
  for (const block of from) {
    if (!toIds.has(block.id))
      changes.push({ blockId: block.id, change: 'removed', before: block, after: null, changedCells: [] });
  }
  return changes;
}

function unified(from: number, to: number, changes: readonly BlockDiff[]): string {
  const lines = [`--- rev ${from}`, `+++ rev ${to}`];
  for (const change of changes) {
    if (change.before !== null) lines.push(`- ${blockPreview(change.before, 200)}`);
    if (change.after !== null) lines.push(`+ ${blockPreview(change.after, 200)}`);
  }
  return `${lines.join('\n')}\n`;
}

export class MemoryDocumentGateway implements DocumentGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  listInSpace(spaceSlug: string, filter: DocumentFilter = {}): Promise<DocumentSummary[]> {
    return respond(() => {
      const space = this.store.space(spaceSlug);
      return this.documentsOf(space.id)
        .filter((doc) => doc.trashedAt === null)
        .filter((doc) => filter.status === undefined || doc.status === filter.status)
        .filter((doc) => filter.tag === undefined || doc.tags.includes(filter.tag))
        .filter((doc) => filter.under === undefined || doc.path.startsWith(`${filter.under}/`))
        .map((doc) => this.summary(doc));
    });
  }

  listTrash(spaceSlug: string): Promise<TrashedDocument[]> {
    return respond(() => {
      const space = this.store.space(spaceSlug);
      return this.documentsOf(space.id).flatMap((doc) =>
        doc.trashedAt === null ? [] : [{ ...this.summary(doc), trashedAt: doc.trashedAt }],
      );
    });
  }

  create(spaceSlug: string, input: NewDocument): Promise<AgentDocument> {
    return respond(() => {
      const space = this.store.space(spaceSlug);
      if (this.documentsOf(space.id).some((doc) => doc.path === input.path && doc.trashedAt === null)) {
        throw new ApiError('conflict', 409, '같은 경로의 문서가 이미 있습니다.');
      }
      const now = this.store.now();
      const document: AgentDocument = {
        id: this.store.nextId('doc'),
        space: { id: space.id, slug: space.slug, name: space.name },
        path: input.path,
        title: input.title,
        status: input.status ?? 'draft',
        tags: input.tags ?? [],
        assignee: input.assignee ?? null,
        revision: 1,
        createdAt: now,
        updatedAt: now,
        trashedAt: null,
        blocks: withHashes(input.blocks ?? []),
        blockChanges: [],
      };
      this.store.documents.set(document.id, document);
      this.pushRevision(document, '문서 생성', []);
      this.store.touchSpace(space.id);
      this.store.record({
        kind: 'document_created',
        space: space,
        documentId: document.id,
        documentTitle: document.title,
        threadId: null,
        revision: 1,
        summary: `새 문서: ${document.title}`,
      });
      return document;
    });
  }

  get(documentId: string): Promise<AgentDocument> {
    return respond(() => this.store.document(documentId));
  }

  saveContent(
    documentId: string,
    baseRevision: number,
    blocks: readonly Block[],
  ): Promise<SaveContentResult> {
    return respond((): SaveContentResult => {
      const document = this.store.document(documentId);
      const conflicts = this.conflicts(document, baseRevision, blocks);
      if (conflicts.length > 0) return { kind: 'conflict', conflicts };
      // Like the server: a save based on an older revision keeps other writers' non-conflicting blocks.
      const merged = baseRevision !== document.revision;
      const body = merged
        ? mergeAfterConflict(
            this.blocksAt(documentId, baseRevision),
            blocks,
            document.blocks.map(withoutHash),
            new Map(),
          )
        : blocks;
      const changes = computeDiff(document.blocks, body);
      const stored = withHashes(body);
      const amended = !merged && this.amendable(document);
      const revision = amended ? document.revision : document.revision + 1;
      const now = this.store.now();
      const changedIds = new Set(changes.map((change) => change.blockId));
      const updated: AgentDocument = {
        ...document,
        blocks: stored,
        revision,
        updatedAt: now,
        blockChanges: [
          ...document.blockChanges.filter((change) => !changedIds.has(change.blockId)),
          ...[...changedIds].map((blockId) => ({ blockId, actor: this.store.actor, revision, at: now })),
        ],
      };
      this.store.documents.set(documentId, updated);
      if (amended) this.replaceLastRevision(updated, changes);
      else
        this.pushRevision(
          updated,
          `블록 ${changes.length}개 변경`,
          changes.map((change) => change.blockId),
        );
      this.store.touchSpace(document.space.id);
      return { kind: 'saved', revision, amended, merged, blocks: stored };
    });
  }

  patch(documentId: string, baseRevision: number, patch: MetaPatch): Promise<AgentDocument> {
    return respond(() => {
      const document = this.store.document(documentId);
      if (this.metaConflicts(document, baseRevision, patch)) {
        throw new ApiError('conflict', 409, '같은 항목을 다른 사람이 먼저 바꿨습니다.');
      }
      const updated = { ...document, ...patch, revision: document.revision + 1, updatedAt: this.store.now() };
      this.store.documents.set(documentId, updated);
      this.pushRevision(updated, '문서 정보 변경', []);
      return updated;
    });
  }

  trash(documentId: string): Promise<void> {
    return respond(() => {
      this.store.documents.set(documentId, {
        ...this.store.document(documentId),
        trashedAt: this.store.now(),
      });
    });
  }

  untrash(documentId: string): Promise<void> {
    return respond(() => {
      this.store.documents.set(documentId, { ...this.store.document(documentId), trashedAt: null });
    });
  }

  destroy(documentId: string): Promise<void> {
    return respond(() => {
      if (this.store.document(documentId).trashedAt === null) {
        throw new ApiError('invalid', 400, '휴지통에 있는 문서만 영구 삭제할 수 있습니다.');
      }
      this.store.documents.delete(documentId);
    });
  }

  revisions(documentId: string, filter: RevisionFilter = {}): Promise<Revision[]> {
    return respond(() =>
      this.history(documentId)
        .map((entry) => entry.meta)
        .filter((meta) => filter.actor === undefined || meta.actor.kind === filter.actor)
        .filter((meta) => filter.from === undefined || meta.createdAt >= filter.from)
        .filter((meta) => filter.to === undefined || meta.createdAt <= filter.to)
        .reverse(),
    );
  }

  revision(documentId: string, revision: number): Promise<RevisionSnapshot> {
    return respond(() => this.snapshot(documentId, revision));
  }

  diff(documentId: string, from: number, to?: number): Promise<DocumentDiff> {
    return respond(() => {
      const document = this.store.document(documentId);
      const target = to ?? document.revision;
      const fromBlocks = this.snapshot(documentId, from).blocks;
      const toBlocks = to === undefined ? document.blocks : this.snapshot(documentId, to).blocks;
      const changes = computeDiff(fromBlocks.map(withoutHash), toBlocks.map(withoutHash));
      return { from, to: target, changes, unified: unified(from, target, changes) };
    });
  }

  restore(documentId: string, revision: number, baseRevision: number): Promise<number> {
    return respond(() => {
      const document = this.store.document(documentId);
      if (baseRevision !== document.revision) {
        throw new ApiError('conflict', 409, '문서가 그사이 바뀌었습니다. 기록을 새로 불러오세요.');
      }
      const snapshot = this.snapshot(documentId, revision);
      const changes = computeDiff(document.blocks, snapshot.blocks);
      const updated = {
        ...document,
        blocks: snapshot.blocks,
        revision: document.revision + 1,
        updatedAt: this.store.now(),
      };
      this.store.documents.set(documentId, updated);
      this.pushRevision(
        updated,
        `rev ${revision}(으)로 복원`,
        changes.map((change) => change.blockId),
      );
      return updated.revision;
    });
  }

  private documentsOf(spaceId: string): AgentDocument[] {
    return [...this.store.documents.values()].filter((doc) => doc.space.id === spaceId);
  }

  private summary(document: AgentDocument): DocumentSummary {
    const last = this.history(document.id).at(-1);
    return {
      id: document.id,
      path: document.path,
      title: document.title,
      status: document.status,
      tags: document.tags,
      assignee: document.assignee,
      revision: document.revision,
      updatedAt: document.updatedAt,
      updatedBy: last?.meta.actor ?? this.store.actor,
    };
  }

  private history(documentId: string): StoredRevision[] {
    return this.store.revisions.get(documentId) ?? [];
  }

  private snapshot(documentId: string, revision: number): RevisionSnapshot {
    const entry = this.history(documentId).find((candidate) => candidate.meta.revision === revision);
    if (entry === undefined) throw new ApiError('not_found', 404, `rev ${revision}을 찾을 수 없습니다.`);
    return entry.snapshot;
  }

  /** Blocks someone else changed after `baseRevision` that this save also changed differently. */
  private conflicts(document: AgentDocument, baseRevision: number, blocks: readonly Block[]): SaveConflict[] {
    if (baseRevision === document.revision) return [];
    const base =
      this.history(document.id).find((entry) => entry.meta.revision === baseRevision)?.snapshot.blocks ?? [];
    const baseById = new Map(base.map((block) => [block.id, block]));
    return document.blocks
      .filter((current) => {
        const before = baseById.get(current.id);
        const mine = blocks.find((block) => block.id === current.id);
        // A conflict needs both sides to have changed the block, to different results.
        const theyChanged = before === undefined || !blocksEqual(before, current);
        const iChanged = mine !== undefined && (before === undefined || !blocksEqual(before, mine));
        return mine !== undefined && theyChanged && iChanged && !blocksEqual(mine, current);
      })
      .map((current) => ({
        blockId: current.id,
        currentHash: current.hash,
        current: withoutHash(current),
        currentMarkdown: null,
      }));
  }

  private blocksAt(documentId: string, revision: number): Block[] {
    const entry = this.history(documentId).find((candidate) => candidate.meta.revision === revision);
    return (entry?.snapshot.blocks ?? []).map(withoutHash);
  }

  /** A stale metadata change conflicts only when someone else changed the same field since then. */
  private metaConflicts(document: AgentDocument, baseRevision: number, patch: MetaPatch): boolean {
    if (baseRevision === document.revision) return false;
    const base = this.history(document.id).find((entry) => entry.meta.revision === baseRevision)?.snapshot;
    if (base === undefined) return true;
    return (
      (patch.title !== undefined && base.title !== document.title) ||
      (patch.path !== undefined && base.path !== document.path)
    );
  }

  private amendable(document: AgentDocument): boolean {
    const last = this.history(document.id).at(-1)?.meta;
    if (last === undefined || last.actor.kind !== 'human' || last.revision !== document.revision)
      return false;
    return (
      Date.now() - new Date(document.updatedAt).getTime() < AMEND_WINDOW_MS && last.summary.startsWith('블록')
    );
  }

  private pushRevision(document: AgentDocument, summary: string, changedBlockIds: readonly string[]): void {
    const history = this.history(document.id);
    const createdAt = this.store.now();
    history.push({
      meta: {
        revision: document.revision,
        actor: this.store.actor,
        source: 'web',
        summary,
        changedBlockIds,
        createdAt,
        threadIds: [],
      },
      snapshot: {
        revision: document.revision,
        title: document.title,
        path: document.path,
        blocks: document.blocks,
        actor: this.store.actor,
        createdAt,
      },
    });
    this.store.revisions.set(document.id, history);
  }

  private replaceLastRevision(document: AgentDocument, changes: readonly BlockDiff[]): void {
    const history = this.history(document.id);
    const last = history.pop();
    if (last === undefined) return;
    const changedBlockIds = [
      ...new Set([...last.meta.changedBlockIds, ...changes.map((change) => change.blockId)]),
    ];
    history.push({
      meta: { ...last.meta, changedBlockIds, summary: `블록 ${changedBlockIds.length}개 변경` },
      snapshot: { ...last.snapshot, blocks: document.blocks },
    });
  }
}

const FAKE_AGENT: Actor = {
  kind: 'agent',
  login: 'robinjoon',
  agent: { connectionId: 'conn-claude', clientName: 'Claude Code' },
};

/**
 * Local-preview helper: an agent rewrites one block's text as a new revision, so the conflict card and
 * the agent change marker can be tried without a backend.
 */
export function simulateAgentEdit(
  store: MemoryStore,
  documentId: string,
  blockId: string,
  text: string,
): void {
  const document = store.document(documentId);
  const now = store.now();
  const revision = document.revision + 1;
  const blocks = document.blocks.map((block) =>
    block.id === blockId && 'text' in block ? { ...block, text, hash: hashOf({ ...block, text }) } : block,
  );
  const updated: AgentDocument = {
    ...document,
    blocks,
    revision,
    updatedAt: now,
    blockChanges: [
      ...document.blockChanges.filter((change) => change.blockId !== blockId),
      { blockId, actor: FAKE_AGENT, revision, at: now },
    ],
  };
  store.documents.set(documentId, updated);
  const history = store.revisions.get(documentId) ?? [];
  history.push({
    meta: {
      revision,
      actor: FAKE_AGENT,
      source: 'mcp',
      summary: '에이전트 수정',
      changedBlockIds: [blockId],
      createdAt: now,
      threadIds: [],
    },
    snapshot: {
      revision,
      title: updated.title,
      path: updated.path,
      blocks,
      actor: FAKE_AGENT,
      createdAt: now,
    },
  });
  store.revisions.set(documentId, history);
}
