import { useState } from 'react';
import type { IssuedWebhook, WebhookEndpoint, WebhookEventType } from '../../../../../domain/webhook/webhook';
import {
  deliveryStatus,
  STOP_REASON_LABEL,
  WEBHOOK_DRAFT_ERROR_TEXT,
  WEBHOOK_EVENT_LABEL,
  WEBHOOK_EVENTS,
} from '../../../../../domain/webhook/webhook';
import { formatDate, formatDateTime } from '../../../../../domain/shared/time';
import { useServices } from '../../../../../config/ServicesContext';
import { CopyButton } from '../../common/CopyButton';
import { Dialog } from '../../common/Dialog';
import { Icon } from '../../common/Icon';
import { EmptyState, ErrorBox, errorMessage, Loading } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import './settings.css';
import './webhooks.css';

function ContractNotes() {
  return (
    <section className="section card webhook-notes">
      <h2 className="section-title">보내는 내용</h2>
      <ul>
        <li>
          문서 변경은 확정된 리비전마다, 댓글과 멘션은 실제로 생길 때마다 보냅니다. 등록한 뒤에 생긴 이벤트만
          보냅니다.
        </li>
        <li>본문에는 ID, 리비전 번호, 행위자, 시각, 링크만 담고 제목·요약·댓글 내용은 담지 않습니다.</li>
        <li>
          Standard Webhooks 형식으로 <code>webhook-id</code>·<code>webhook-timestamp</code>·
          <code>webhook-signature</code> 헤더를 붙입니다. 같은 이벤트가 두 번 올 수 있으니 받는 쪽에서{' '}
          <code>webhook-id</code>로 중복을 거르세요.
        </li>
        <li>
          2xx 응답만 성공입니다. 실패하면 5초, 30초, 2분, 10분, 1시간, 6시간, 24시간 뒤 다시 보내 모두 8번
          시도합니다. 410 응답을 받으면 엔드포인트를 중지합니다.
        </li>
      </ul>
    </section>
  );
}

function SecretDialog({
  issued,
  title,
  onClose,
}: {
  issued: IssuedWebhook;
  title: string;
  onClose: () => void;
}) {
  return (
    <Dialog
      open
      title={title}
      onClose={onClose}
      footer={
        <button type="button" className="btn btn-primary" onClick={onClose}>
          복사했습니다
        </button>
      }
    >
      <div className="warning-box">서명 비밀은 다시 표시되지 않습니다. 지금 복사해 받는 쪽에 설정하세요.</div>
      <div className="token-display">
        <input
          className="input mono"
          readOnly
          value={issued.secret}
          aria-label="서명 비밀"
          onFocus={(event) => event.target.select()}
        />
        <CopyButton text={issued.secret} label="비밀 복사" done="서명 비밀을 복사했습니다" />
      </div>
      <p className="faint small mono">{issued.endpoint.url}</p>
    </Dialog>
  );
}

function RegisterDialog({
  onClose,
  onIssued,
}: {
  onClose: () => void;
  onIssued: (issued: IssuedWebhook) => void;
}) {
  const services = useServices();
  const [url, setUrl] = useState('');
  const [events, setEvents] = useState<readonly WebhookEventType[]>(WEBHOOK_EVENTS);
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const toggle = (event: WebhookEventType, checked: boolean): void => {
    setEvents(checked ? [...events, event] : events.filter((candidate) => candidate !== event));
  };
  const submit = async (): Promise<void> => {
    setBusy(true);
    setErrors([]);
    try {
      const result = await services.webhooks.register({ url, events });
      if (result.ok) onIssued(result.value);
      else setErrors(result.error.map((error) => WEBHOOK_DRAFT_ERROR_TEXT[error]));
    } catch (failure) {
      setErrors([errorMessage(failure)]);
    } finally {
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      title="Webhook 등록"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="register-webhook" className="btn btn-primary" disabled={busy}>
            등록
          </button>
        </>
      }
    >
      <form
        id="register-webhook"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="field">
          <label htmlFor="webhook-url">받을 주소</label>
          <input
            id="webhook-url"
            className="input mono"
            value={url}
            autoFocus
            placeholder="예: http://192.168.0.20:8080/agent-docs"
            onChange={(event) => setUrl(event.target.value)}
          />
          <p className="faint small">http·https 주소. 홈랩 안의 사설 주소도 됩니다.</p>
        </div>
        <fieldset className="field plain-fieldset">
          <legend className="field-label">받을 이벤트</legend>
          {WEBHOOK_EVENTS.map((event) => (
            <label key={event} className="webhook-event-option">
              <input
                type="checkbox"
                checked={events.includes(event)}
                onChange={(change) => toggle(event, change.target.checked)}
              />
              {WEBHOOK_EVENT_LABEL[event]} <code className="faint">{event}</code>
            </label>
          ))}
        </fieldset>
        {errors.map((error) => (
          <p key={error} className="field-error">
            {error}
          </p>
        ))}
      </form>
    </Dialog>
  );
}

