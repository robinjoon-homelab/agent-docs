import { useState } from 'react';
import { useSearchParams } from 'react-router';
import type { Connection, ConnectionScope, IssuedToken } from '../../../../../domain/connection/connection';
import {
  CONNECTION_KIND_LABEL,
  DEFAULT_EXPIRY_DAYS,
  expiryState,
  GRANT_ERROR_TEXT,
  grantedSpaceNames,
  mcpSetupCommands,
  SCOPE_LABEL,
} from '../../../../../domain/connection/connection';
import type { Space } from '../../../../../domain/space/space';
import { daysUntil, formatDate } from '../../../../../domain/shared/time';
import { useServices } from '../../../../../config/ServicesContext';
import { CopyButton } from '../../common/CopyButton';
import { Dialog } from '../../common/Dialog';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { EmptyState, ErrorBox, errorMessage, Loading } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { ScopePicker, SpaceCheckboxes } from './ConnectionGrantFields';
import { RegrantDialog } from './ConnectionRegrantDialog';
import './settings.css';

function CommandLine({ command }: { command: string }) {
  return (
    <div className="command-line">
      <code>{command}</code>
      <CopyButton text={command} label="복사" done="명령을 복사했습니다" />
    </div>
  );
}

function SetupGuide() {
  const setup = mcpSetupCommands(window.location.origin);
  return (
    <section className="section card setup-guide">
      <h2 className="section-title">MCP 서버 추가</h2>
      <ol className="setup-steps">
        <li>
          <p>쓰는 에이전트에 MCP 서버를 추가합니다.</p>
          {setup.add.map((command) => (
            <CommandLine key={command} command={command} />
          ))}
        </li>
        <li>
          <p>로그인하면 브라우저에 연결 승인 화면이 열립니다. 허용할 스페이스와 권한을 고르세요.</p>
          {setup.login.map((command) => (
            <CommandLine key={command} command={command} />
          ))}
        </li>
      </ol>
    </section>
  );
}

function ExpiryCell({ connection, now }: { connection: Connection; now: Date }) {
  if (connection.revokedAt !== null) return <span className="badge badge-neutral">취소됨</span>;
  const state = expiryState(connection.expiresAt, now);
  if (state === 'expired') return <span className="badge badge-danger">만료됨</span>;
  return (
    <span className="expiry-cell">
      {formatDate(connection.expiresAt)}
      {state === 'soon' ? (
        <span className="badge badge-warning">
          <Icon name="warning" size={12} />
          {Math.max(1, Math.ceil(daysUntil(connection.expiresAt, now)))}일 남음
        </span>
      ) : null}
    </span>
  );
}

function ConnectionRow({
  connection,
  spaces,
  now,
  onRegrant,
  onRevoke,
}: {
  connection: Connection;
  /** All spaces, for turning the granted space ids into names; null while loading. */
  spaces: readonly Space[] | null;
  now: Date;
  onRegrant: () => void;
  onRevoke: () => void;
}) {
  const names = spaces === null ? null : grantedSpaceNames(connection, spaces);
  const inactive = connection.revokedAt !== null || expiryState(connection.expiresAt, now) === 'expired';
  return (
    <tr className={inactive ? 'connection-inactive' : undefined}>
      <td data-label="이름">
        <strong>{connection.name === '' ? '이름 없음' : connection.name}</strong>
        {connection.clientHost === null ? null : <div className="faint small">{connection.clientHost}</div>}
      </td>
      <td data-label="종류">{CONNECTION_KIND_LABEL[connection.kind]}</td>
      <td data-label="허용 범위">
        <span title={names?.join(', ')}>
          스페이스 {connection.spaceIds.length}개 · {SCOPE_LABEL[connection.scope]}
        </span>
        {names === null || names.length === 0 ? null : <div className="faint small">{names.join(', ')}</div>}
      </td>
      <td data-label="생성">{formatDate(connection.createdAt)}</td>
      <td data-label="마지막 사용">
        {connection.lastUsedAt === null ? (
          <span className="faint">사용 안 함</span>
        ) : (
          <RelativeTime iso={connection.lastUsedAt} />
        )}
      </td>
      <td data-label="만료">
        <ExpiryCell connection={connection} now={now} />
      </td>
      <td className="connection-actions">
        <button type="button" className="btn btn-small" disabled={inactive} onClick={onRegrant}>
          범위 변경
        </button>
        <button
          type="button"
          className="btn btn-small btn-danger"
          disabled={connection.revokedAt !== null}
          onClick={onRevoke}
        >
          연결 취소
        </button>
      </td>
    </tr>
  );
}

