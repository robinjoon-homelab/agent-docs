import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { loginHref } from '../../../../domain/session/me';
import { useServices } from '../../../../config/ServicesContext';
import { ErrorBox, Loading } from '../common/States';
import { useLoad } from '../hooks/useLoad';
import { takeNext } from './nextPath';
import { SessionProvider } from './SessionContext';

/** Gate for every signed-in route: no session means the login page, with the current URL as `next`. */
export function RequireSession() {
  const services = useServices();
  const location = useLocation();
  const current = useLoad('me', () => services.session.currentUser());
  const [inboxVersion, setInboxVersion] = useState(0);
  const refreshInbox = useCallback(() => setInboxVersion((value) => value + 1), []);
  const [next] = useState(() => takeNext());
  const navigate = useNavigate();
  const redirected = useRef(false);
  const me = current.state === 'ready' ? current.data : null;
  const session = useMemo(
    () => (me === null ? null : { me, inboxVersion, refreshInbox }),
    [me, inboxVersion, refreshInbox],
  );

  useEffect(() => {
    if (session === null || next === null || redirected.current) return;
    redirected.current = true;
    void navigate(next, { replace: true });
  }, [session, next, navigate]);

  if (current.state === 'loading') return <Loading />;
  if (current.state === 'error') {
    return (
      <div className="page">
        <ErrorBox error={current.error} onRetry={current.reload} />
      </div>
    );
  }
  if (session === null) {
    return <Navigate to={loginHref(`${location.pathname}${location.search}${location.hash}`)} replace />;
  }
  return (
    <SessionProvider session={session}>
      <Outlet />
    </SessionProvider>
  );
}
