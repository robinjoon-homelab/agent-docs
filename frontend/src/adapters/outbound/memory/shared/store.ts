import type { Activity } from '../../../../domain/activity/activity';
import type { Asset } from '../../../../domain/asset/asset';
import type { Connection, OAuthRequest } from '../../../../domain/connection/connection';
import type { AgentDocument, Revision, RevisionSnapshot } from '../../../../domain/document/document';
import type { InboxItem } from '../../../../domain/inbox/inbox';
import type { Usage } from '../../../../domain/metrics/usage';
import type { Me } from '../../../../domain/session/me';
import type { Actor } from '../../../../domain/shared/actor';
import type { Space } from '../../../../domain/space/space';
import type { Thread } from '../../../../domain/thread/thread';
import { ApiError } from '../../../../application/shared/ports/errors';

export type StoredRevision = { readonly meta: Revision; readonly snapshot: RevisionSnapshot };

/**
 * State of the in-memory fake backend used with VITE_FAKE_API=true for local preview without the
 * Spring Boot server. It is deliberately simple and not a reference for server behavior.
 */
export class MemoryStore {
  me: Me;
  spaces: Space[] = [];
  /** Former slugs keep resolving to the space, like the real server. */
  slugAliases = new Map<string, string>();
  documents = new Map<string, AgentDocument>();
  revisions = new Map<string, StoredRevision[]>();
  threads = new Map<string, Thread[]>();
  inbox: InboxItem[] = [];
  activity: Activity[] = [];
  connections: Connection[] = [];
  oauthRequests = new Map<string, OAuthRequest>();
  assets = new Map<string, { readonly asset: Asset; readonly url: string }>();
  usage: Usage = { weeks: [], agentResponseMinutesMedian: null, revisitedDocuments: 0 };
  private counter = 0;

  constructor(me: Me) {
    this.me = me;
  }

  get actor(): Actor {
    return { kind: 'human', login: this.me.login, agent: null };
  }

  nextId(prefix: string): string {
    this.counter += 1;
    return `${prefix}-${this.counter.toString(36)}`;
  }

  now(): string {
    return new Date().toISOString();
  }

  space(slug: string): Space {
    const current = this.slugAliases.get(slug) ?? slug;
    const space = this.spaces.find((candidate) => candidate.slug === current);
    if (space === undefined) throw new ApiError('not_found', 404, '스페이스를 찾을 수 없습니다.');
    return space;
  }

  document(id: string): AgentDocument {
    const document = this.documents.get(id);
    if (document === undefined) throw new ApiError('not_found', 404, '문서를 찾을 수 없습니다.');
    return document;
  }

  findThread(threadId: string): Thread {
    for (const list of this.threads.values()) {
      const thread = list.find((candidate) => candidate.id === threadId);
      if (thread !== undefined) return thread;
    }
    throw new ApiError('not_found', 404, '스레드를 찾을 수 없습니다.');
  }

  replaceThread(thread: Thread): Thread {
    const list = this.threads.get(thread.documentId) ?? [];
    this.threads.set(
      thread.documentId,
      list.map((candidate) => (candidate.id === thread.id ? thread : candidate)),
    );
    return thread;
  }

  touchSpace(spaceId: string): void {
    const at = this.now();
    this.spaces = this.spaces.map((space) =>
      space.id === spaceId ? { ...space, lastActivityAt: at } : space,
    );
  }

  record(activity: Omit<Activity, 'id' | 'at' | 'actor'>): void {
    this.activity.unshift({ ...activity, id: this.nextId('act'), at: this.now(), actor: this.actor });
  }
}

/** Fake latency so loading states are visible during local preview. */
export function delay<T>(value: T, ms = 120): Promise<T> {
  return new Promise((resolve) => setTimeout(() => resolve(value), ms));
}

/** Runs a fake request: thrown errors become rejections, results arrive after a short delay. */
export function respond<T>(handler: () => T): Promise<T> {
  try {
    return delay(structuredClone(handler()));
  } catch (error) {
    return Promise.reject(error instanceof Error ? error : new Error(String(error)));
  }
}

export function clone<T>(value: T): T {
  return structuredClone(value);
}
