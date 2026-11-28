import { useSession } from '../../app/SessionContext';
import { SpaceMembersSection } from './SpaceMembersSection';
import { useState } from 'react';
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router';
import {
  CONNECTION_KIND_LABEL,
  SCOPE_LABEL,
  allowsSpace,
  expiryState,
} from '../../../../../domain/connection/connection';
import type { Space, SpaceDraft, SpaceLink, SpaceLinkKind } from '../../../../../domain/space/space';
import {
  SPACE_DRAFT_ERROR_TEXT,
  SPACE_LINK_KINDS,
  SPACE_LINK_LABEL,
  draftOf,
  isArchived,
} from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { useShell } from '../../app/ShellContext';
import { Icon } from '../../common/Icon';
import { ErrorBox, Loading, LoadError, NotFound, errorMessage } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';
import { useNow } from '../../hooks/useNow';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useSpace } from '../../hooks/useSpace';
import { AgentGuideSection, DefaultRepositorySection } from './SpaceGuidanceSections';
import { TrashSection } from './TrashSection';
import './space.css';

const LINK_PLACEHOLDER: Record<SpaceLinkKind, string> = {
  github_repository: 'owner/repo',
  github_organization: 'organization',
  local_path: '/Users/me/projects/agent-docs (pwd 결과)',
  url: 'https://example.com',
};

function LinksEditor({
  links,
  onChange,
}: {
  links: readonly SpaceLink[];
  onChange: (links: SpaceLink[]) => void;
}) {
  const replace = (index: number, link: SpaceLink): void =>
    onChange(links.map((existing, position) => (position === index ? link : existing)));
  return (
    <div className="links-editor">
      {links.length === 0 ? <p className="faint small">외부 링크가 없습니다.</p> : null}
      {links.map((link, index) => (
        <div key={index} className="links-editor-row">
          <select
            className="select"
            aria-label="링크 종류"
            value={link.kind}
            onChange={(event) => replace(index, { ...link, kind: event.target.value as SpaceLinkKind })}
          >
            {SPACE_LINK_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {SPACE_LINK_LABEL[kind]}
              </option>
            ))}
          </select>
          <input
            className="input"
            aria-label="링크 값"
            value={link.value}
            placeholder={LINK_PLACEHOLDER[link.kind]}
            onChange={(event) => replace(index, { ...link, value: event.target.value })}
          />
          <button
            type="button"
            className="icon-btn"
            aria-label="링크 삭제"
            onClick={() => onChange(links.filter((_, position) => position !== index))}
          >
            <Icon name="close" size={14} />
          </button>
        </div>
      ))}
      <button
        type="button"
        className="btn btn-small"
        onClick={() => onChange([...links, { kind: 'github_repository', value: '' }])}
      >
        <Icon name="plus" size={14} />
        링크 추가
      </button>
    </div>
  );
}

function SpaceForm({ space, onSaved }: { space: Space; onSaved: (space: Space) => void }) {
  const services = useServices();
  const shell = useShell();
  const toast = useToast();
  const navigate = useNavigate();
  const [draft, setDraft] = useState<SpaceDraft>(() => draftOf(space));
  const [errors, setErrors] = useState<readonly string[]>([]);
  const [busy, setBusy] = useState(false);
  const update = (patch: Partial<SpaceDraft>): void => setDraft((current) => ({ ...current, ...patch }));

  const save = async (): Promise<void> => {
    setBusy(true);
    try {
      const result = await services.spaces.update(space, draft);
      if (!result.ok) {
        setErrors(result.error.map((error) => SPACE_DRAFT_ERROR_TEXT[error]));
        return;
      }
      setErrors([]);
      shell.refreshSpaces();
      toast('스페이스 설정을 저장했습니다');
      if (result.value.slug !== space.slug) {
        void navigate(`/s/${encodeURIComponent(result.value.slug)}/settings`, { replace: true });
        return;
      }
      onSaved(result.value);
    } catch (error) {
      setErrors([errorMessage(error)]);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form
      className="card settings-form"
      onSubmit={(event) => {
        event.preventDefault();
        void save();
      }}
    >
      <div className="field">
        <label htmlFor="space-name">이름</label>
        <input
          id="space-name"
          className="input"
          value={draft.name}
          onChange={(event) => update({ name: event.target.value })}
        />
      </div>
      <div className="field">
        <label htmlFor="space-slug">슬러그</label>
        <input
          id="space-slug"
          className="input mono"
          value={draft.slug}
          onChange={(event) => update({ slug: event.target.value })}
        />
        <span className="field-hint">
          슬러그를 바꿔도 이전 주소(/s/{space.slug})는 새 주소로 계속 연결됩니다.
        </span>
      </div>
      <div className="field">
        <label htmlFor="space-description">설명</label>
        <textarea
          id="space-description"
          className="textarea"
          value={draft.description}
          onChange={(event) => update({ description: event.target.value })}
        />
      </div>
      <div className="field">
        <span className="field-label">외부 링크</span>
        <LinksEditor links={draft.links} onChange={(links) => update({ links })} />
      </div>
      {errors.map((error) => (
        <p key={error} className="field-error">
          {error}
        </p>
      ))}
      <div className="settings-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          저장
        </button>
      </div>
    </form>
  );
}

function ConnectionsSection({ space }: { space: Space }) {
  const services = useServices();
  const now = useNow();
  const connections = useLoad('space-settings:connections', () => services.connections.list());

  if (connections.state === 'loading') return <Loading />;
  if (connections.state === 'error')
    return <ErrorBox error={connections.error} onRetry={connections.reload} />;
  const allowed = connections.data.filter(
    (connection) => connection.revokedAt === null && allowsSpace(connection, space.id),
  );
  return (
    <>
      {allowed.length === 0 ? (
        <p className="faint small">이 스페이스를 허용한 연결이 없습니다.</p>
      ) : (
        <ul className="list card space-list">
          {allowed.map((connection) => {
            const expiry = expiryState(connection.expiresAt, now);
            return (
              <li key={connection.id} className="list-row">
                <div className="row-main">
                  <div className="row-title">{connection.clientHost ?? connection.name}</div>
                  <div className="row-sub">
                    {connection.clientHost === null
                      ? CONNECTION_KIND_LABEL[connection.kind]
                      : `${connection.name} · ${CONNECTION_KIND_LABEL[connection.kind]}`}
                  </div>
                </div>
                {expiry === 'ok' ? null : (
                  <span className={`badge ${expiry === 'expired' ? 'badge-danger' : 'badge-warning'}`}>
                    {expiry === 'expired' ? '만료됨' : '곧 만료'}
                  </span>
                )}
                <span className="badge badge-neutral">{SCOPE_LABEL[connection.scope]}</span>
              </li>
            );
          })}
        </ul>
      )}
      <p className="small" style={{ marginTop: 8 }}>
        <Link to="/settings/agents">에이전트 연결 관리 →</Link>
      </p>
    </>
  );
}

/** Archiving keeps every document; the space leaves lists and an agent's space lookup, while authorized search includes it. */
function ArchiveSection({ space, onSaved }: { space: Space; onSaved: (space: Space) => void }) {
  const services = useServices();
  const shell = useShell();
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const archived = isArchived(space);

  const toggle = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      const updated = await services.spaces.setArchived(space, !archived);
      shell.refreshSpaces();
      toast(archived ? '스페이스를 보관 해제했습니다' : '스페이스를 보관했습니다');
      onSaved(updated);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <p className="small muted">
        {archived
          ? '보관한 스페이스입니다. 목록과 에이전트의 스페이스 찾기에 다시 나오게 하려면 보관을 해제하세요.'
          : '보관하면 스페이스 목록과 에이전트의 스페이스 찾기에서 빠집니다. 문서는 그대로 남고 주소로 열리며, 권한이 있는 전체 검색·제목 찾기에는 계속 나옵니다.'}
      </p>
      <button type="button" className="btn" disabled={busy} onClick={() => void toggle()}>
        {archived ? '보관 해제' : '스페이스 보관'}
      </button>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </>
  );
}

