import type { DocumentSummary } from '../../domain/document/document';
import type { Anchor, Comment, Thread } from '../../domain/thread/thread';
import { withReply } from '../../domain/thread/thread';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { ThreadGateway } from './ports/ThreadGateway';

export type CommentError = 'body_required';

export type OpenThreadSummary = { readonly document: DocumentSummary; readonly threads: readonly Thread[] };

export interface ThreadUseCases {
  list(documentId: string): Promise<Thread[]>;
  start(documentId: string, anchor: Anchor, body: string): Promise<Result<Thread, CommentError>>;
  reply(thread: Thread, body: string): Promise<Result<Thread, CommentError>>;
  resolve(threadId: string): Promise<Thread>;
  reopen(threadId: string): Promise<Thread>;
  /** Re-attaches a detached thread to a new selection. */
  reanchor(threadId: string, anchor: Anchor): Promise<Thread>;
  editComment(thread: Thread, commentId: string, body: string): Promise<Result<Thread, CommentError>>;
  deleteComment(thread: Thread, commentId: string): Promise<Thread>;
  /** Unresolved threads of the given documents, for the space home summary. */
  openAcross(documents: readonly DocumentSummary[]): Promise<OpenThreadSummary[]>;
}

function replaceComment(thread: Thread, comment: Comment): Thread {
  return {
    ...thread,
    comments: thread.comments.map((existing) => (existing.id === comment.id ? comment : existing)),
  };
}

export function createThreadUseCases(gateway: ThreadGateway): ThreadUseCases {
  return {
    list: (documentId) => gateway.list(documentId),
    async start(documentId, anchor, rawBody) {
      const body = rawBody.trim();
      if (body === '') return failure('body_required');
      return ok(await gateway.create(documentId, anchor, body));
    },
    async reply(thread, rawBody) {
      const body = rawBody.trim();
      if (body === '') return failure('body_required');
      return ok(withReply(thread, await gateway.reply(thread.id, body)));
    },
    resolve: (threadId) => gateway.update(threadId, { status: 'resolved' }),
    reopen: (threadId) => gateway.update(threadId, { status: 'open' }),
    reanchor: (threadId, anchor) => gateway.update(threadId, { anchor }),
    async editComment(thread, commentId, rawBody) {
      const body = rawBody.trim();
      if (body === '') return failure('body_required');
      return ok(replaceComment(thread, await gateway.editComment(commentId, body)));
    },
    async deleteComment(thread, commentId) {
      await gateway.deleteComment(commentId);
      return {
        ...thread,
        comments: thread.comments.map((comment) =>
          comment.id === commentId ? { ...comment, deleted: true } : comment,
        ),
      };
    },
    async openAcross(documents) {
      const lists = await Promise.all(documents.map((document) => gateway.list(document.id)));
      return documents
        .map((document, index) => ({
          document,
          threads: (lists[index] ?? []).filter((thread) => thread.status !== 'resolved'),
        }))
        .filter((summary) => summary.threads.length > 0);
    },
  };
}
