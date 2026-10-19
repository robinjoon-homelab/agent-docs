import type { ThreadGateway, ThreadPatch } from '../../../../application/thread/ports/ThreadGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type { Anchor, Comment, Thread, ThreadStatus } from '../../../../domain/thread/thread';
import { withReply } from '../../../../domain/thread/thread';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

function resolutionFor(anchor: Anchor): Thread['resolution'] {
  return anchor.kind === 'text'
    ? { state: 'attached', start: anchor.start, end: anchor.end }
    : { state: 'attached' };
}

export class MemoryThreadGateway implements ThreadGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(documentId: string, status?: ThreadStatus): Promise<Thread[]> {
    return respond(() => {
      this.store.document(documentId);
      const threads = this.store.threads.get(documentId) ?? [];
      return status === undefined ? threads : threads.filter((thread) => thread.status === status);
    });
  }

  create(documentId: string, anchor: Anchor, body: string): Promise<Thread> {
    return respond(() => {
      const document = this.store.document(documentId);
      const now = this.store.now();
      const thread: Thread = {
        id: this.store.nextId('th'),
        documentId,
        anchor,
        resolution: resolutionFor(anchor),
        status: 'open',
        author: this.store.actor,
        comments: [this.comment(body)],
        linkedRevisions: [],
        createdAt: now,
        updatedAt: now,
      };
      this.store.threads.set(documentId, [...(this.store.threads.get(documentId) ?? []), thread]);
      this.store.record({
        kind: 'comment',
        space: document.space,
        documentId,
        documentTitle: document.title,
        threadId: thread.id,
        revision: null,
        summary: `댓글: ${body.slice(0, 60)}`,
      });
      return thread;
    });
  }

  reply(threadId: string, body: string): Promise<Comment> {
    return respond(() => {
      const thread = this.store.findThread(threadId);
      const comment = this.comment(body);
      this.store.replaceThread(withReply(thread, comment));
      return comment;
    });
  }

  update(threadId: string, patch: ThreadPatch): Promise<Thread> {
    return respond(() => {
      const thread = this.store.findThread(threadId);
      const anchor = patch.anchor ?? thread.anchor;
      return this.store.replaceThread({
        ...thread,
        status: patch.status ?? thread.status,
        anchor,
        resolution: patch.anchor === undefined ? thread.resolution : resolutionFor(anchor),
        updatedAt: this.store.now(),
      });
    });
  }

  editComment(commentId: string, body: string): Promise<Comment> {
    return respond(() =>
      this.changeComment(commentId, (comment) => ({ ...comment, body, editedAt: this.store.now() })),
    );
  }

  deleteComment(commentId: string): Promise<void> {
    return respond(() => {
      this.changeComment(commentId, (comment) => ({ ...comment, body: '', deleted: true }));
    });
  }

  private comment(body: string): Comment {
    return {
      id: this.store.nextId('cm'),
      author: this.store.actor,
      body,
      createdAt: this.store.now(),
      editedAt: null,
      deleted: false,
    };
  }

  private changeComment(commentId: string, change: (comment: Comment) => Comment): Comment {
    for (const threads of this.store.threads.values()) {
      for (const thread of threads) {
        const comment = thread.comments.find((candidate) => candidate.id === commentId);
        if (comment === undefined) continue;
        const updated = change(comment);
        this.store.replaceThread({
          ...thread,
          comments: thread.comments.map((c) => (c.id === commentId ? updated : c)),
        });
        return updated;
      }
    }
    throw new ApiError('not_found', 404, '댓글을 찾을 수 없습니다.');
  }
}
