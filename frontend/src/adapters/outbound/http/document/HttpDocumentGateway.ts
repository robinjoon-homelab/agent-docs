import type {
  CurrentDocument,
  SavedContent,
  DocumentFilter,
  DocumentGateway,
  MetaPatch,
  NewDocument,
  RevisionFilter,
  SaveContentResult,
} from '../../../../application/document/ports/DocumentGateway';
import { StaleDocumentError } from '../../../../application/document/ports/DocumentGateway';
import { PathTakenError } from '../../../../application/shared/ports/errors';
import type { Block } from '../../../../domain/document/block';
import type {
  AgentDocument,
  BlockChange,
  BlockDiff,
  CellRef,
  DocumentDiff,
  DocumentSummary,
  Revision,
  RevisionSnapshot,
  TrashedDocument,
} from '../../../../domain/document/document';
import type { HttpClient } from '../shared/httpClient';
import { ProblemError, query, segment } from '../shared/httpClient';
import { list, num, obj, oneOf, optStr, str, strOr, strings } from '../shared/json';
import {
  toActor,
  toDocumentSummary,
  toOptionalBlock,
  toSpaceRef,
  toStatus,
  toStoredBlock,
} from '../shared/mappers';

function toBlockChange(value: unknown): BlockChange {
  const source = obj(value, 'blockChange');
  return {
    blockId: str(source, 'blockId'),
    actor: toActor(source.actor),
    revision: num(source, 'revision'),
    at: str(source, 'at'),
  };
}

function toCurrentDocument(value: unknown): CurrentDocument {
  const source = obj(value, 'document');
  return {
    id: str(source, 'id'),
    path: str(source, 'path'),
    title: str(source, 'title'),
    status: toStatus(source),
    tags: strings(source, 'tags'),
    assignee: optStr(source, 'assignee'),
    version: num(source, 'version'),
    revision: num(source, 'revision'),
    createdAt: str(source, 'createdAt'),
    updatedAt: str(source, 'updatedAt'),
    trashedAt: optStr(source, 'trashedAt'),
    blocks: list(source.blocks, toStoredBlock),
  };
}

export function toDocument(value: unknown): AgentDocument {
  const source = obj(value, 'document');
  return {
    ...toCurrentDocument(value),
    space: toSpaceRef(source.space),
    blockChanges: source.blockChanges === undefined ? [] : list(source.blockChanges, toBlockChange),
  };
}

function toTrashed(value: unknown): TrashedDocument {
  return { ...toDocumentSummary(value), trashedAt: str(obj(value), 'trashedAt') };
}

/** A path in use comes back as PathTakenError, so the screen can say which document is in the way. */
async function namingTakenPath<T>(request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    if (error instanceof ProblemError && error.body.code === 'taken' && typeof error.body.path === 'string') {
      throw new PathTakenError(error.body.path);
    }
    throw error;
  }
}

function toRevision(value: unknown): Revision {
  const source = obj(value, 'revision');
  return {
    revision: num(source, 'revision'),
    actor: toActor(source.actor),
    source: oneOf(source, 'source', ['web', 'mcp', 'rest', 'system'] as const),
    summary: strOr(source, 'summary', ''),
    changedBlockIds: strings(source, 'changedBlockIds'),
    createdAt: str(source, 'createdAt'),
    threadIds: strings(source, 'threadIds'),
  };
}

function toSnapshot(value: unknown): RevisionSnapshot {
  const source = obj(value, 'revision');
  return {
    revision: num(source, 'revision'),
    title: str(source, 'title'),
    path: str(source, 'path'),
    blocks: list(source.blocks, toStoredBlock),
    actor: toActor(source.actor),
    createdAt: str(source, 'createdAt'),
  };
}

function toCell(value: unknown): CellRef {
  const source = obj(value, 'cell');
  return { rowId: str(source, 'rowId'), columnId: str(source, 'columnId') };
}

function toBlockDiff(value: unknown): BlockDiff {
  const source = obj(value, 'change');
  return {
    blockId: str(source, 'blockId'),
    change: oneOf(source, 'change', ['added', 'modified', 'removed'] as const),
    before: toOptionalBlock(source.before),
    after: toOptionalBlock(source.after),
    changedCells:
      source.changedCells === undefined || source.changedCells === null
        ? []
        : list(source.changedCells, toCell),
  };
}

