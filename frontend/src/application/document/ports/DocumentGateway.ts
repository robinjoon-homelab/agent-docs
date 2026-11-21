import type { InputTracePayload } from '../../../domain/statistics/statistics';
import type { Block, StoredBlock, FlowFrame } from '../../../domain/document/block';
import type {
  AgentDocument,
  DocumentDiff,
  DocumentMeta,
  DocumentStatus,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  TrashedDocument,
} from '../../../domain/document/document';

export type NewDocument = {
  readonly type?: 'doc' | 'flow';
  readonly frame?: FlowFrame;
  readonly idPrefix?: string;
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
  readonly version: number;
  readonly revision: number;
  /** The stored document body after this save. */
  readonly blocks: readonly StoredBlock[];
};

export type CurrentDocument = Omit<AgentDocument, 'space' | 'blockChanges'>;

export type ContentConflict = {
  readonly kind: 'conflict';
  readonly currentVersion: number;
  readonly currentDocument: CurrentDocument;
};

export class StaleDocumentError extends Error {
  constructor(
    readonly currentVersion: number,
    readonly currentDocument: CurrentDocument,
  ) {
    super('문서가 그사이 바뀌었습니다.');
  }
}

export type SaveContentResult = SavedContent | ContentConflict;

export type ActorFilter = 'human' | 'agent';

/** Revision list filter; `from`/`to` are ISO-8601 instants. */
export type RevisionFilter = { readonly actor?: ActorFilter; readonly from?: string; readonly to?: string };

export interface DocumentGateway {
  listInSpace(spaceSlug: string, filter?: DocumentFilter): Promise<DocumentSummary[]>;
  listTrash(spaceSlug: string): Promise<TrashedDocument[]>;
  create(spaceSlug: string, document: NewDocument): Promise<AgentDocument>;
  get(documentId: string): Promise<AgentDocument>;
  /** Returns the current document when the base version is stale. */
  saveContent(
    documentId: string,
    baseVersion: number,
    blocks: readonly Block[],
    inputTrace?: InputTracePayload,
  ): Promise<SaveContentResult>;
  patch(
    documentId: string,
    baseVersion: number,
    patch: MetaPatch,
    inputTrace?: InputTracePayload,
  ): Promise<AgentDocument>;
  transfer(documentId: string, baseVersion: number, space: string, path: string): Promise<TransferResult>;
  finalize(documentId: string, baseVersion: number): Promise<SavedContent>;
  trash(documentId: string): Promise<void>;
  untrash(documentId: string): Promise<void>;
  destroy(documentId: string): Promise<void>;
  revisions(documentId: string, filter?: RevisionFilter): Promise<Revision[]>;
  revision(documentId: string, revision: number): Promise<RevisionSnapshot>;
  /** `to` omitted compares with the current document. */
  diff(documentId: string, from: number, to?: number): Promise<DocumentDiff>;
  /** `baseVersion` is the document version the user was looking at; stale writes are rejected. */
  restore(documentId: string, revision: number, baseVersion: number): Promise<number>;
  nameRevision(documentId: string, revision: number, name: string): Promise<string | null>;
  restoreBlock(documentId: string, revision: number, blockId: string, baseVersion: number): Promise<number>;
}

export type TransferResult = {
  readonly documentId: string;
  readonly space: AgentDocument['space'];
  readonly path: string;
  readonly version: number;
  readonly revision: number;
};
