import type { ReactNode } from 'react';
import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { ConnectionScope, OAuthRequest } from '../../../../../domain/connection/connection';
import {
  CONNECTION_SCOPES,
  DEFAULT_EXPIRY_DAYS,
  GRANT_ERROR_TEXT,
  requestingClientLabel,
  SCOPE_LABEL,
} from '../../../../../domain/connection/connection';
import type { Space } from '../../../../../domain/space/space';
import { pickableSpaces } from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { ErrorBox, errorMessage, Loading } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import './oauth.css';

const CLIENT_KIND_TEXT: Record<OAuthRequest['client']['kind'], string> = {
  metadata_document: '클라이언트 메타데이터 문서로 확인한 클라이언트',
  registered: '등록된 클라이언트',
};

function Frame({ children }: { children: ReactNode }) {
  return (
    <div className="approval-screen">
      <div className="approval-card card">{children}</div>
    </div>
  );
}

function Unavailable({ message }: { message: string }) {
  return (
    <Frame>
      <h1 className="approval-title">연결 요청을 처리할 수 없습니다</h1>
      <p className="muted">{message}</p>
      <p className="faint small">에이전트에서 로그인 명령을 다시 실행하면 새 요청이 열립니다.</p>
    </Frame>
  );
}

function ApprovalForm({ request, spaces }: { request: OAuthRequest; spaces: readonly Space[] }) {
  const services = useServices();
  const [spaceIds, setSpaceIds] = useState<readonly string[]>([]);
  const [scope, setScope] = useState<ConnectionScope>(request.requestedScope ?? 'read');
  const [days, setDays] = useState(String(DEFAULT_EXPIRY_DAYS));
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);

  const finish = async (action: () => Promise<string | null>): Promise<void> => {
    setBusy(true);
    setErrors([]);
    try {
      const redirectTo = await action();
      // A full navigation hands control back to the MCP client's redirect URI.
      if (redirectTo !== null) window.location.assign(redirectTo);
      else setBusy(false);
    } catch (error) {
      setErrors([errorMessage(error)]);
      setBusy(false);
    }
  };

  const approve = (): Promise<void> =>
    finish(async () => {
      const result = await services.connections.approve(request.id, {
        spaceIds,
        scope,
        expiresInDays: Number(days),
      });
      if (result.ok) return result.value;
      setErrors(result.error.map((error) => GRANT_ERROR_TEXT[error]));
      return null;
    });

  const deny = (): Promise<void> => finish(() => services.connections.deny(request.id));

  return (
    <Frame>
      <p className="approval-eyebrow">에이전트 연결 요청</p>
      <h1 className="approval-host">{requestingClientLabel(request)}</h1>
      <p className="approval-lead">
        <strong>{request.client.name === '' ? requestingClientLabel(request) : request.client.name}</strong>
        이(가) Agent Docs 문서에 접근하려고 합니다.
      </p>
      {request.client.host === null ? (
        <p className="small muted">
          호스트로 확인할 수 없는 직접 등록 클라이언트입니다. 클라이언트 ID: <code>{request.client.id}</code>
        </p>
      ) : null}
      <dl className="approval-facts">
        <dt>클라이언트</dt>
        <dd>{CLIENT_KIND_TEXT[request.client.kind]}</dd>
        <dt>승인 후 돌아갈 곳</dt>
        <dd className="mono">{request.redirectHost}</dd>
        <dt>요청한 권한</dt>
        <dd>{request.requestedScope === null ? '지정 안 함' : SCOPE_LABEL[request.requestedScope]}</dd>
      </dl>
      {request.client.host !== null &&
      request.redirectHost !== request.client.host &&
      request.redirectHost !== 'localhost' &&
      request.redirectHost !== '127.0.0.1' ? (
        <div className="warning-box approval-warning">
          돌아갈 주소({request.redirectHost})가 클라이언트 호스트({request.client.host})와 다릅니다. 직접
          시작한 요청인지 확인하세요.
        </div>
      ) : null}

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void approve();
        }}
      >
        <fieldset className="field approval-fieldset">
          <legend className="field-label">허용할 스페이스</legend>
          {spaces.length === 0 ? (
            <p className="faint small">스페이스가 없습니다. 먼저 스페이스를 만드세요.</p>
          ) : null}
          <div className="approval-spaces">
            {pickableSpaces(spaces, spaceIds).map((space) => (
              <label key={space.id} className="checkbox-row">
                <input
                  type="checkbox"
                  checked={spaceIds.includes(space.id)}
                  onChange={(event) =>
                    setSpaceIds((current) =>
                      event.target.checked ? [...current, space.id] : current.filter((id) => id !== space.id),
                    )
                  }
                />
                {space.name}
                <span className="faint small">/{space.slug}</span>
              </label>
            ))}
          </div>
        </fieldset>
        <div className="field">
          <span className="field-label">권한</span>
          <div className="segmented" role="group" aria-label="권한">
            {CONNECTION_SCOPES.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={scope === value}
                onClick={() => setScope(value)}
              >
                {SCOPE_LABEL[value]}
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <label htmlFor="approval-days">만료 (일)</label>
          <input
            id="approval-days"
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
        <div className="approval-actions">
          <button type="button" className="btn" disabled={busy} onClick={() => void deny()}>
            거절
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            승인
          </button>
        </div>
      </form>
    </Frame>
  );
}

export function ConnectionApprovalPage() {
  const services = useServices();
  const [params] = useSearchParams();
  const requestId = params.get('request') ?? '';
  const now = useNow();
  const request = useLoad(`oauth-request:${requestId}`, () =>
    requestId === '' ? Promise.resolve(null) : services.connections.getRequest(requestId),
  );
  const spaces = useLoad('approval-spaces', () => services.spaces.list());
  usePageTitle('에이전트 연결 승인');

  if (requestId === '') return <Unavailable message="주소에 연결 요청이 없습니다." />;
  if (request.state === 'error') {
    if (isNotVisible(request.error))
      return <Unavailable message="연결 요청을 찾을 수 없거나 이미 만료되었습니다." />;
    return (
      <Frame>
        <ErrorBox error={request.error} onRetry={request.reload} />
      </Frame>
    );
  }
  if (spaces.state === 'error') {
    return (
      <Frame>
        <ErrorBox error={spaces.error} onRetry={spaces.reload} />
      </Frame>
    );
  }
  if (request.state === 'loading' || spaces.state === 'loading') return <Loading />;
  if (request.data === null) return <Unavailable message="연결 요청을 찾을 수 없습니다." />;
  if (new Date(request.data.expiresAt).getTime() <= now.getTime()) {
    return <Unavailable message="연결 요청이 만료되었습니다." />;
  }
  return <ApprovalForm request={request.data} spaces={spaces.data} />;
}
