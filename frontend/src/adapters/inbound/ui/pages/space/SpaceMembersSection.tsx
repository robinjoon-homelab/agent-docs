import { isApiError } from '../../../../../application/shared/ports/errors';
import { MembershipConflictError } from '../../../../../application/membership/ports/MembershipConflictError';
import { useState } from 'react';
import type { MembershipRole } from '../../../../../domain/membership/membership';
import { MEMBERSHIP_ROLE_LABEL } from '../../../../../domain/membership/membership';
import { useServices } from '../../../../../config/ServicesContext';
import { useLoad } from '../../hooks/useLoad';
import { ErrorBox, Loading, errorMessage } from '../../common/States';

export function SpaceMembersSection({ spaceId }: { spaceId: string }) {
  const { memberships } = useServices();
  const members = useLoad(`members:${spaceId}`, () => memberships.list(spaceId));
  const [login, setLogin] = useState('');
  const [role, setRole] = useState<MembershipRole>('editor');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [conflictId, setConflictId] = useState<string | null>(null);
  const change = async (operation: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    setConflictId(null);
    try {
      await operation();
      members.reload();
    } catch (failure) {
      setError(failure);
      if (failure instanceof MembershipConflictError) {
        setConflictId(failure.membershipId);
        members.reload();
      } else if (isApiError(failure, 'not_found')) {
        members.reload();
      }
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="section" aria-label="스페이스 멤버">
      <h2 className="section-title">멤버</h2>
      <p className="muted small">
        GitHub 로그인으로 초대합니다. 메일이나 수락 링크는 보내지 않습니다. 대상자가 GitHub로 다시 로그인하면
        이 스페이스에 참여합니다.
      </p>
      <form
        className="card settings-form"
        onSubmit={(event) => {
          event.preventDefault();
          void change(async () => {
            await memberships.invite({ space: spaceId, login, role });
            setLogin('');
          });
        }}
      >
        <label className="field">
          GitHub 로그인
          <input
            className="input"
            required
            maxLength={39}
            value={login}
            onChange={(event) => setLogin(event.target.value)}
          />
        </label>
        <label className="field">
          역할
          <select
            className="select"
            value={role}
            onChange={(event) => setRole(event.target.value as MembershipRole)}
          >
            <option value="editor">편집자</option>
            <option value="commenter">댓글 작성자</option>
          </select>
        </label>
        <p className="muted small">
          편집자는 문서 편집·이동·휴지통과 댓글을, 댓글 작성자는 읽기와 댓글을 할 수 있습니다. 연결 승인과
          영구 삭제는 소유자만 합니다.
        </p>
        <button className="btn btn-primary" type="submit" disabled={busy}>
          초대
        </button>
      </form>
      {error === null ? null : (
        <p role="alert" className="field-error">
          {errorMessage(error)}
        </p>
      )}
      {members.state === 'loading' ? (
        <Loading />
      ) : members.state === 'error' ? (
        <ErrorBox error={members.error} onRetry={members.reload} />
      ) : members.data.length === 0 ? (
        <p className="muted">초대한 멤버가 없습니다.</p>
      ) : (
        <ul className="list card">
          {members.data.map((member) => (
            <li
              className="list-row"
              key={member.id}
              id={`membership-${member.id}`}
              aria-label={member.id === conflictId ? `기존 멤버 ${member.login}` : undefined}
            >
              <img src={member.avatarUrl} alt="" width={28} height={28} />
              <div className="row-main">
                <strong>@{member.login}</strong>
                {member.id === conflictId ? (
                  <span className="badge">기존 초대 · 아래에서 역할을 변경하세요</span>
                ) : null}
                <p className="small muted">{member.state === 'pending' ? '첫 로그인 대기' : '참여 중'}</p>
              </div>
              <select
                className="select"
                aria-label={`${member.login} 역할`}
                value={member.role}
                disabled={busy}
                onChange={(event) =>
                  void change(() => memberships.changeRole(member.id, event.target.value as MembershipRole))
                }
              >
                {(['editor', 'commenter'] as const).map((option) => (
                  <option key={option} value={option}>
                    {MEMBERSHIP_ROLE_LABEL[option]}
                  </option>
                ))}
              </select>
              <button
                className="btn btn-small"
                type="button"
                disabled={busy}
                onClick={() => {
                  if (window.confirm(`@${member.login}의 이 스페이스 접근을 회수할까요?`))
                    void change(() => memberships.revoke(member.id));
                }}
              >
                접근 회수
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
