import type { Block } from '../../domain/document/block';
import type {
  AgentDocument,
  DocumentDiff,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  TrashedDocument,
} from '../../domain/document/document';
import { PATH_RULE_TEXT, isValidPath, movedPath, normalizePath } from '../../domain/document/document';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { DocumentFilter, DocumentGateway, RevisionFilter } from './ports/DocumentGateway';

export type NewDocumentError = 'path_invalid' | 'title_required';

export const NEW_DOCUMENT_ERROR_TEXT: Record<NewDocumentError, string> = {
  path_invalid: PATH_RULE_TEXT,
  title_required: '제목을 입력하세요.',
};

export type Comparison = {
  readonly diff: DocumentDiff;
  readonly fromBlocks: readonly Block[];
  readonly toBlocks: readonly Block[];
};

export type MovableDocument = { readonly id: string; readonly path: string; readonly revision: number };

export type RecentDocument<S> = { readonly space: S; readonly document: DocumentSummary };

export interface DocumentUseCases {
  listInSpace(spaceSlug: string, filter?: DocumentFilter): Promise<DocumentSummary[]>;
  /** Recently updated documents across the given spaces, newest first, each with its space. */
  recentAcross<S extends { readonly slug: string }>(
    spaces: readonly S[],
    limit: number,
  ): Promise<RecentDocument<S>[]>;
  listTrash(spaceSlug: string): Promise<TrashedDocument[]>;
  create(spaceSlug: string, path: string, title: string): Promise<Result<AgentDocument, NewDocumentError[]>>;
  get(documentId: string): Promise<AgentDocument>;
  trash(documentId: string): Promise<void>;
  untrash(documentId: string): Promise<void>;
  destroy(documentId: string): Promise<void>;
  revisions(documentId: string, filter?: RevisionFilter): Promise<Revision[]>;
  revision(documentId: string, revision: number): Promise<RevisionSnapshot>;
  /** Diff plus both block lists so the view can place removed blocks; `to` null means current. */
  compare(documentId: string, from: number, to: number | null): Promise<Comparison>;
  restore(documentId: string, revision: number, baseRevision: number): Promise<number>;
  /** Moves a document into `folder` ('' = space root) keeping its name; null when nothing changes. */
  move(document: MovableDocument, folder: string): Promise<Result<AgentDocument | null, NewDocumentError[]>>;
}

export function createDocumentUseCases(gateway: DocumentGateway): DocumentUseCases {
  return {
    listInSpace: (spaceSlug, filter) => gateway.listInSpace(spaceSlug, filter),
    async recentAcross(spaces, limit) {
      const lists = await Promise.all(
        spaces.map(async (space) =>
          (await gateway.listInSpace(space.slug)).map((document) => ({ space, document })),
        ),
      );
      return lists
        .flat()
        .sort((a, b) => b.document.updatedAt.localeCompare(a.document.updatedAt))
        .slice(0, limit);
    },
    listTrash: (spaceSlug) => gateway.listTrash(spaceSlug),
    async create(spaceSlug, rawPath, rawTitle) {
      const path = normalizePath(rawPath);
      const title = rawTitle.trim();
      const errors: NewDocumentError[] = [];
      if (!isValidPath(path)) errors.push('path_invalid');
      if (title === '') errors.push('title_required');
      if (errors.length > 0) return failure(errors);
      return ok(await gateway.create(spaceSlug, { path, title }));
    },
    get: (documentId) => gateway.get(documentId),
    trash: (documentId) => gateway.trash(documentId),
    untrash: (documentId) => gateway.untrash(documentId),
    destroy: (documentId) => gateway.destroy(documentId),
    revisions: (documentId, filter) => gateway.revisions(documentId, filter),
    revision: (documentId, revision) => gateway.revision(documentId, revision),
    async compare(documentId, from, to) {
      const [diff, fromSnapshot, toBlocks] = await Promise.all([
        gateway.diff(documentId, from, to ?? undefined),
        gateway.revision(documentId, from),
        to === null
          ? gateway.get(documentId).then((document) => document.blocks)
          : gateway.revision(documentId, to).then((snapshot) => snapshot.blocks),
      ]);
      return { diff, fromBlocks: fromSnapshot.blocks, toBlocks };
    },
    restore: (documentId, revision, baseRevision) => gateway.restore(documentId, revision, baseRevision),
    async move(document, folder) {
      const path = movedPath(document.path, folder);
      if (path === document.path) return ok(null);
      if (!isValidPath(path)) return failure(['path_invalid']);
      return ok(await gateway.patch(document.id, document.revision, { path }));
    },
  };
}