function RevokeDialog({
  connection,
  onClose,
  onRevoked,
}: {
  connection: Connection;
  onClose: () => void;
  onRevoked: () => void;
}) {
  const services = useServices();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const revoke = async (): Promise<void> => {
    setBusy(true);
    try {
      await services.connections.revoke(connection.id);
      onRevoked();
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  };
  return (
    <Dialog
      open
      title="연결 취소"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            닫기
          </button>
          <button type="button" className="btn btn-danger" disabled={busy} onClick={() => void revoke()}>
            연결 취소
          </button>
        </>
      }
    >
      <p>
        ‘{connection.name}’ 연결을 취소하면 이 {connection.kind === 'script' ? '토큰' : '에이전트'}은(는) 더
        이상 문서에 접근할 수 없습니다. 되돌릴 수 없습니다.
      </p>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </Dialog>
  );
}

function IssueTokenDialog({ onClose, onIssued }: { onClose: () => void; onIssued: () => void }) {
  const services = useServices();
  const spaces = useLoad('token-spaces', () => services.spaces.list());
  const [name, setName] = useState('');
  const [spaceIds, setSpaceIds] = useState<readonly string[]>([]);
  const [scope, setScope] = useState<ConnectionScope>('read');
  const [days, setDays] = useState(String(DEFAULT_EXPIRY_DAYS));
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const [issued, setIssued] = useState<IssuedToken | null>(null);

  const submit = async (): Promise<void> => {
    setBusy(true);
    setErrors([]);
    try {
      const result = await services.connections.issueScriptToken({
        name,
        spaceIds,
        scope,
        expiresInDays: Number(days),
      });
      if (!result.ok) {
        setErrors(result.error.map((error) => GRANT_ERROR_TEXT[error]));
        return;
      }
      setIssued(result.value);
      onIssued();
    } catch (failure) {
      setErrors([errorMessage(failure)]);
    } finally {
      setBusy(false);
    }
  };

  if (issued !== null) {
    return (
      <Dialog
        open
        title="스크립트 토큰 발급됨"
        onClose={onClose}
        footer={
          <button type="button" className="btn btn-primary" onClick={onClose}>
            복사했습니다
          </button>
        }
      >
        <div className="warning-box">
          이 토큰은 다시 표시되지 않습니다. 지금 복사해 안전한 곳에 보관하세요.
        </div>
        <div className="token-display">
          <input
            className="input mono"
            readOnly
            value={issued.token}
            aria-label="발급된 토큰"
            onFocus={(event) => event.target.select()}
          />
          <CopyButton text={issued.token} label="토큰 복사" done="토큰을 복사했습니다" />
        </div>
        <p className="faint small">
          ‘{issued.connection.name}’ · 스페이스 {issued.connection.spaceIds.length}개 ·{' '}
          {SCOPE_LABEL[issued.connection.scope]} · {formatDate(issued.connection.expiresAt)} 만료
        </p>
      </Dialog>
    );
  }

  return (
    <Dialog
      open
      title="스크립트 토큰 발급"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="submit" form="issue-token" className="btn btn-primary" disabled={busy}>
            발급
          </button>
        </>
      }
    >
      <form
        id="issue-token"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        <div className="field">
          <label htmlFor="token-name">이름</label>
          <input
            id="token-name"
            className="input"
            value={name}
            autoFocus
            placeholder="예: CI 문서 동기화"
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <fieldset className="field plain-fieldset">
          <legend className="field-label">허용할 스페이스</legend>
          {spaces.state === 'loading' ? <span className="faint small">불러오는 중…</span> : null}
          {spaces.state === 'error' ? <ErrorBox error={spaces.error} onRetry={spaces.reload} /> : null}
          {spaces.state === 'ready' ? (
            <SpaceCheckboxes spaces={spaces.data} selected={spaceIds} onChange={setSpaceIds} />
          ) : null}
        </fieldset>
        <div className="field">
          <span className="field-label">권한</span>
          <ScopePicker value={scope} onChange={setScope} />
        </div>
        <div className="field">
          <label htmlFor="token-days">만료 (일)</label>
          <input
            id="token-days"
            className="input"
            type="number"
            min={1}
            max={365}
            value={days}
            onChange={(event) => setDays(event.target.value)}
            style={{ maxWidth: 140 }}
          />
        </div>
        {errors.map((error) => (
          <p key={error} className="field-error">
            {error}
          </p>
        ))}
      </form>
    </Dialog>
  );
}

