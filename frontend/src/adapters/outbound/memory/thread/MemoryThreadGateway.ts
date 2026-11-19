import type { ThreadGateway, ThreadPatch } from '../../../../application/thread/ports/ThreadGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type {
  Anchor,
  Comment,
  Thread,
  ThreadStatus,
  SuggestionInput,
} from '../../../../domain/thread/thread';
import { plainText } from '../../../../domain/document/inline';
import type { StoredBlock } from '../../../../domain/document/block';
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

  react(threadId: string, emoji: string, add: boolean): Promise<Thread> {
    return respond(() => {
      const thread = this.store.findThread(threadId);
      const reactions = (thread.reactions ?? []).filter(
        (r) => !(r.emoji === emoji && r.actor.kind === 'human' && r.actor.login === this.store.me.login),
      );
      return this.store.replaceThread({
        ...thread,
        reactions: add ? [...reactions, { actor: this.store.actor, emoji }] : reactions,
      });
    });
  }

  claim(): Promise<Thread> {
    return Promise.reject(new ApiError('forbidden', 403, '점유는 에이전트 연결 전용입니다.'));
  }

  suggest(threadId: string, input: SuggestionInput): Promise<Thread> {
    return respond(() => {
      const thread = this.store.findThread(threadId);
      const target = this.store
        .document(thread.documentId)
        .blocks.find((b) => input.anchor.kind !== 'document' && b.id === input.anchor.blockId);
      if (target === undefined) throw new ApiError('not_found', 404, '제안 범위를 찾을 수 없습니다.');
      if (input.hash !== undefined && input.hash !== target.hash)
        throw new ApiError('conflict', 409, '제안 기준이 변경되었습니다.');
      if (input.before === '') throw new ApiError('invalid', 400, '현재 글을 입력하세요.');
      previewReplacement(target, input);
      const comment = this.comment('수정 제안');
      const next = withReply(thread, comment);
      return this.store.replaceThread({
        ...next,
        suggestions: [
          ...(thread.suggestions ?? []),
          { ...input, hash: target.hash, commentId: comment.id, decision: null },
        ],
      });
    });
  }

  decide(commentId: string, accept: boolean): Promise<Thread> {
    return respond(() => {
      if (!this.store.me.owner) throw new ApiError('forbidden', 403, '소유자만 제안을 판정합니다.');
      const thread = [...this.store.threads.values()]
        .flat()
        .find((t) => t.suggestions?.some((s) => s.commentId === commentId));
      if (thread === undefined) throw new ApiError('not_found', 404, '제안을 찾을 수 없습니다.');
      const suggestion = thread.suggestions?.find((s) => s.commentId === commentId);
      if (suggestion === undefined || suggestion.decision !== null)
        throw new ApiError('conflict', 409, '이미 판정한 제안입니다.');
      const document = this.store.document(thread.documentId);
      const revision = accept ? document.revision + 1 : null;
      if (accept) {
        const target = document.blocks.find(
          (b) => suggestion.anchor.kind !== 'document' && b.id === suggestion.anchor.blockId,
        );
        if (target === undefined || target.hash !== suggestion.hash)
          throw new ApiError('conflict', 409, '제안 기준이 변경되었습니다.');
        const replacement = previewReplacement(target, suggestion);
        const blocks = document.blocks.map((b) => (b.id === target.id ? replacement : b));
        const now = this.store.now();
        const number = document.revision + 1;
        const updated = {
          ...document,
          blocks,
          version: document.version + 1,
          revision: number,
          updatedAt: now,
        };
        const history = this.store.revisions.get(document.id) ?? [];
        this.store.documents.set(document.id, updated);
        this.store.revisions.set(document.id, [
          ...history,
          {
            meta: {
              revision: number,
              actor: this.store.actor,
              source: 'web',
              summary: '수정 제안 수락',
              changedBlockIds: [target.id],
              createdAt: now,
              threadIds: [thread.id],
            },
            snapshot: {
              revision: number,
              title: document.title,
              path: document.path,
              status: document.status,
              tags: document.tags,
              assignee: document.assignee,
              blocks,
              actor: this.store.actor,
              createdAt: now,
            },
          },
        ]);
      }
      return this.store.replaceThread({
        ...thread,
        linkedRevisions: revision === null ? thread.linkedRevisions : [...thread.linkedRevisions, revision],
        suggestions: thread.suggestions?.map((s) =>
          s.commentId === commentId
            ? {
                ...s,
                decision: { actor: this.store.actor, at: this.store.now(), accepted: accept, revision },
              }
            : s,
        ),
      });
    });
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
        claim: patch.status === 'resolved' ? null : thread.claim,
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
          suggestions: updated.deleted
            ? thread.suggestions?.filter((s) => s.commentId !== commentId)
            : thread.suggestions,
        });
        return updated;
      }
    }
    throw new ApiError('not_found', 404, '댓글을 찾을 수 없습니다.');
  }
}

function previewReplacement(block: StoredBlock, input: SuggestionInput): StoredBlock {
  const anchor = input.anchor;
  const hash = `${block.hash}-suggested`;
  if (anchor.kind === 'cell' && block.type === 'table') {
    const column = block.columns.findIndex((c) => c.id === anchor.columnId);
    const row = block.rows.find((r) => r.id === anchor.rowId);
    if (column < 0 || row?.cells[column] !== input.before)
      throw new ApiError('conflict', 409, '셀 기준이 변경되었습니다.');
    return {
      ...block,
      hash,
      rows: block.rows.map((r) =>
        r.id === anchor.rowId
          ? { ...r, cells: r.cells.map((c, index) => (index === column ? input.after : c)) }
          : r,
      ),
    };
  }
  const source = 'text' in block ? block.text : block.type === 'code' ? block.code : null;
  if (source === null)
    throw new ApiError('invalid', 400, '이 미리보기 블록은 실제 서버에서 제안할 수 있습니다.');
  let replacement = input.after;
  if (anchor.kind === 'text') {
    const expected =
      plainText(source).slice(0, anchor.start) + plainText(input.after) + plainText(source).slice(anchor.end);
    const candidates = Array.from({ length: source.length }, (_, index) => index)
      .filter((index) => source.startsWith(input.before, index))
      .map((index) => source.slice(0, index) + input.after + source.slice(index + input.before.length))
      .filter((s) => plainText(s) === expected);
    const candidate = candidates.length === 1 ? candidates[0] : undefined;
    if (candidate === undefined) throw new ApiError('conflict', 409, '텍스트 기준이 변경되었습니다.');
    replacement = candidate;
  } else if (source !== input.before) throw new ApiError('conflict', 409, '블록 기준이 변경되었습니다.');
  return 'text' in block
    ? { ...block, text: replacement, hash }
    : ({ ...block, code: replacement, hash } as StoredBlock);
}
