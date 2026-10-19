import { useState } from 'react';
import { useNavigate } from 'react-router';
import type { InboxItem } from '../../../../../domain/inbox/inbox';
import { INBOX_KIND_LABEL, isUnread } from '../../../../../domain/inbox/inbox';
import { useServices } from '../../../../../config/ServicesContext';
import { useSession } from '../../app/SessionContext';
import { ActorChip } from '../../common/ActorChip';
import { RelativeTime } from '../../common/RelativeTime';
import { EmptyState, ErrorBox, errorMessage, Loading } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import './inbox.css';

function InboxRow({ item, onOpen }: { item: InboxItem; onOpen: (item: InboxItem) => void }) {
  const unread = isUnread(item);
  return (
    <li>
      <button type="button" className={`inbox-row${unread ? ' unread' : ''}`} onClick={() => onOpen(item)}>
        <span className="inbox-dot" aria-label={unread ? '안 읽음' : undefined} />
        <span className="inbox-main">
          <span className="inbox-top">
            <span className="badge badge-neutral">{INBOX_KIND_LABEL[item.kind]}</span>
            <ActorChip actor={item.actor} />
            <span className="faint small inbox-time">
              <RelativeTime iso={item.createdAt} />
            </span>
          </span>
          <span className="inbox-where small">
            {item.space.name} ·{' '}
            <strong>{item.documentTitle === '' ? '제목 없음' : item.documentTitle}</strong>
          </span>
          {item.excerpt === '' ? null : <span className="inbox-excerpt">{item.excerpt}</span>}
        </span>
      </button>
    </li>
  );
}

export function InboxPage() {
  const services = useServices();
  const navigate = useNavigate();
  const toast = useToast();
  const { refreshInbox } = useSession();
  const [unreadOnly, setUnreadOnly] = useState(true);
  const items = useLoad(`inbox:${unreadOnly}`, () => services.inbox.list(unreadOnly));
  usePageTitle('인박스');

  const open = (item: InboxItem): void => {
    services.inbox.open(item).then(
      (href) => {
        refreshInbox();
        void navigate(href);
      },
      (error: unknown) => toast(errorMessage(error)),
    );
  };

  const markAllRead = (): void => {
    services.inbox.markAllRead().then(
      () => {
        refreshInbox();
        items.reload();
      },
      (error: unknown) => toast(errorMessage(error)),
    );
  };

  const hasUnread = items.state === 'ready' && items.data.some(isUnread);

  return (
    <div className="page page-narrow">
      <div className="page-header">
        <h1>인박스</h1>
        <div className="inbox-actions">
          <div className="segmented" role="group" aria-label="표시할 알림">
            <button type="button" aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>
              안 읽음
            </button>
            <button type="button" aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)}>
              전체
            </button>
          </div>
          <button type="button" className="btn btn-small" disabled={!hasUnread} onClick={markAllRead}>
            모두 읽음
          </button>
        </div>
      </div>
      {items.state === 'loading' ? <Loading /> : null}
      {items.state === 'error' ? <ErrorBox error={items.error} onRetry={items.reload} /> : null}
      {items.state === 'ready' && items.data.length === 0 ? (
        <EmptyState title={unreadOnly ? '읽지 않은 알림이 없습니다' : '알림이 없습니다'}>
          <span className="small">멘션, 내 스레드의 답글, 담당 문서의 새 댓글이 여기에 모입니다.</span>
        </EmptyState>
      ) : null}
      {items.state === 'ready' && items.data.length > 0 ? (
        <ul className="list inbox-list">
          {items.data.map((item) => (
            <InboxRow key={item.id} item={item} onOpen={open} />
          ))}
        </ul>
      ) : null}
    </div>
  );
}
