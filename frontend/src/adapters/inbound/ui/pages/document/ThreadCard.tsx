import { useState } from 'react';
import { Link } from 'react-router';
import { historyHref } from '../../../../../domain/document/document';
import type { Comment, Thread } from '../../../../../domain/thread/thread';
import {
  THREAD_STATUS_LABEL,
  anchorQuote,
  canResolve,
  isDetached,
  isOwnComment,
} from '../../../../../domain/thread/thread';
import { ActorChip } from '../../common/ActorChip';
import { CommentBody } from '../../common/InlineText';
import { RelativeTime } from '../../common/RelativeTime';
import { CommentEditor } from './CommentEditor';

export type ThreadActions = {
  readonly reply: (thread: Thread, body: string) => Promise<boolean>;
  readonly resolve: (thread: Thread) => Promise<void>;
  readonly reopen: (thread: Thread) => Promise<void>;
  readonly editComment: (thread: Thread, comment: Comment, body: string) => Promise<boolean>;
  readonly deleteComment: (thread: Thread, comment: Comment) => Promise<void>;
  readonly startReanchor: (thread: Thread) => void;
};

function anchorLabel(thread: Thread): string {
  switch (thread.anchor.kind) {
    case 'document':
      return '문서 전체';
    case 'block':
      return '블록';
    case 'cell':
    case 'text':
      return anchorQuote(thread.anchor) ?? '';
  }
}

function CommentItem({
  thread,
  comment,
  login,
  mentionLogins,
  actions,
}: {
  thread: Thread;
  comment: Comment;
  login: string;
  mentionLogins: readonly string[];
  actions: ThreadActions;
}) {
  const [editing, setEditing] = useState(false);
  const own = isOwnComment(comment, login);
  return (
    <li className="comment">
      <div className="comment-head">
        <ActorChip actor={comment.author} />
        <span className="faint small">
          <RelativeTime iso={comment.createdAt} />
          {comment.editedAt === null || comment.deleted ? null : ' · 수정됨'}
        </span>
      </div>
      {comment.deleted ? (
        <p className="comment-deleted">삭제된 댓글</p>
      ) : editing ? (
        <CommentEditor
          mentionLogins={mentionLogins}
          initial={comment.body}
          placeholder="댓글 수정"
          submitLabel="저장"
          autoFocus
          onCancel={() => setEditing(false)}
          onSubmit={async (body) => {
            const done = await actions.editComment(thread, comment, body);
            if (done) setEditing(false);
            return done;
          }}
        />
      ) : (
        <CommentBody markdown={comment.body} />
      )}
      {own && !editing ? (
        <div className="comment-actions">
          <button type="button" className="link-btn" onClick={() => setEditing(true)}>
            수정
          </button>
          <button
            type="button"
            className="link-btn"
            onClick={() => {
              if (window.confirm('이 댓글을 삭제할까요?')) void actions.deleteComment(thread, comment);
            }}
          >
            삭제
          </button>
        </div>
      ) : null}
    </li>
  );
}

export function ThreadCard({
  thread,
  active,
  login,
  spaceSlug,
  mentionLogins,
  actions,
  onActivate,
}: {
  thread: Thread;
  active: boolean;
  login: string;
  spaceSlug: string;
  mentionLogins: readonly string[];
  actions: ThreadActions;
  onActivate: () => void;
}) {
  const detached = isDetached(thread);
  const quote = anchorLabel(thread);
  return (
    <article
      id={`thread-${thread.id}`}
      className={`thread-card${active ? ' is-active' : ''}${thread.status === 'resolved' ? ' is-resolved' : ''}`}
      onClick={onActivate}
      aria-label={`댓글 스레드: ${quote}`}
    >
      <header className="thread-head">
        <span
          className={`thread-quote${thread.anchor.kind === 'text' || thread.anchor.kind === 'cell' ? '' : ' faint'}`}
          title={quote}
        >
          {quote}
        </span>
        <span className={`badge thread-status thread-status-${thread.status}`}>
          {THREAD_STATUS_LABEL[thread.status]}
        </span>
      </header>
      {detached ? (
        <div className="thread-detached">
          <span className="badge badge-warning">분리됨</span>
          <span className="small muted">원래 위치의 글이 바뀌어 연결이 끊겼습니다.</span>
          <button
            type="button"
            className="btn btn-small"
            onClick={(event) => {
              event.stopPropagation();
              actions.startReanchor(thread);
            }}
          >
            다른 위치에 다시 달기
          </button>
        </div>
      ) : null}
      <ul className="comments">
        {thread.comments.map((comment) => (
          <CommentItem
            key={comment.id}
            thread={thread}
            comment={comment}
            login={login}
            mentionLogins={mentionLogins}
            actions={actions}
          />
        ))}
      </ul>
      {thread.linkedRevisions.length === 0 ? null : (
        <div className="comment-revisions">
          {thread.linkedRevisions.map((revision) => (
            <Link key={revision} className="chip" to={historyHref(spaceSlug, thread.documentId, revision)}>
              rev {revision} 변경 보기
            </Link>
          ))}
        </div>
      )}
      {active ? (
        <CommentEditor
          mentionLogins={mentionLogins}
          placeholder="답글 (⌘Enter)"
          submitLabel="답글"
          initial=""
          onSubmit={(body) => actions.reply(thread, body)}
        />
      ) : null}
      <footer className="thread-actions">
        {canResolve(thread) ? (
          <button type="button" className="btn btn-small" onClick={() => void actions.resolve(thread)}>
            해결
          </button>
        ) : (
          <button type="button" className="btn btn-small" onClick={() => void actions.reopen(thread)}>
            다시 열기
          </button>
        )}
        {active ? null : (
          <span className="faint small">
            {thread.comments.length > 1 ? `댓글 ${thread.comments.length}개` : ''}
          </span>
        )}
      </footer>
    </article>
  );
}
