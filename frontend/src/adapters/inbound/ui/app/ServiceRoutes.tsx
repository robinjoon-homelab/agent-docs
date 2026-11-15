import { useServices } from '../../../../config/ServicesContext';
import { ErrorBox, Loading } from '../common/States';
import { useLoad } from '../hooks/useLoad';
import { usePageTitle } from '../hooks/usePageTitle';
import { HomePage } from '../pages/home/HomePage';
import { MobileMemoPage } from '../pages/mobile/MobileMemoPage';
import { DataPage } from '../pages/settings/DataPage';
import { useSession } from './SessionContext';

/*
 * Route elements for pages that take their use cases and the signed-in user as props. The pages stay free of
 * the services hook; these wrappers load what a page needs before showing it.
 */

/** S2 home with the personal recent and favorite lists. */
export function HomeRoute() {
  const services = useServices();
  return <HomePage navigation={services.navigation} />;
}

/** S14 quick memo at /m, outside the app shell: it is the mobile entry point. */
export function MobileMemoRoute() {
  const services = useServices();
  const { me } = useSession();
  const spaces = useLoad('mobile:spaces', () => services.spaces.list());
  usePageTitle('빠른 메모');
  if (spaces.state === 'loading') return <Loading />;
  if (spaces.state === 'error')
    return (
      <div className="page">
        <ErrorBox error={spaces.error} onRetry={spaces.reload} />
      </div>
    );
  return <MobileMemoPage navigation={services.navigation} userId={me.userId} spaces={spaces.data} />;
}

/** S11 data at /settings/data: folder import, and ZIP export for the owner. */
export function DataRoute() {
  const services = useServices();
  const { me } = useSession();
  const spaces = useLoad('settings:data:spaces', () => services.spaces.list());
  usePageTitle('데이터');
  if (spaces.state === 'loading') return <Loading />;
  if (spaces.state === 'error')
    return (
      <div className="page">
        <ErrorBox error={spaces.error} onRetry={spaces.reload} />
      </div>
    );
  return (
    <DataPage
      portability={services.portability}
      spaces={spaces.data.map(({ slug, name }) => ({ slug, name }))}
      owner={me.owner}
    />
  );
}
