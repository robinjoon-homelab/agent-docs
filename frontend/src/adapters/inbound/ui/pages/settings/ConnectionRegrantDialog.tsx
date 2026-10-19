import { useState } from 'react';
import type { Connection, ConnectionScope } from '../../../../../domain/connection/connection';
import { useServices } from '../../../../../config/ServicesContext';
import { Dialog } from '../../common/Dialog';
import { ErrorBox, errorMessage } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';
import { ScopePicker, SpaceCheckboxes } from './ConnectionGrantFields';

/** Narrows or widens what a connection may do; the change applies from the connection's next request. */
export function RegrantDialog({
  connection,
  onClose,
  onSaved,
}: {
  connection: Connection;
  onClose: () => void;
  onSaved: () => void;
}) {
  const services = useServices();
  const spaces = useLoad('regrant-spaces', () => services.spaces.list());
  const [spaceIds, setSpaceIds] = useState<readonly string[]>(connection.spaceIds);
  const [scope, setScope] = useState<ConnectionScope>(connection.scope);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const save = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      await services.connections.update(connection.id, { spaceIds, scope });
      onSaved();
    } catch (failure) {
      setError(failure);
      setBusy(false);
    }
  };

  return (
    <Dialog
      open
      title="허용 범위 변경"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            취소
          </button>
          <button type="button" className="btn btn-primary" disabled={busy} onClick={() => void save()}>
            저장
          </button>
        </>
      }
    >
      <p className="small muted">
        ‘{connection.name}’ 연결이 쓸 수 있는 스페이스와 권한입니다. 바꾸면 이 연결의 다음 요청부터
        적용됩니다.
      </p>
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
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </Dialog>
  );
}
