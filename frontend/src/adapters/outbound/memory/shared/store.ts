import type { Activity } from '../../../../domain/activity/activity';
import type { Asset } from '../../../../domain/asset/asset';
import type { Connection, OAuthRequest } from '../../../../domain/connection/connection';
import type { AgentDocument, Revision, RevisionSnapshot } from '../../../../domain/document/document';
import type { InboxItem } from '../../../../domain/inbox/inbox';
import type { Me } from '../../../../domain/session/me';
import type { Actor } from '../../../../domain/shared/actor';
import type { Space } from '../../../../domain/space/space';
import type { Thread } from '../../../../domain/thread/thread';
import { ApiError } from '../../../../application/shared/ports/errors';

export type StoredRevision = { readonly meta: Revision; readonly snapshot: RevisionSnapshot };

/**
 * A map keyed by document id that reports every write, so the fake raises the same "this document changed"
 * signal the server's event stream does without each gateway having to remember to send it.
 */
class DocumentKeyedMap<V> extends Map<string, V> {
  private readonly changed: (documentId: string) => void;

  constructor(changed: (documentId: string) => void) {
    super();
    this.changed = changed;
  }

  override set(documentId: string, value: V): this {
    super.set(documentId, value);
    this.changed(documentId);
    return this;
  }

  override delete(documentId: string): boolean {
    const removed = super.delete(documentId);
    if (removed) this.changed(documentId);
    return removed;
  }
}

/**
 * State of the in-memory fake backend used with VITE_FAKE_API=true for local preview without the
 * Spring Boot server. It is deliberately simple and not a reference for server behavior.
 */
export class MemoryStore {
  me: Me;
  spaces: Space[] = [];
  /** Former slugs keep resolving to the space, like the real server. */
  slugAliases = new Map<string, string>();
  private readonly documentListeners = new Set<(documentId: string) => void>();
  private readonly changedDocuments = new Set<string>();
  /** Writes here and to `threads` are reported to onDocumentChange listeners. */
  documents: Map<string, AgentDocument> = new DocumentKeyedMap((id) => this.documentChanged(id));
  revisions = new Map<string, StoredRevision[]>();
  threads: Map<string, Thread[]> = new DocumentKeyedMap((id) => this.documentChanged(id));
  inbox: InboxItem[] = [];
  activity: Activity[] = [];
  connections: Connection[] = [];
  oauthRequests = new Map<string, OAuthRequest>();
  assets = new Map<string, { readonly asset: Asset; readonly url: string }>();
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

  /**
   * Tells the listener which documents a fake request changed, once per document and only after the request's
   * synchronous handler finished, so a listener that reads back never sees a half-applied write.
   */
  onDocumentChange(listener: (documentId: string) => void): () => void {
    this.documentListeners.add(listener);
    return () => {
      this.documentListeners.delete(listener);
    };
  }

  private documentChanged(documentId: string): void {
    // Writes nobody listens to (seeding the preview) are not reported to a listener that subscribes later.
    if (this.documentListeners.size === 0) return;
    if (this.changedDocuments.size === 0) queueMicrotask(() => this.reportChanges());
    this.changedDocuments.add(documentId);
  }

  private reportChanges(): void {
    const changed = [...this.changedDocuments];
    this.changedDocuments.clear();
    for (const documentId of changed) for (const listener of this.documentListeners) listener(documentId);
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
