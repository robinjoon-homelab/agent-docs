import { useState } from 'react';
import { Link } from 'react-router';
import { referenceRepository } from '../../../../../domain/document/githubReferences';
import type { Space } from '../../../../../domain/space/space';
import {
  SPACE_GUIDE_ISSUE_TEXT,
  githubContextOf,
  linkedRepositories,
} from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { ErrorBox, Loading, errorMessage } from '../../common/States';
import { useToast } from '../../common/Toast';
import { useLoad } from '../../hooks/useLoad';

type SectionProps = { space: Space; onSaved: (space: Space) => void };

function documentHref(space: Space, documentId: string): string {
  return `/s/${encodeURIComponent(space.slug)}/d/${encodeURIComponent(documentId)}`;
}

/** F5-9: the document agents read before writing here. Only documents of this space outside the trash are offered. */
export function AgentGuideSection({ space, onSaved }: SectionProps) {
  const services = useServices();
  const toast = useToast();
  const documents = useLoad(`space-settings:guide:${space.slug}`, () =>
    services.documents.listInSpace(space.slug),
  );
  const [chosen, setChosen] = useState(space.agentGuide?.documentId ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const save = async (documentId: string | null): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      const updated = await services.spaces.setAgentGuide(space, documentId);
      setChosen(updated.agentGuide?.documentId ?? '');
      toast(documentId === null ? '지침 문서 지정을 해제했습니다' : '지침 문서를 지정했습니다');
      onSaved(updated);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  };

  const guide = space.agentGuide ?? null;
  const issue = space.agentGuideIssue ?? null;
  return (
    <>
      <p className="small muted">
        작성 규칙, 용어, 템플릿을 담은 문서를 지정하면 에이전트가 MCP 연결 안내와 spaces_list·spaces_resolve,
        docs_read 응답에서 이 문서를 먼저 읽으라는 안내를 받습니다.
      </p>
      {guide === null ? null : (
        <p className="small">
          현재 지침: <Link to={documentHref(space, guide.documentId)}>{guide.title}</Link>{' '}
          <span className="faint mono">{guide.path}</span>
        </p>
      )}
      {issue === null ? null : <p className="field-error">{SPACE_GUIDE_ISSUE_TEXT[issue]}</p>}
      {documents.state === 'loading' ? <Loading /> : null}
      {documents.state === 'error' ? <ErrorBox error={documents.error} onRetry={documents.reload} /> : null}
      {documents.state === 'ready' ? (
        <div className="guidance-row">
          <select
            className="select"
            aria-label="지침 문서"
            value={chosen}
            onChange={(event) => setChosen(event.target.value)}
          >
            <option value="">지침 문서 없음</option>
            {documents.data.map((document) => (
              <option key={document.id} value={document.id}>
                {document.title === '' ? document.path : `${document.title} (${document.path})`}
              </option>
            ))}
          </select>
          <button
            type="button"
            className="btn btn-small"
            disabled={busy || chosen === '' || chosen === guide?.documentId}
            onClick={() => void save(chosen)}
          >
            지정
          </button>
          <button
            type="button"
            className="btn btn-small"
            disabled={busy || (guide === null && issue === null)}
            onClick={() => void save(null)}
          >
            해제
          </button>
        </div>
      ) : null}
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </>
  );
}

/**
 * F2-8: which linked repository short references (#123, a commit, a file path) stand for. One linked repository is
 * used as is; with several, nothing is linked until one is chosen. An organization link alone never names one.
 */
export function DefaultRepositorySection({ space, onSaved }: SectionProps) {
  const services = useServices();
  const toast = useToast();
  const repositories = linkedRepositories(space);
  const [chosen, setChosen] = useState(space.defaultRepository ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const effective = referenceRepository(githubContextOf(space));

  const save = async (): Promise<void> => {
    setBusy(true);
    setError(null);
    try {
      const updated = await services.spaces.setDefaultRepository(space, chosen === '' ? null : chosen);
      setChosen(updated.defaultRepository ?? '');
      toast(chosen === '' ? '기본 레포 선택을 해제했습니다' : '기본 레포를 저장했습니다');
      onSaved(updated);
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  };

  if (repositories.length === 0) {
    return (
      <p className="small muted">
        연결한 GitHub 저장소가 없어 문서의 짧은 참조를 링크하지 않습니다. 일반 탭에서 저장소를 연결하세요.
      </p>
    );
  }
  if (repositories.length === 1) {
    return (
      <p className="small muted">
        연결한 저장소가 하나라 짧은 참조는 <span className="mono">{repositories[0]}</span>로 링크합니다. 파일
        경로는 기본 브랜치(HEAD)로 엽니다.
      </p>
    );
  }
  return (
    <>
      <p className="small muted">
        저장소를 여럿 연결하면 고른 기본 저장소가 있을 때만 짧은 참조를 링크합니다. 파일 경로는 기본
        브랜치(HEAD)로 엽니다. 지금:{' '}
        {effective === null ? '링크하지 않음' : <span className="mono">{effective}</span>}
      </p>
      <div className="guidance-row">
        <select
          className="select"
          aria-label="기본 저장소"
          value={chosen}
          onChange={(event) => setChosen(event.target.value)}
        >
          <option value="">선택 안 함</option>
          {repositories.map((repository) => (
            <option key={repository} value={repository}>
              {repository}
            </option>
          ))}
        </select>
        <button
          type="button"
          className="btn btn-small"
          disabled={busy || chosen === (space.defaultRepository ?? '')}
          onClick={() => void save()}
        >
          저장
        </button>
      </div>
      {error === null ? null : <p className="field-error">{errorMessage(error)}</p>}
    </>
  );
}
