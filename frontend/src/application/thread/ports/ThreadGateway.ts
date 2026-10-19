import type { Anchor, Comment, Thread, ThreadStatus } from '../../../domain/thread/thread';

export type ThreadPatch = { readonly status?: 'open' | 'resolved'; readonly anchor?: Anchor };

export interface ThreadGateway {
  list(documentId: string, status?: ThreadStatus): Promise<Thread[]>;
  create(documentId: string, anchor: Anchor, body: string): Promise<Thread>;
  reply(threadId: string, body: string): Promise<Comment>;
  update(threadId: string, patch: ThreadPatch): Promise<Thread>;
  editComment(commentId: string, body: string): Promise<Comment>;
  deleteComment(commentId: string): Promise<void>;
}
