import { Link } from 'react-router';
import type { NavigationUseCases } from '../../../../application/navigation/navigationUseCases';
import { useLoad } from '../hooks/useLoad';
import { ErrorBox, Loading } from '../common/States';

export function PersonalDocuments({
  navigation,
  kind,
}: {
  navigation: NavigationUseCases;
  kind: 'recent' | 'favorites';
}) {
  const loaded = useLoad(`navigation:${kind}`, () => navigation[kind]());
  if (loaded.state === 'loading') return <Loading />;
  if (loaded.state === 'error') return <ErrorBox error={loaded.error} onRetry={loaded.reload} />;
  if (loaded.data.total === 0)
    return (
      <p className="faint small">{kind === 'recent' ? '최근 본 문서가 없습니다.' : '즐겨찾기가 없습니다.'}</p>
    );
  return (
    <ul className="list card home-list">
      {loaded.data.documents.map((entry) => (
        <li key={entry.id}>
          <Link className="list-row list-row-link" to={`/d/${encodeURIComponent(entry.id)}`}>
            <div className="row-main">
              <div className="row-title">{entry.title}</div>
              <div className="row-sub">
                {entry.spaceName} · {entry.path}
              </div>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