type Pending = { readonly action: 'stop' | 'rotate'; readonly endpoint: WebhookEndpoint };

function ConfirmDialog({
  pending,
  onClose,
  onDone,
}: {
  pending: Pending;
  onClose: () => void;
  onDone: (issued: IssuedWebhook | null) => void;
}) {
  const services = useServices();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const stopping = pending.action === 'stop';
  const run = async (): Promise<void> => {
    setBusy(true);
    try {
      if (stopping) {
        await services.webhooks.stop(pending.endpoint.id);
        onDone(null);
      } else {
        onDone(await services.webhooks.rotateSecret(pending.endpoint.id));
      }
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      title={stopping ? 'Webhook 중지' : '서명 비밀 교체'}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            닫기
          </button>
          <button
            type="button"
            className={stopping ? 'btn btn-danger' : 'btn btn-primary'}
            disabled={busy}
            onClick={() => void run()}
          >
            {stopping ? '중지' : '교체'}
          </button>
        </>
      }
    >
      <p className="mono small">{pending.endpoint.url}</p>
      <p>
        {stopping
          ? '중지하면 더 보내지 않고 다시 켤 수 없습니다. 엔드포인트와 전달 기록은 남습니다. 다시 받으려면 새로 등록하세요.'
          : '새 비밀로 바로 서명합니다. 받는 쪽 설정을 바꾸기 전까지 전달이 실패하고 일정에 따라 다시 보냅니다.'}
      </p>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </Dialog>
  );
}

