import { newFlow, normalizeFlow } from '../../../../domain/flow/flow';
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
import { isValidPath } from '../../../../domain/document/document';
import { isWithinPath } from '../../../../domain/document/pathTree';
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
  const oldOrder = from.filter((block) => toIds.has(block.id));
  const newOrder = to.filter((block) => fromById.has(block.id));
  const moved = new Set(
    newOrder.filter((block, index) => oldOrder[index]?.id !== block.id).map((block) => block.id),
  );
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
    } else if (moved.has(block.id)) {
      changes.push({ blockId: block.id, change: 'moved', before, after: block, changedCells: [] });
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
      this.store.requireEdit(space.id);
      if (this.documentsOf(space.id).some((doc) => doc.path === input.path && doc.trashedAt === null)) {
        throw new ApiError('conflict', 409, '같은 경로의 문서가 이미 있습니다.');
      }
      const initial =
        input.blocks ??
        (input.type === 'flow'
          ? newFlow(this.store.nextId('flow'), this.store.nextId('screen'), input.frame, input.idPrefix)
          : []);
      const content = input.type === 'flow' ? normalizeFlow(initial) : initial;
      if (input.type !== 'flow' && content.some((block) => block.type.startsWith('flow')))
        throw new ApiError('invalid', 400, '일반 문서에 화면 흐름 블록을 넣을 수 없습니다.');
      const now = this.store.now();
      const document: AgentDocument = {
        type: input.type ?? 'doc',
        id: this.store.nextId('doc'),
        permission: this.store.permissionIn(space.id) ?? 'read',
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
        blocks: withHashes(content),
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
      this.store.requireEdit(document.space.id);
      if (baseVersion !== document.version) {
        return { kind: 'conflict', currentVersion: document.version, currentDocument: document };
      }
      if (document.type === 'flow') blocks = normalizeFlow(blocks, document.blocks);
      else if (blocks.some((block) => block.type.startsWith('flow')))
        throw new ApiError('invalid', 400, '일반 문서에 화면 흐름 블록을 넣을 수 없습니다.');
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
      this.store.requireEdit(document.space.id);
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

  transfer(documentId: string, baseVersion: number, spaceSlug: string, path: string) {
    return respond(() => {
      const document = this.store.document(documentId);
      this.store.requireEdit(document.space.id);
      if (document.version !== baseVersion) throw new StaleDocumentError(document.version, document);
      const space = this.store.space(spaceSlug);
      this.store.requireEdit(space.id);
      if (!isValidPath(path) || (space.id === document.space.id && path.startsWith(`${document.path}/`)))
        throw new ApiError('invalid', 400, '이사 경로를 확인하세요.');
      const moving = this.documentsOf(document.space.id).filter(
        (entry) => entry.id === documentId || entry.path.startsWith(`${document.path}/`),
      );
      const ids = new Set(moving.map((entry) => entry.id));
      const targets = moving.map((entry) => ({ entry, path: path + entry.path.slice(document.path.length) }));
      if (
        targets.some((target) =>
          this.documentsOf(space.id).some((entry) => !ids.has(entry.id) && entry.path === target.path),
        )
      )
        throw new ApiError('conflict', 409, '같은 경로의 문서가 있습니다.');
      for (const target of targets) {
        const confirmed = this.confirm(target.entry.id);
        if (confirmed.space.id === space.id && confirmed.path === target.path) continue;
        const updated = {
          ...confirmed,
          space: { id: space.id, slug: space.slug, name: space.name },
          path: target.path,
          version: confirmed.version + 1,
          revision: confirmed.revision + 1,
          updatedAt: this.store.now(),
        };
        this.store.documents.set(updated.id, updated);
        this.pushRevision(updated, '문서 이사', []);
      }
      this.store.touchSpace(document.space.id);
      this.store.touchSpace(space.id);
      const updated = this.store.document(documentId);
      return {
        documentId,
        space: updated.space,
        path: updated.path,
        version: updated.version,
        revision: updated.revision,
      };
    });
  }

  finalize(documentId: string, baseVersion: number): Promise<SavedContent> {
    return respond(() => {
      const current = this.store.document(documentId);
      this.store.requireEdit(current.space.id);
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
    return respond(() => {
      const document = this.store.document(documentId);
      this.store.requireEdit(document.space.id);
      if (document.trashedAt !== null) return;
      const at = this.store.now();
      for (const candidate of this.documentsOf(document.space.id)) {
        if (candidate.trashedAt === null && isWithinPath(candidate.path, document.path)) {
          this.setTrashed(candidate.id, at);
        }
      }
    });
  }

  untrash(documentId: string): Promise<void> {
    return respond(() => {
      const document = this.store.document(documentId);
      this.store.requireEdit(document.space.id);
      if (document.trashedAt === null) return;
      for (const candidate of this.documentsOf(document.space.id)) {
        if (
          candidate.trashedAt !== null &&
          (isWithinPath(document.path, candidate.path) ||
            (candidate.trashedAt === document.trashedAt && isWithinPath(candidate.path, document.path)))
        ) {
          this.setTrashed(candidate.id, null);
        }
      }
    });
  }

  private setTrashed(documentId: string, trashedAt: string | null): void {
    const document = this.confirm(documentId);
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
      this.store.requireOwner();
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
      this.store.requireEdit(document.space.id);
      if (baseVersion !== document.version) {
        throw new StaleDocumentError(document.version, document);
      }
      const snapshot = this.snapshot(documentId, revision);
      const confirmed = this.confirm(documentId);
      const changes = computeDiff(document.blocks, snapshot.blocks);
      if (
        changes.length === 0 &&
        snapshot.title === document.title &&
        snapshot.status === document.status &&
        snapshot.assignee === document.assignee &&
        JSON.stringify(snapshot.tags) === JSON.stringify(document.tags)
      )
        return confirmed.revision;
      const updated = {
        ...document,
        blocks: snapshot.blocks,
        title: snapshot.title,
        status: snapshot.status,
        tags: snapshot.tags,
        assignee: snapshot.assignee,
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

  nameRevision(documentId: string, revision: number, name: string): Promise<string | null> {
    return respond(() => {
      this.store.requireEdit(this.store.document(documentId).space.id);
      const normalized = name.trim();
      if (normalized.length > 100 || /[\r\n]/u.test(normalized))
        throw new ApiError('invalid', 400, '리비전 이름은 한 줄로 100자까지입니다.');
      const history = this.history(documentId);
      if (!history.some((entry) => entry.meta.revision === revision))
        throw new ApiError('not_found', 404, '리비전이 없습니다.');
      this.store.revisions.set(
        documentId,
        history.map((entry) =>
          entry.meta.revision !== revision
            ? entry
            : {
                meta: { ...entry.meta, name: normalized || null },
                snapshot: { ...entry.snapshot, name: normalized || null },
              },
        ),
      );
      return normalized || null;
    });
  }

  restoreBlock(documentId: string, revision: number, blockId: string, baseVersion: number): Promise<number> {
    return respond(() => {
      const current = this.store.document(documentId);
      this.store.requireEdit(current.space.id);
      if (current.version !== baseVersion) throw new StaleDocumentError(current.version, current);
      const earlier = this.snapshot(documentId, revision).blocks;
      const original = earlier.find((block) => block.id === blockId);
      if (original === undefined) throw new ApiError('not_found', 404, '원본 블록이 없습니다.');
      let blocks = current.blocks.map((block) => (block.id === blockId ? original : block));
      if (!blocks.some((block) => block.id === blockId)) {
        const at = earlier.findIndex((block) => block.id === blockId);
        const positions = new Map(blocks.map((block, index) => [block.id, index]));
        const before = earlier
          .slice(0, at)
          .reverse()
          .find((block) => positions.has(block.id));
        const after = earlier.slice(at + 1).find((block) => positions.has(block.id));
        const index =
          before === undefined
            ? after === undefined
              ? blocks.length
              : (positions.get(after.id) ?? blocks.length)
            : (positions.get(before.id) ?? -1) + 1;
        blocks = [...blocks.slice(0, index), original, ...blocks.slice(index)];
      }
      if (JSON.stringify(blocks) === JSON.stringify(current.blocks)) return current.revision;
      const confirmed = this.confirm(documentId);
      const updated = {
        ...confirmed,
        blocks,
        version: confirmed.version + 1,
        revision: confirmed.revision + 1,
        updatedAt: this.store.now(),
      };
      this.store.documents.set(documentId, updated);
      this.pushRevision(updated, `rev ${revision} 블록 복원`, [blockId]);
      return updated.revision;
    });
  }

  private documentsOf(spaceId: string): AgentDocument[] {
    return [...this.store.documents.values()].filter((doc) => doc.space.id === spaceId);
  }

  private summary(document: AgentDocument): DocumentSummary {
    const last = this.history(document.id).at(-1);
    return {
      type: document.type ?? 'doc',
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
    this.store.document(documentId);
    return this.store.revisions.get(documentId) ?? [];
  }

  private snapshot(documentId: string, revision: number): StoredRevision['snapshot'] {
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
        status: document.status,
        tags: document.tags,
        assignee: document.assignee,
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
      status: updated.status,
      tags: updated.tags,
      assignee: updated.assignee,
      blocks,
      actor: FAKE_AGENT,
      createdAt: now,
    },
  });
  store.revisions.set(documentId, history);
}
