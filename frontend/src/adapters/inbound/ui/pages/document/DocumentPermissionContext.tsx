import { createContext, useContext } from 'react';
import type { Permission } from '../../../../../domain/space/permission';
import { effectivePermission } from '../../../../../domain/space/permission';
import { useSession } from '../../app/SessionContext';
export const DocumentPermissionContext = createContext<Permission | null | undefined>(undefined);
export function useDocumentPermission(): Permission | null {
  const permission = useContext(DocumentPermissionContext);
  const { me } = useSession();
  return permission === null ? null : effectivePermission(permission, me.owner);
}