export function toDiff(value: unknown): DocumentDiff {
  const source = obj(value, 'diff');
  return {
    from: num(source, 'from'),
    to: num(source, 'to'),
    changes: list(source.changes, toBlockDiff),
    unified: strOr(source, 'unified', ''),
  };
}

export function toSaveResult(value: unknown): SavedContent {
  const source = obj(value, 'save');
  return {
    kind: 'saved',
    revision: num(source, 'revision'),
    version: num(source, 'version'),
    blocks: list(source.blocks, toStoredBlock),
  };
}

export class HttpDocumentGateway implements DocumentGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async listInSpace(spaceSlug: string, filter: DocumentFilter = {}): Promise<DocumentSummary[]> {
    const params = query({ status: filter.status, tag: filter.tag, folder: filter.under });
    const url = `/api/spaces/${segment(spaceSlug)}/documents${params}`;
    return list(await this.http.get(url), toDocumentSummary);
  }

  async listTrash(spaceSlug: string): Promise<TrashedDocument[]> {
    return list(await this.http.get(`/api/spaces/${segment(spaceSlug)}/trash`), toTrashed);
  }

  async create(spaceSlug: string, document: NewDocument): Promise<AgentDocument> {
    return toDocument(
      await namingTakenPath(this.http.post(`/api/spaces/${segment(spaceSlug)}/documents`, document)),
    );
  }

  async get(documentId: string): Promise<AgentDocument> {
    return toDocument(await this.http.get(`/api/documents/${segment(documentId)}`));
  }

  async saveContent(
    documentId: string,
    baseVersion: number,
    blocks: readonly Block[],
  ): Promise<SaveContentResult> {
    try {
      return toSaveResult(
        await this.http.put(`/api/documents/${segment(documentId)}/content`, { baseVersion, blocks }),
      );
    } catch (error) {
      if (
        error instanceof ProblemError &&
        error.code === 'conflict' &&
        error.body.currentDocument !== undefined
      ) {
        return {
          kind: 'conflict',
          currentVersion: num(error.body, 'currentVersion'),
          currentDocument: toCurrentDocument(error.body.currentDocument),
        };
      }
      throw error;
    }
  }

  async patch(documentId: string, baseVersion: number, patch: MetaPatch): Promise<AgentDocument> {
    try {
      return toDocument(
        await namingTakenPath(
          this.http.patch(`/api/documents/${segment(documentId)}`, { baseVersion, ...patch }),
        ),
      );
    } catch (error) {
      if (error instanceof ProblemError && error.body.currentDocument !== undefined) {
        throw new StaleDocumentError(
          num(error.body, 'currentVersion'),
          toCurrentDocument(error.body.currentDocument),
        );
      }
      throw error;
    }
  }

  async finalize(documentId: string, baseVersion: number): Promise<SavedContent> {
    return toSaveResult(
      await this.http.postKeepalive(
        `/api/documents/${segment(documentId)}/finalize?baseVersion=${baseVersion}`,
      ),
    );
  }

  async trash(documentId: string): Promise<void> {
    await this.http.post(`/api/documents/${segment(documentId)}/trash`);
  }

  async untrash(documentId: string): Promise<void> {
    await namingTakenPath(this.http.post(`/api/documents/${segment(documentId)}/untrash`));
  }

  async destroy(documentId: string): Promise<void> {
    await this.http.delete(`/api/documents/${segment(documentId)}`);
  }

  async revisions(documentId: string, filter: RevisionFilter = {}): Promise<Revision[]> {
    const params = query({ actor: filter.actor, from: filter.from, to: filter.to });
    return list(await this.http.get(`/api/documents/${segment(documentId)}/revisions${params}`), toRevision);
  }

  async revision(documentId: string, revision: number): Promise<RevisionSnapshot> {
    return toSnapshot(await this.http.get(`/api/documents/${segment(documentId)}/revisions/${revision}`));
  }

  async diff(documentId: string, from: number, to?: number): Promise<DocumentDiff> {
    return toDiff(await this.http.get(`/api/documents/${segment(documentId)}/diff${query({ from, to })}`));
  }

  async restore(documentId: string, revision: number, baseVersion: number): Promise<number> {
    return num(
      obj(await this.http.post(`/api/documents/${segment(documentId)}/restore`, { revision, baseVersion })),
      'revision',
    );
  }
}
