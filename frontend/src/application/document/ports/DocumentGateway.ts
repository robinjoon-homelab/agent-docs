import type { Block, StoredBlock } from '../../../domain/document/block';
import type {
  AgentDocument,
  DocumentDiff,
  DocumentMeta,
  DocumentStatus,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  SaveConflict,
  TrashedDocument,
} from '../../../domain/document/document';

export type NewDocument = {
  readonly path: string;
  readonly title: string;
  readonly blocks?: readonly Block[];
  readonly status?: DocumentStatus;
  readonly tags?: readonly string[];
  readonly assignee?: string | null;
};

/** `under` keeps the documents under that document's path. */
export type DocumentFilter = {
  readonly status?: DocumentStatus;
  readonly tag?: string;
  readonly under?: string;
};

export type MetaPatch = Partial<DocumentMeta>;

export type SavedContent = {
  readonly kind: 'saved';
  readonly revision: number;
  /** The server folded this save into the previous revision (same person within 30 seconds). */
  readonly amended: boolean;
  /**
   * The save was based on an older revision and the server kept other writers' blocks; `blocks` then
   * differ from what was sent and become the new base.
   */
  readonly merged: boolean;
  /** The stored document body after this save. */
  readonly blocks: readonly StoredBlock[];
};

export type ContentConflict = { readonly kind: 'conflict'; readonly conflicts: readonly SaveConflict[] };

export type SaveContentResult = SavedContent | ContentConflict;

export type ActorFilter = 'human' | 'agent';

/** Revision list filter; `from`/`to` are ISO-8601 instants. */
export type RevisionFilter = { readonly actor?: ActorFilter; readonly from?: string; readonly to?: string };

export interface DocumentGateway {
  listInSpace(spaceSlug: string, filter?: DocumentFilter): Promise<DocumentSummary[]>;
  listTrash(spaceSlug: string): Promise<TrashedDocument[]>;
  create(spaceSlug: string, document: NewDocument): Promise<AgentDocument>;
  get(documentId: string): Promise<AgentDocument>;
  /** Returns a conflict result (not an error) when someone changed the same blocks first. */
  saveContent(documentId: string, baseRevision: number, blocks: readonly Block[]): Promise<SaveContentResult>;
  patch(documentId: string, baseRevision: number, patch: MetaPatch): Promise<AgentDocument>;
  trash(documentId: string): Promise<void>;
  untrash(documentId: string): Promise<void>;
  destroy(documentId: string): Promise<void>;
  revisions(documentId: string, filter?: RevisionFilter): Promise<Revision[]>;
  revision(documentId: string, revision: number): Promise<RevisionSnapshot>;
  /** `to` omitted compares with the current document. */
  diff(documentId: string, from: number, to?: number): Promise<DocumentDiff>;
  /** `baseRevision` is the revision the user was looking at; a stale one is rejected with a conflict. */
  restore(documentId: string, revision: number, baseRevision: number): Promise<number>;
}
