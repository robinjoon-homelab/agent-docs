import { Link, useSearchParams } from 'react-router';
import type { Space } from '../../../../../domain/space/space';
import { isArchived, spaceInitial } from '../../../../../domain/space/space';
import { useServices } from '../../../../../config/ServicesContext';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { EmptyState, ErrorBox, Loading } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';
import { usePageTitle } from '../../hooks/usePageTitle';
import { NewSpaceDialog } from './NewSpaceDialog';
import './home.css';

import type { NavigationUseCases } from '../../../../../application/navigation/navigationUseCases';
import { PersonalDocuments } from '../../navigation/PersonalDocuments';

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

/** S2: my spaces and recent documents across spaces. */
export function HomePage({ navigation }: { navigation: NavigationUseCases }) {
  const services = useServices();
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
            {spaces.data.some((space) => !isArchived(space)) ? (
              <div className="grid-cards">
                {spaces.data
                  .filter((space) => !isArchived(space))
                  .map((space) => (
                    <SpaceCard key={space.id} space={space} />
                  ))}
              </div>
            ) : (
              <p className="faint small">
                모든 스페이스를 보관했습니다. 아래에서 열거나 새 스페이스를 만드세요.
              </p>
            )}
          </section>
          <section className="section" aria-labelledby="home-recent">
            <h2 className="section-title" id="home-recent">
              최근 본 문서
            </h2>
            <PersonalDocuments navigation={navigation} kind="recent" />
          </section>
          <section className="section" aria-label="즐겨찾기">
            <h2 className="section-title">즐겨찾기</h2>
            <PersonalDocuments navigation={navigation} kind="favorites" />
          </section>
          {spaces.data.some(isArchived) ? (
            <section className="section" aria-labelledby="home-archived">
              <h2 className="section-title" id="home-archived">
                보관한 스페이스
              </h2>
              <div className="grid-cards space-cards-archived">
                {spaces.data.filter(isArchived).map((space) => (
                  <SpaceCard key={space.id} space={space} />
                ))}
              </div>
            </section>
          ) : null}
        </>
      ) : null}
      {dialogOpen ? <NewSpaceDialog onClose={closeDialog} /> : null}
    </div>
  );
}
