import type { ThreadGateway, ThreadPatch } from '../../../../application/thread/ports/ThreadGateway';
import type {
  Anchor,
  Comment,
  Resolution,
  Thread,
  ThreadStatus,
  SuggestionInput,
  Suggestion,
} from '../../../../domain/thread/thread';
import type { HttpClient } from '../shared/httpClient';
import { query, segment } from '../shared/httpClient';
import { bool, list, num, numbers, obj, oneOf, optNum, optStr, str } from '../shared/json';
import { toActor } from '../shared/mappers';

const STATUSES: readonly ThreadStatus[] = ['open', 'answered', 'resolved'];

export function toAnchor(value: unknown): Anchor {
  const source = obj(value, 'anchor');
  const kind = oneOf(source, 'kind', ['text', 'cell', 'block', 'document'] as const);
  switch (kind) {
    case 'text':
      return {
        kind,
        blockId: str(source, 'blockId'),
        start: num(source, 'start'),
        end: num(source, 'end'),
        quote: str(source, 'quote'),
      };
    case 'cell':
      return {
        kind,
        blockId: str(source, 'blockId'),
        rowId: str(source, 'rowId'),
        columnId: str(source, 'columnId'),
        quote: str(source, 'quote'),
      };
    case 'block':
      return { kind, blockId: str(source, 'blockId'), quote: optStr(source, 'quote') ?? '' };
    case 'document':
      return { kind };
  }
}

function toResolution(value: unknown): Resolution {
  if (value === undefined || value === null) return { state: 'attached' };
  const source = obj(value, 'resolution');
  const state = oneOf(source, 'state', ['attached', 'detached'] as const);
  const start = optNum(source, 'start');
  const end = optNum(source, 'end');
  return start !== null && end !== null ? { state, start, end } : { state };
}

export function toComment(value: unknown): Comment {
  const source = obj(value, 'comment');
  return {
    id: str(source, 'id'),
    author: toActor(source.author),
    body: typeof source.body === 'string' ? source.body : '',
    createdAt: str(source, 'createdAt'),
    editedAt: optStr(source, 'editedAt'),
    deleted: bool(source, 'deleted'),
  };
}

function toClaim(value: unknown) {
  const source = obj(value, 'claim');
  return {
    actor: toActor(source.actor),
    acquiredAt: str(source, 'acquiredAt'),
    expiresAt: str(source, 'expiresAt'),
  };
}

function toSuggestion(value: unknown): Suggestion {
  const source = obj(value, 'suggestion');
  const decision = source.decision == null ? null : obj(source.decision, 'decision');
  return {
    commentId: str(source, 'commentId'),
    anchor: toAnchor(source.anchor),
    hash: str(source, 'hash'),
    before: str(source, 'before'),
    after: str(source, 'after'),
    decision:
      decision === null
        ? null
        : {
            actor: toActor(decision.actor),
            at: str(decision, 'at'),
            accepted: bool(decision, 'accepted'),
            revision: optNum(decision, 'revision'),
          },
  };
}

export function toThread(value: unknown): Thread {
  const source = obj(value, 'thread');
  return {
    claim: source.claim == null ? null : toClaim(source.claim),
    reactions:
      source.reactions == null
        ? []
        : list(source.reactions, (value) => {
            const item = obj(value, 'reaction');
            return { actor: toActor(item.actor), emoji: str(item, 'emoji') };
          }),
    suggestions: source.suggestions == null ? [] : list(source.suggestions, toSuggestion),
    id: str(source, 'id'),
    documentId: str(source, 'documentId'),
    anchor: toAnchor(source.anchor),
    resolution: toResolution(source.resolution),
    status: oneOf(source, 'status', STATUSES),
    author: toActor(source.author),
    comments: list(source.comments, toComment),
    linkedRevisions: numbers(source, 'linkedRevisions'),
    createdAt: str(source, 'createdAt'),
    updatedAt: str(source, 'updatedAt'),
  };
}

export class HttpThreadGateway implements ThreadGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async react(threadId: string, emoji: string, add: boolean): Promise<Thread> {
    return toThread(
      await this.http.post(`/api/threads/${segment(threadId)}/reactions`, {
        emoji,
        action: add ? 'add' : 'remove',
      }),
    );
  }
  async claim(threadId: string, action: 'acquire' | 'renew' | 'release'): Promise<Thread> {
    return toThread(await this.http.post(`/api/threads/${segment(threadId)}/claim`, { action }));
  }
  async suggest(threadId: string, input: SuggestionInput): Promise<Thread> {
    return toThread(await this.http.post(`/api/threads/${segment(threadId)}/suggestions`, input));
  }
  async decide(commentId: string, accept: boolean): Promise<Thread> {
    return toThread(
      await this.http.post(`/api/comments/${segment(commentId)}/suggestion-decision`, {
        action: accept ? 'accept' : 'reject',
      }),
    );
  }

  async list(documentId: string, status?: ThreadStatus): Promise<Thread[]> {
    return list(
      await this.http.get(`/api/documents/${segment(documentId)}/threads${query({ status })}`),
      toThread,
    );
  }

  async create(documentId: string, anchor: Anchor, body: string): Promise<Thread> {
    return toThread(await this.http.post(`/api/documents/${segment(documentId)}/threads`, { anchor, body }));
  }

  async reply(threadId: string, body: string): Promise<Comment> {
    return toComment(await this.http.post(`/api/threads/${segment(threadId)}/comments`, { body }));
  }

  async update(threadId: string, patch: ThreadPatch): Promise<Thread> {
    return toThread(await this.http.patch(`/api/threads/${segment(threadId)}`, patch));
  }

  async editComment(commentId: string, body: string): Promise<Comment> {
    return toComment(await this.http.patch(`/api/comments/${segment(commentId)}`, { body }));
  }

  async deleteComment(commentId: string): Promise<void> {
    await this.http.delete(`/api/comments/${segment(commentId)}`);
  }
}
