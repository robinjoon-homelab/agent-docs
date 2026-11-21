import { ApiError } from '../../../../application/shared/ports/errors';
import type { Block } from '../../../../domain/document/block';
import type { AgentDocument } from '../../../../domain/document/document';
import { backlinkTargets } from '../../../../domain/navigation/backlinks';
import type { MemoCommand, NavigationDocument } from '../../../../domain/navigation/navigation';
import type { MemoryStore } from '../shared/store';
import type { MemoryNavigationBackend, MemoryNavigationState } from './MemoryNavigationGateway';

/**
 * The document writes a memo needs, as the fake document gateway offers them. Memos go through the same
 * writes as the editor so hashes, versions and revisions follow the fake's one storage path.
 */
export type MemoDocumentWrites = {
  create(
    spaceSlug: string,
    document: { readonly path: string; readonly title: string; readonly blocks: readonly Block[] },
  ): Promise<{ readonly id: string }>;
  saveContent(
    documentId: string,
    baseVersion: number,
    blocks: readonly Block[],
  ): Promise<{ readonly kind: 'saved'; readonly version: number } | { readonly kind: 'conflict' }>;
  finalize(documentId: string, baseVersion: number): Promise<unknown>;
};

export function memoryNavigationState(): MemoryNavigationState {
  return { visits: new Map(), favorites: new Map(), preferences: new Map() };
}

/** Live documents of authorized spaces that still exist; archived spaces stay readable, the trash does not. */
function visibleDocuments(store: MemoryStore): AgentDocument[] {
  return [...store.documents.values()].filter(
    (document) =>
      document.trashedAt === null &&
      store.permissionIn(document.space.id) !== null &&
      store.spaces.some((space) => space.id === document.space.id),
  );
}

function entry(store: MemoryStore, document: AgentDocument): NavigationDocument {
  const space = store.spaces.find((candidate) => candidate.id === document.space.id) ?? document.space;
  return {
    id: document.id,
    spaceId: space.id,
    spaceSlug: space.slug,
    spaceName: space.name,
    path: document.path,
    type: document.type ?? 'doc',
    title: document.title,
  };
}

/** The same paragraph the HTTP gateway sends: trimmed text, line breaks kept as <br>. */
function memoBlock(command: MemoCommand): Block {
  return { id: command.blockId, type: 'paragraph', text: command.text.trim().replaceAll('\n', '<br>') };
}

async function writeOnce(
  store: MemoryStore,
  documents: MemoDocumentWrites,
  command: MemoCommand,
): Promise<string> {
  const space = store.space(command.space);
  const target = [...store.documents.values()].find(
    (document) =>
      document.space.id === space.id && document.path === command.path && document.trashedAt === null,
  );
  if (target === undefined) {
    const created = await documents.create(space.slug, {
      path: command.path,
      title: command.path.split('/').at(-1) ?? command.path,
      blocks: [memoBlock(command)],
    });
    return created.id;
  }
  // Like the server's guarded block edit: unconfirmed editor input becomes its own revision first, then the
  // appended memo is confirmed right away as the next one. Confirming keeps the version, so it stays the base.
  await documents.finalize(target.id, target.version);
  const saved = await documents.saveContent(target.id, target.version, [
    ...target.blocks,
    memoBlock(command),
  ]);
  if (saved.kind === 'conflict')
    throw new ApiError('conflict', 409, '문서가 그사이 바뀌었습니다. 다시 저장하세요.');
  await documents.finalize(target.id, saved.version);
  return target.id;
}

/**
 * What the fake navigation gateway reads from and writes to the fake workspace. A memo retried with the same
 * opId returns the first result instead of appending again; a failed write is not remembered, like the server.
 */
export function memoryNavigationBackend(
  store: MemoryStore,
  documents: MemoDocumentWrites,
  timeZone: string,
): MemoryNavigationBackend {
  const operations = new Map<string, Promise<string>>();
  return {
    userId: () => store.me.userId,
    now: () => Date.now(),
    timeZone,
    visible: () => visibleDocuments(store).map((document) => entry(store, document)),
    backlinks: (id) => {
      const origin = typeof window === 'undefined' ? null : window.location.origin;
      return visibleDocuments(store)
        .filter((document) => backlinkTargets(document.blocks, origin).has(id))
        .map((document) => entry(store, document));
    },
    writeMemo: (command) => {
      const first = operations.get(command.opId);
      if (first !== undefined) return first;
      const result = writeOnce(store, documents, command).catch((error: unknown) => {
        operations.delete(command.opId);
        throw error;
      });
      operations.set(command.opId, result);
      return result;
    },
  };
}