function Deliveries({ endpoint }: { endpoint: WebhookEndpoint }) {
  const services = useServices();
  const deliveries = useLoad(`webhook-deliveries:${endpoint.id}`, () =>
    services.webhooks.deliveries(endpoint.id),
  );
  if (deliveries.state === 'loading') return <Loading />;
  if (deliveries.state === 'error') return <ErrorBox error={deliveries.error} onRetry={deliveries.reload} />;
  if (deliveries.data.length === 0) return <p className="faint small">아직 보낸 이벤트가 없습니다.</p>;
  return (
    <div className="connections-table-wrap">
      <table className="connections-table webhook-deliveries">
        <thead>
          <tr>
            <th>이벤트</th>
            <th>발생</th>
            <th>상태</th>
            <th>다음 시도</th>
            <th>webhook-id</th>
          </tr>
        </thead>
        <tbody>
          {deliveries.data.map((delivery) => {
            const status = deliveryStatus(delivery, endpoint);
            return (
              <tr key={delivery.id}>
                <td data-label="이벤트">
                  {WEBHOOK_EVENT_LABEL[delivery.event]}
                  {delivery.mentioned === null ? null : <span className="faint"> @{delivery.mentioned}</span>}
                </td>
                <td data-label="발생">{formatDateTime(delivery.occurredAt)}</td>
                <td data-label="상태">
                  <span className={`webhook-status webhook-status-${status.tone}`}>{status.text}</span>
                </td>
                <td data-label="다음 시도">
                  {delivery.nextAttemptAt === null || !endpoint.active
                    ? '—'
                    : formatDateTime(delivery.nextAttemptAt)}
                </td>
                <td data-label="webhook-id" className="mono small">
                  {delivery.webhookId}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <button type="button" className="btn btn-small" onClick={deliveries.reload}>
        새로 고침
      </button>
    </div>
  );
}

function EndpointCard({
  endpoint,
  onAction,
}: {
  endpoint: WebhookEndpoint;
  onAction: (pending: Pending) => void;
}) {
  const [open, setOpen] = useState(false);
  return (
    <li className={endpoint.active ? 'card webhook-endpoint' : 'card webhook-endpoint connection-inactive'}>
      <div className="webhook-endpoint-head">
        <div>
          <strong className="mono">{endpoint.url}</strong>
          <div className="faint small">
            {endpoint.events.map((event) => WEBHOOK_EVENT_LABEL[event]).join(' · ')} ·{' '}
            {formatDate(endpoint.createdAt)} 등록
            {endpoint.secretRotatedAt === null
              ? null
              : ` · ${formatDate(endpoint.secretRotatedAt)} 비밀 교체`}
          </div>
          {endpoint.stopReason === null ? null : (
            <span className="badge badge-neutral">
              {STOP_REASON_LABEL[endpoint.stopReason]}
              {endpoint.stoppedAt === null ? null : ` · ${formatDate(endpoint.stoppedAt)}`}
            </span>
          )}
        </div>
        <div className="connection-actions">
          <button type="button" className="btn btn-small" onClick={() => setOpen(!open)}>
            {open ? '기록 닫기' : '전달 기록'}
          </button>
          <button
            type="button"
            className="btn btn-small"
            disabled={!endpoint.active}
            onClick={() => onAction({ action: 'rotate', endpoint })}
          >
            비밀 교체
          </button>
          <button
            type="button"
            className="btn btn-small btn-danger"
            disabled={!endpoint.active}
            onClick={() => onAction({ action: 'stop', endpoint })}
          >
            중지
          </button>
        </div>
      </div>
      {open ? <Deliveries endpoint={endpoint} /> : null}
    </li>
  );
}

export function WebhooksPage() {
  const services = useServices();
  const toast = useToast();
  const endpoints = useLoad('webhooks', () => services.webhooks.list());
  const [registering, setRegistering] = useState(false);
  const [pending, setPending] = useState<Pending | null>(null);
  const [issued, setIssued] = useState<{ value: IssuedWebhook; title: string } | null>(null);
  usePageTitle('Webhook');

  return (
    <div className="page">
      <div className="page-header">
        <h1>Webhook</h1>
        <button type="button" className="btn btn-primary" onClick={() => setRegistering(true)}>
          <Icon name="plus" size={14} />
          Webhook 등록
        </button>
      </div>

      <ContractNotes />

      <section className="section">
        <h2 className="section-title">엔드포인트</h2>
        {endpoints.state === 'loading' ? <Loading /> : null}
        {endpoints.state === 'error' ? <ErrorBox error={endpoints.error} onRetry={endpoints.reload} /> : null}
        {endpoints.state === 'ready' && endpoints.data.length === 0 ? (
          <EmptyState title="등록한 webhook이 없습니다">
            <span className="small">받을 주소를 등록하면 그때부터 생기는 이벤트를 보냅니다.</span>
          </EmptyState>
        ) : null}
        {endpoints.state === 'ready' && endpoints.data.length > 0 ? (
          <ul className="webhook-endpoints">
            {endpoints.data.map((endpoint) => (
              <EndpointCard key={endpoint.id} endpoint={endpoint} onAction={setPending} />
            ))}
          </ul>
        ) : null}
      </section>

      {registering ? (
        <RegisterDialog
          onClose={() => setRegistering(false)}
          onIssued={(value) => {
            setRegistering(false);
            setIssued({ value, title: 'Webhook 등록됨' });
            endpoints.reload();
          }}
        />
      ) : null}
      {pending === null ? null : (
        <ConfirmDialog
          pending={pending}
          onClose={() => setPending(null)}
          onDone={(value) => {
            setPending(null);
            if (value === null) toast('Webhook을 중지했습니다');
            else setIssued({ value, title: '서명 비밀 교체됨' });
            endpoints.reload();
          }}
        />
      )}
      {issued === null ? null : (
        <SecretDialog issued={issued.value} title={issued.title} onClose={() => setIssued(null)} />
      )}
    </div>
  );
}
