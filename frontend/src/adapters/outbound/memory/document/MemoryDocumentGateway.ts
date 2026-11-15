import type {
  DocumentFilter,
  DocumentGateway,
  MetaPatch,
  NewDocument,
  RevisionFilter,
  SaveContentResult,
  SavedContent,
} from '../../../../application/document/ports/DocumentGateway';
import { StaleDocumentError } from '../../../../application/document/ports/DocumentGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type { Block, StoredBlock } from '../../../../domain/document/block';
import { blocksEqual, withoutHash } from '../../../../domain/document/block';
import { blockPreview } from '../../../../domain/document/blockText';
import type {
  AgentDocument,
  BlockDiff,
  CellRef,
  DocumentDiff,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  TrashedDocument,
} from '../../../../domain/document/document';
import type { Actor } from '../../../../domain/shared/actor';
import type { MemoryStore, StoredRevision } from '../shared/store';
import { respond } from '../shared/store';

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
  private readonly pending = new Set<string>();

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
        version: 1,
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

  saveContent(documentId: string, baseVersion: number, blocks: readonly Block[]): Promise<SaveContentResult> {
    return respond((): SaveContentResult => {
      const document = this.store.document(documentId);
      if (baseVersion !== document.version) {
        return { kind: 'conflict', currentVersion: document.version, currentDocument: document };
      }
      const changes = computeDiff(document.blocks, blocks);
      if (JSON.stringify(document.blocks.map(withoutHash)) === JSON.stringify(blocks.map(withoutHash))) {
        return {
          kind: 'saved',
          version: document.version,
          revision: document.revision,
          blocks: document.blocks,
        };
      }
      const stored = withHashes(blocks);
      const revision = document.revision;
      const now = this.store.now();
      const changedIds = new Set(changes.map((change) => change.blockId));
      const updated: AgentDocument = {
        ...document,
        blocks: stored,
        version: document.version + 1,
        revision,
        updatedAt: now,
        blockChanges: [
          ...document.blockChanges.filter((change) => !changedIds.has(change.blockId)),
          ...[...changedIds].map((blockId) => ({ blockId, actor: this.store.actor, revision, at: now })),
        ],
      };
      this.store.documents.set(documentId, updated);
      this.pending.add(documentId);
      this.store.touchSpace(document.space.id);
      return { kind: 'saved', version: updated.version, revision, blocks: stored };
    });
  }

  patch(documentId: string, baseVersion: number, patch: MetaPatch): Promise<AgentDocument> {
    return respond(() => {
      const document = this.store.document(documentId);
      if (baseVersion !== document.version) throw new StaleDocumentError(document.version, document);
      if (
        Object.entries(patch).every(
          ([key, value]) => JSON.stringify(document[key as keyof AgentDocument]) === JSON.stringify(value),
        )
      )
        return document;
      const updated = { ...document, ...patch, version: document.version + 1, updatedAt: this.store.now() };
      this.store.documents.set(documentId, updated);
      this.pending.add(documentId);
      return updated;
    });
  }

  finalize(documentId: string, baseVersion: number): Promise<SavedContent> {
    return respond(() => {
      const current = this.store.document(documentId);
      const document = current.version === baseVersion ? this.confirm(documentId) : current;
      return {
        kind: 'saved',
        version: document.version,
        revision: document.revision,
        blocks: document.blocks,
      };
    });
  }

  private confirm(documentId: string): AgentDocument {
    const document = this.store.document(documentId);
    if (!this.pending.delete(documentId)) return document;
    const updated = { ...document, revision: document.revision + 1 };
    this.store.documents.set(documentId, updated);
    this.pushRevision(updated, '문서 편집', []);
    return updated;
  }

  trash(documentId: string): Promise<void> {
    return respond(() => this.setTrashed(documentId, this.store.now()));
  }

  untrash(documentId: string): Promise<void> {
    return respond(() => this.setTrashed(documentId, null));
  }

  private setTrashed(documentId: string, trashedAt: string | null): void {
    const document = this.confirm(documentId);
    if ((document.trashedAt === null) === (trashedAt === null)) return;
    const updated = {
      ...document,
      trashedAt,
      version: document.version + 1,
      revision: document.revision + 1,
      updatedAt: this.store.now(),
    };
    this.store.documents.set(documentId, updated);
    this.pushRevision(updated, trashedAt === null ? '휴지통에서 복원' : '휴지통으로 이동', []);
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

  restore(documentId: string, revision: number, baseVersion: number): Promise<number> {
    return respond(() => {
      const document = this.store.document(documentId);
      if (baseVersion !== document.version) {
        throw new StaleDocumentError(document.version, document);
      }
      const confirmed = this.confirm(documentId);
      const snapshot = this.snapshot(documentId, revision);
      const changes = computeDiff(document.blocks, snapshot.blocks);
      const updated = {
        ...document,
        blocks: snapshot.blocks,
        version: document.version + 1,
        revision: confirmed.revision + 1,
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
      version: document.version,
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
    version: document.version + 1,
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
