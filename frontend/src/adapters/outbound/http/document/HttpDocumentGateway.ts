import type {
  DocumentFilter,
  DocumentGateway,
  MetaPatch,
  NewDocument,
  RevisionFilter,
  SaveContentResult,
} from '../../../../application/document/ports/DocumentGateway';
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
  SaveConflict,
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

export function toDocument(value: unknown): AgentDocument {
  const source = obj(value, 'document');
  return {
    id: str(source, 'id'),
    space: toSpaceRef(source.space),
    path: str(source, 'path'),
    title: str(source, 'title'),
    status: toStatus(source),
    tags: strings(source, 'tags'),
    assignee: optStr(source, 'assignee'),
    revision: num(source, 'revision'),
    createdAt: str(source, 'createdAt'),
    updatedAt: str(source, 'updatedAt'),
    trashedAt: optStr(source, 'trashedAt'),
    blocks: list(source.blocks, toStoredBlock),
    blockChanges: source.blockChanges === undefined ? [] : list(source.blockChanges, toBlockChange),
  };
}

function toTrashed(value: unknown): TrashedDocument {
  return { ...toDocumentSummary(value), trashedAt: str(obj(value), 'trashedAt') };
}

function toRevision(value: unknown): Revision {
  const source = obj(value, 'revision');
  return {
    revision: num(source, 'revision'),
    actor: toActor(source.actor),
    source: oneOf(source, 'source', ['web', 'mcp', 'rest'] as const),
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

function toConflict(value: unknown): SaveConflict {
  const source = obj(value, 'conflict');
  return {
    blockId: str(source, 'blockId'),
    currentHash: optStr(source, 'currentHash'),
    current: toOptionalBlock(source.current),
    currentMarkdown: optStr(source, 'currentMarkdown'),
  };
}

export function toSaveResult(value: unknown): SaveContentResult {
  const source = obj(value, 'save');
  return {
    kind: 'saved',
    revision: num(source, 'revision'),
    amended: source.amended === true,
    merged: source.merged === true,
    blocks: list(source.blocks, toStoredBlock),
  };
}

export class HttpDocumentGateway implements DocumentGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async listInSpace(spaceSlug: string, filter: DocumentFilter = {}): Promise<DocumentSummary[]> {
    const url = `/api/spaces/${segment(spaceSlug)}/documents${query({ status: filter.status, tag: filter.tag })}`;
    return list(await this.http.get(url), toDocumentSummary);
  }

  async listTrash(spaceSlug: string): Promise<TrashedDocument[]> {
    return list(await this.http.get(`/api/spaces/${segment(spaceSlug)}/trash`), toTrashed);
  }

  async create(spaceSlug: string, document: NewDocument): Promise<AgentDocument> {
    return toDocument(await this.http.post(`/api/spaces/${segment(spaceSlug)}/documents`, document));
  }

  async get(documentId: string): Promise<AgentDocument> {
    return toDocument(await this.http.get(`/api/documents/${segment(documentId)}`));
  }

  async saveContent(
    documentId: string,
    baseRevision: number,
    blocks: readonly Block[],
  ): Promise<SaveContentResult> {
    try {
      return toSaveResult(
        await this.http.put(`/api/documents/${segment(documentId)}/content`, { baseRevision, blocks }),
      );
    } catch (error) {
      if (error instanceof ProblemError && error.code === 'conflict' && Array.isArray(error.body.conflicts)) {
        return { kind: 'conflict', conflicts: list(error.body.conflicts, toConflict) };
      }
      throw error;
    }
  }

  async patch(documentId: string, baseRevision: number, patch: MetaPatch): Promise<AgentDocument> {
    return toDocument(
      await this.http.patch(`/api/documents/${segment(documentId)}`, { baseRevision, ...patch }),
    );
  }

  async trash(documentId: string): Promise<void> {
    await this.http.post(`/api/documents/${segment(documentId)}/trash`);
  }

  async untrash(documentId: string): Promise<void> {
    await this.http.post(`/api/documents/${segment(documentId)}/untrash`);
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

  async restore(documentId: string, revision: number, baseRevision: number): Promise<number> {
    return num(
      obj(await this.http.post(`/api/documents/${segment(documentId)}/restore`, { revision, baseRevision })),
      'revision',
    );
  }
}
