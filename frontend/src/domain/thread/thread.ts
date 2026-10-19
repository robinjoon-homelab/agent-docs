import type { Actor } from '../shared/actor';

export type TextAnchor = {
  readonly kind: 'text';
  readonly blockId: string;
  readonly start: number;
  readonly end: number;
  readonly quote: string;
};
export type CellAnchor = {
  readonly kind: 'cell';
  readonly blockId: string;
  readonly rowId: string;
  readonly columnId: string;
  readonly quote: string;
};
export type BlockAnchor = { readonly kind: 'block'; readonly blockId: string };
export type DocumentAnchor = { readonly kind: 'document' };

export type Anchor = TextAnchor | CellAnchor | BlockAnchor | DocumentAnchor;

export type ThreadStatus = 'open' | 'answered' | 'resolved';

export type Resolution = {
  readonly state: 'attached' | 'detached';
  readonly start?: number;
  readonly end?: number;
};

export type Comment = {
  readonly id: string;
  readonly author: Actor;
  readonly body: string;
  readonly createdAt: string;
  readonly editedAt: string | null;
  readonly deleted: boolean;
};

export type Thread = {
  readonly id: string;
  readonly documentId: string;
  readonly anchor: Anchor;
  readonly resolution: Resolution;
  readonly status: ThreadStatus;
  readonly author: Actor;
  readonly comments: readonly Comment[];
  readonly linkedRevisions: readonly number[];
  readonly createdAt: string;
  readonly updatedAt: string;
};

export const THREAD_STATUS_LABEL: Record<ThreadStatus, string> = {
  open: '열림',
  answered: '답변함',
  resolved: '해결됨',
};

export type ThreadFilter = 'open' | 'all' | 'detached';

export const THREAD_FILTER_LABEL: Record<ThreadFilter, string> = {
  open: '열림',
  all: '전체',
  detached: '분리됨',
};

export const DELETED_COMMENT_TEXT = '삭제된 댓글';

export function isDetached(thread: Thread): boolean {
  return thread.resolution.state === 'detached';
}

export function filterThreads(threads: readonly Thread[], filter: ThreadFilter): Thread[] {
  switch (filter) {
    case 'open':
      return threads.filter((thread) => thread.status !== 'resolved');
    case 'detached':
      return threads.filter(isDetached);
    case 'all':
      return [...threads];
  }
}

export function anchorBlockId(anchor: Anchor): string | null {
  return anchor.kind === 'document' ? null : anchor.blockId;
}

/** Current character range of a text anchor: the server's re-resolved range wins over the stored one. */
export function textRange(thread: Thread): { readonly start: number; readonly end: number } | null {
  if (thread.anchor.kind !== 'text' || isDetached(thread)) return null;
  return {
    start: thread.resolution.start ?? thread.anchor.start,
    end: thread.resolution.end ?? thread.anchor.end,
  };
}

export function anchorQuote(anchor: Anchor): string | null {
  return anchor.kind === 'text' || anchor.kind === 'cell' ? anchor.quote : null;
}

/** Agent replies move a thread to "answered"; only humans resolve, which the web UI always is. */
export function canResolve(thread: Thread): boolean {
  return thread.status !== 'resolved';
}

/**
 * Mirrors the server rule: any reply reopens a resolved thread, and a person's reply also reopens an
 * answered one (an agent's reply leaves it answered).
 */
export function withReply(thread: Thread, comment: Comment): Thread {
  const reopens =
    thread.status === 'resolved' || (thread.status === 'answered' && comment.author.kind !== 'agent');
  return {
    ...thread,
    comments: [...thread.comments, comment],
    status: reopens ? 'open' : thread.status,
    updatedAt: comment.createdAt,
  };
}

export function commentText(comment: Comment): string {
  return comment.deleted ? DELETED_COMMENT_TEXT : comment.body;
}

export function isOwnComment(comment: Comment, login: string): boolean {
  return comment.author.kind === 'human' && comment.author.login === login && !comment.deleted;
}
