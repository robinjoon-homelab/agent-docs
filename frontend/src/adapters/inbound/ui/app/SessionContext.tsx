import type { ReactNode } from 'react';
import { createContext, useContext } from 'react';
import type { Me } from '../../../../domain/session/me';

export type Session = {
  readonly me: Me;
  /** Increments when something changed the unread inbox count, so the sidebar refreshes it. */
  readonly inboxVersion: number;
  readonly refreshInbox: () => void;
};

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ session, children }: { session: Session; children: ReactNode }) {
  return <SessionContext value={session}>{children}</SessionContext>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (session === null) throw new Error('useSession must be used inside an authenticated route');
  return session;
}
