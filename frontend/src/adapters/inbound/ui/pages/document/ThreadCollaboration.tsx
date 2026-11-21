import { flowPreview } from './flowPreview';
import './threadCollaboration.css';
import { useEffect, useState } from 'react';
import type { Thread } from '../../../../../domain/thread/thread';
import { actorShortName } from '../../../../../domain/shared/actor';
import { useDocumentPermission } from './DocumentPermissionContext';
import { canEdit, canComment } from '../../../../../domain/space/permission';

export type ThreadCollaborationActions = {
  readonly react?: (thread: Thread, emoji: string, add: boolean) => Promise<void>;
  readonly suggest?: (thread: Thread, before: string, after: string) => Promise<boolean>;
  readonly decide?: (commentId: string, accept: boolean) => Promise<void>;
};

export function ThreadCollaboration({
  thread,
  login,
  actions,
}: {
  thread: Thread;
  login: string;
  actions: ThreadCollaborationActions;
}) {
  const permission = useDocumentPermission();
  const [form, setForm] = useState(() => thread.id === `flow-${flowPreview().suggest}`);
  const [before, setBefore] = useState(thread.anchor.kind === 'document' ? '' : (thread.anchor.quote ?? ''));
  const [after, setAfter] = useState('');
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);
  const claim = thread.claim;
  const active = claim != null && Date.parse(claim.expiresAt) > now;
  return (
    <section className="thread-collaboration" aria-label="스레드 협업">
      {claim == null ? null : (
        <p className="small muted">
          {active ? '처리 중' : '점유 만료'} · {actorShortName(claim.actor)} ·{' '}
          <time dateTime={claim.expiresAt}>
            {new Date(claim.expiresAt).toLocaleTimeString([], {
              hour: '2-digit',
              minute: '2-digit',
              hour12: false,
            })}
            까지
          </time>
        </p>
      )}
      <div className="thread-reactions">
        {[...new Set(['👍', '❤️', '🎉', '👀', '✅', ...(thread.reactions ?? []).map((r) => r.emoji)])].map(
          (emoji) => {
            const reactions = (thread.reactions ?? []).filter((r) => r.emoji === emoji);
            const mine = reactions.some(
              (r) => r.own ?? (r.actor.kind === 'human' && r.actor.login === login),
            );
            return (
              <button
                key={emoji}
                type="button"
                className="btn btn-small"
                aria-label={`${emoji} 반응`}
                aria-pressed={mine}
                disabled={!canComment(permission)}
                title={reactions.map((r) => actorShortName(r.actor)).join(', ')}
                onClick={() => void actions.react?.(thread, emoji, !mine)}
              >
                {emoji} {reactions.length || ''}
              </button>
            );
          },
        )}
      </div>
      {(thread.suggestions ?? []).map((suggestion) => (
        <div className="thread-suggestion" key={suggestion.commentId}>
          <p className="small muted">수정 제안</p>
          <del style={{ whiteSpace: 'pre-wrap' }}>{suggestion.before}</del>
          <br />
          <ins style={{ whiteSpace: 'pre-wrap' }}>{suggestion.after}</ins>
          {suggestion.decision == null ? (
            canEdit(permission) ? (
              <div>
                <button
                  type="button"
                  className="btn btn-small"
                  onClick={() => void actions.decide?.(suggestion.commentId, true)}
                >
                  수락
                </button>
                <button
                  type="button"
                  className="btn btn-small"
                  onClick={() => void actions.decide?.(suggestion.commentId, false)}
                >
                  거절
                </button>
              </div>
            ) : (
              <p className="small muted">편집자 검토 대기</p>
            )
          ) : (
            <p className="small muted">
              {suggestion.decision.accepted ? `수락 · rev ${suggestion.decision.revision}` : '거절'}
            </p>
          )}
        </div>
      ))}
      {!canComment(permission) ||
      thread.anchor.kind === 'document' ||
      actions.suggest === undefined ? null : (
        <button
          type="button"
          className="link-btn"
          onClick={() => {
            if (!form) setBefore(thread.anchor.kind === 'document' ? '' : (thread.anchor.quote ?? ''));
            setForm(!form);
          }}
        >
          수정 제안
        </button>
      )}
      {!form ? null : (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            setBusy(true);
            void actions
              .suggest?.(thread, before, after)
              .then((done) => {
                if (done) setForm(false);
              })
              .finally(() => setBusy(false));
          }}
        >
          <label>
            현재 글
            <textarea
              maxLength={100000}
              required
              value={before}
              onChange={(event) => setBefore(event.target.value)}
            />
          </label>
          <label>
            바꿀 글
            <textarea maxLength={100000} value={after} onChange={(event) => setAfter(event.target.value)} />
          </label>
          <button className="btn btn-small" disabled={busy || !canComment(permission)} type="submit">
            제안 보내기
          </button>
        </form>
      )}
    </section>
  );
}
