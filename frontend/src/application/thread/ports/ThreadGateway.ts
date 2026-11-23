import type { Anchor, Comment, Thread, ThreadStatus, SuggestionInput } from '../../../domain/thread/thread';

export type ThreadPatch = { readonly status?: 'open' | 'resolved'; readonly anchor?: Anchor };

export interface ThreadGateway {
  react(threadId: string, emoji: string, add: boolean): Promise<Thread>;
  claim(threadId: string, action: 'acquire' | 'renew' | 'release'): Promise<Thread>;
  suggest(threadId: string, input: SuggestionInput): Promise<Thread>;
  decide(commentId: string, accept: boolean): Promise<Thread>;

  list(documentId: string, status?: ThreadStatus, flowTargets?: boolean): Promise<Thread[]>;
  create(documentId: string, anchor: Anchor, body: string): Promise<Thread>;
  reply(threadId: string, body: string): Promise<Comment>;
  update(threadId: string, patch: ThreadPatch): Promise<Thread>;
  editComment(commentId: string, body: string): Promise<Comment>;
  deleteComment(commentId: string): Promise<void>;
}
