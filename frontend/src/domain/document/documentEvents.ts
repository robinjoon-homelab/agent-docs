import type { Permission } from '../space/permission';
export type RevocationReason = 'not_found' | 'unauthenticated';
export type DocumentEventHandlers = {
  readonly permission?: (permission: Permission) => void;
  readonly revoked?: (reason: RevocationReason) => void;
};
