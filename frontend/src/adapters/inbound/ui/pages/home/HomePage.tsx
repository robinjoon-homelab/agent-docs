import { Link, useSearchParams } from 'react-router';
import { documentHref } from '../../../../../domain/document/document';
import type { Space } from '../../../../../domain/space/space';
import { spaceInitial } from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { useSession } from '../../app/SessionContext';
import { ActorChip } from '../../common/ActorChip';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { EmptyState, ErrorBox, Loading } from '../../common/States';
import { StatusBadge } from '../../common/StatusBadge';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import { NewSpaceDialog } from './NewSpaceDialog';
import { UsageCard } from './UsageCard';
import './home.css';

const RECENT_LIMIT = 10;

function SpaceCard({ space }: { space: Space }) {
  return (
    <Link className="card space-card" to={`/s/${encodeURIComponent(space.slug)}`}>
      <div className="space-card-head">
        <span className="space-icon space-card-icon">{spaceInitial(space.name)}</span>
        <span className="space-card-name">{space.name}</span>
      </div>
      {space.description === '' ? null : (
        <p className="space-card-description muted small">{space.description}</p>
      )}
      <div className="space-card-meta faint small">
        <span>문서 {space.documentCount}개</span>
        {space.lastActivityAt === null ? null : (
          <span>
            · <RelativeTime iso={space.lastActivityAt} />
          </span>
        )}
      </div>
    </Link>
  );
}

/** Recent documents across spaces; each entry keeps its space so the link and label are right. */
function RecentDocuments({ spaces }: { spaces: readonly Space[] }) {
  const services = useServices();
  const key = spaces.map((space) => space.slug).join(',');
  const recent = useLoad(`home:recent:${key}`, () => services.documents.recentAcross(spaces, RECENT_LIMIT));

  if (recent.state === 'loading') return <Loading />;
  if (recent.state === 'error') return <ErrorBox error={recent.error} onRetry={recent.reload} />;
  if (recent.data.length === 0) return <p className="faint small">최근 문서가 없습니다.</p>;
  return (
    <ul className="list card home-list">
      {recent.data.map(({ space, document }) => (
        <li key={document.id}>
          <Link className="list-row list-row-link" to={documentHref(space.slug, document.id)}>
            <div className="row-main">
              <div className="row-title">{document.title === '' ? document.path : document.title}</div>
              <div className="row-sub">
                {space.name} · {document.path}
              </div>
            </div>
            <span className="home-row-actor">
              <ActorChip actor={document.updatedBy} />
            </span>
            <StatusBadge status={document.status} />
            <span className="faint small home-row-time">
              <RelativeTime iso={document.updatedAt} />
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}

/** S2: my spaces, recent documents across spaces and, for the owner, usage metrics. */
export function HomePage() {
  const services = useServices();
  const { me } = useSession();
  const [params, setParams] = useSearchParams();
  const spaces = useLoad('home:spaces', () => services.spaces.list());
  usePageTitle('홈');

  const dialogOpen = params.get('new') === 'space';
  const openDialog = (): void => setParams({ new: 'space' });
  const closeDialog = (): void => {
    const next = new URLSearchParams(params);
    next.delete('new');
    setParams(next, { replace: true });
  };

  const newSpaceButton = (
    <button type="button" className="btn btn-primary" onClick={openDialog}>
      <Icon name="plus" size={14} />새 스페이스
    </button>
  );

  return (
    <div className="page">
      <div className="page-header">
        <h1>홈</h1>
        {spaces.state === 'ready' && spaces.data.length > 0 ? newSpaceButton : null}
      </div>
      {spaces.state === 'loading' ? <Loading /> : null}
      {spaces.state === 'error' ? <ErrorBox error={spaces.error} onRetry={spaces.reload} /> : null}
      {spaces.state === 'ready' && spaces.data.length === 0 ? (
        <EmptyState title="아직 스페이스가 없습니다" actions={newSpaceButton}>
          <p className="muted" style={{ margin: 0 }}>
            스페이스를 만들어 문서를 모으세요. 접근 범위가 다른 문서는 다른 스페이스에 두세요.
          </p>
        </EmptyState>
      ) : null}
      {spaces.state === 'ready' && spaces.data.length > 0 ? (
        <>
          <section className="section" aria-labelledby="home-spaces">
            <h2 className="section-title" id="home-spaces">
              내 스페이스
            </h2>
            <div className="grid-cards">
              {spaces.data.map((space) => (
                <SpaceCard key={space.id} space={space} />
              ))}
            </div>
          </section>
          <section className="section" aria-labelledby="home-recent">
            <h2 className="section-title" id="home-recent">
              최근 문서
            </h2>
            <RecentDocuments spaces={spaces.data} />
          </section>
        </>
      ) : null}
      {me.owner ? (
        <section className="section" aria-labelledby="home-usage">
          <h2 className="section-title" id="home-usage">
            사용 현황
          </h2>
          <UsageCard />
        </section>
      ) : null}
      {dialogOpen ? <NewSpaceDialog onClose={closeDialog} /> : null}
    </div>
  );
}
