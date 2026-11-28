import { useParams } from 'react-router';
import { canEdit, permissionInSpace } from '../../../../../domain/space/permission';
import { useSession } from '../../app/SessionContext';
import { useSpacePermissions } from '../../app/SpacePermissionsContext';
import { Loading, LoadError, NotFound } from '../../common/States';
import { usePageTitle } from '../../hooks/usePageTitle';
import { useSpace } from '../../hooks/useSpace';
import { TrashSection } from './TrashSection';

export function SpaceTrashPage() {
  const { space: slug = '' } = useParams();
  const { me } = useSession();
  const { grants } = useSpacePermissions();
  const space = useSpace(slug);
  usePageTitle('휴지통');

  if (space.state === 'loading') return <Loading />;
  if (space.state === 'error') return <LoadError error={space.error} onRetry={space.reload} />;
  if (!canEdit(permissionInSpace(space.data, me.owner, grants))) return <NotFound />;
  if (space.data.slug !== slug) return <Loading />;

  return (
    <div className="page page-narrow">
      <div className="page-header">
        <h1>휴지통</h1>
      </div>
      <TrashSection key={slug} spaceSlug={slug} />
    </div>
  );
}