export function AgentConnectionsPage() {
  const services = useServices();
  const toast = useToast();
  const now = useNow();
  const [params] = useSearchParams();
  const connections = useLoad('connections', () => services.connections.list());
  // Connections carry space ids only; names come from the space list.
  const spaces = useLoad('connections:spaces', () => services.spaces.list());
  const [regranting, setRegranting] = useState<Connection | null>(null);
  const [revoking, setRevoking] = useState<Connection | null>(null);
  const [issuing, setIssuing] = useState(false);
  usePageTitle('에이전트 연결');

  return (
    <div className="page">
      <div className="page-header">
        <h1>에이전트 연결</h1>
        <button type="button" className="btn btn-primary" onClick={() => setIssuing(true)}>
          <Icon name="plus" size={14} />
          스크립트 토큰 발급
        </button>
      </div>
      {params.get('approved') === null ? null : (
        <div className="notice-box">에이전트 연결을 승인했습니다.</div>
      )}
      {params.get('denied') === null ? null : (
        <div className="notice-box">에이전트 연결 요청을 거절했습니다.</div>
      )}

      <SetupGuide />

      <section className="section">
        <h2 className="section-title">연결</h2>
        {connections.state === 'loading' ? <Loading /> : null}
        {connections.state === 'error' ? (
          <ErrorBox error={connections.error} onRetry={connections.reload} />
        ) : null}
        {connections.state === 'ready' && connections.data.length === 0 ? (
          <EmptyState title="연결된 에이전트가 없습니다">
            <span className="small">위 명령으로 MCP 서버를 추가하거나 스크립트 토큰을 발급하세요.</span>
          </EmptyState>
        ) : null}
        {connections.state === 'ready' && connections.data.length > 0 ? (
          <div className="connections-table-wrap">
            <table className="connections-table">
              <thead>
                <tr>
                  <th>이름 / 클라이언트</th>
                  <th>종류</th>
                  <th>허용 범위</th>
                  <th>생성</th>
                  <th>마지막 사용</th>
                  <th>만료</th>
                  <th aria-label="작업" />
                </tr>
              </thead>
              <tbody>
                {connections.data.map((connection) => (
                  <ConnectionRow
                    key={connection.id}
                    connection={connection}
                    spaces={spaces.state === 'ready' ? spaces.data : null}
                    now={now}
                    onRegrant={() => setRegranting(connection)}
                    onRevoke={() => setRevoking(connection)}
                  />
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {regranting === null ? null : (
        <RegrantDialog
          connection={regranting}
          onClose={() => setRegranting(null)}
          onSaved={() => {
            setRegranting(null);
            toast('허용 범위를 바꿨습니다');
            connections.reload();
          }}
        />
      )}
      {revoking === null ? null : (
        <RevokeDialog
          connection={revoking}
          onClose={() => setRevoking(null)}
          onRevoked={() => {
            setRevoking(null);
            toast('연결을 취소했습니다');
            connections.reload();
          }}
        />
      )}
      {issuing ? <IssueTokenDialog onClose={() => setIssuing(false)} onIssued={connections.reload} /> : null}
    </div>
  );
}
