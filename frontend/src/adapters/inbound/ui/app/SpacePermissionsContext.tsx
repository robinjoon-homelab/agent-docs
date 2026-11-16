import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';
import type { Permission } from '../../../../domain/space/permission';
import type { Space } from '../../../../domain/space/space';
type SpacePermissions = {
  readonly grants: ReadonlyMap<string, Permission>;
  readonly update: (space: Space) => void;
};
const SpacePermissionsContext = createContext<SpacePermissions>({ grants: new Map(), update: () => {} });
/** Only authoritative space responses update navigation grants; document revocation is local to that document. */
export function SpacePermissionsProvider({ children }: { children: ReactNode }) {
  const [grants, setGrants] = useState<ReadonlyMap<string, Permission>>(new Map());
  const update = useCallback((space: Space) => {
    setGrants((previous) => {
      const next = new Map(previous);
      if (space.permission === undefined) next.delete(space.id);
      else next.set(space.id, space.permission);
      return next;
    });
  }, []);
  return <SpacePermissionsContext value={{ grants, update }}>{children}</SpacePermissionsContext>;
}
export function useSpacePermissions(): SpacePermissions {
  return useContext(SpacePermissionsContext);
}