function SpaceSettings({ space, onSaved }: { space: Space; onSaved: (space: Space) => void }) {
  usePageTitle(`${space.name} 설정`);
  return (
    <div className="page page-narrow">
      <div className="page-header">
        <h1>스페이스 설정</h1>
        <Link className="btn" to={`/s/${encodeURIComponent(space.slug)}`}>
          {space.name}(으)로
        </Link>
      </div>
      <section className="section" aria-labelledby="settings-general">
        <h2 className="section-title" id="settings-general">
          일반
        </h2>
        <SpaceForm key={`${space.id}:${space.slug}`} space={space} onSaved={onSaved} />
      </section>
      <section className="section" aria-labelledby="settings-guide">
        <h2 className="section-title" id="settings-guide">
          에이전트 지침 문서
        </h2>
        <AgentGuideSection
          key={`${space.id}:${space.agentGuide?.documentId ?? ''}:${space.agentGuideIssue ?? ''}`}
          space={space}
          onSaved={onSaved}
        />
      </section>
      <section className="section" aria-labelledby="settings-repository">
        <h2 className="section-title" id="settings-repository">
          GitHub 기본 저장소
        </h2>
        <DefaultRepositorySection
          key={`${space.id}:${space.defaultRepository ?? ''}:${JSON.stringify(space.links)}`}
          space={space}
          onSaved={onSaved}
        />
      </section>
      <SpaceMembersSection spaceId={space.id} />
      <section className="section" aria-labelledby="settings-connections">
        <h2 className="section-title" id="settings-connections">
          이 스페이스를 허용한 연결
        </h2>
        <ConnectionsSection space={space} />
      </section>
      <section className="section" aria-labelledby="settings-trash">
        <h2 className="section-title" id="settings-trash">
          휴지통
        </h2>
        <TrashSection spaceSlug={space.slug} />
      </section>
      <section className="section" aria-labelledby="settings-archive">
        <h2 className="section-title" id="settings-archive">
          보관
        </h2>
        <ArchiveSection space={space} onSaved={onSaved} />
      </section>
    </div>
  );
}

function AuthorizedSpaceSettingsPage() {
  const { space: slug = '' } = useParams();
  const location = useLocation();
  const space = useSpace(slug);
  if (space.state === 'loading') return <Loading />;
  if (space.state === 'error') return <LoadError error={space.error} onRetry={space.reload} />;
  if (space.data.slug !== slug) {
    return (
      <Navigate
        to={`/s/${encodeURIComponent(space.data.slug)}/settings${location.search}${location.hash}`}
        replace
      />
    );
  }
  return <SpaceSettings space={space.data} onSaved={(updated) => space.setData(() => updated)} />;
}

/**
 * S8: space name, slug, description, external links, the agent guide, the default GitHub repository, trash,
 * archiving and the connections allowed here.
 */
export function SpaceSettingsPage() {
  const { me } = useSession();
  if (!me.owner) return <NotFound />;
  return <AuthorizedSpaceSettingsPage />;
}
