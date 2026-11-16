import type { Thread } from '../thread/thread';
export type Permission = 'read' | 'comment' | 'edit' | 'owner';
export function permissionOf(value: unknown): Permission | undefined {
  if (value === undefined || value === null) return undefined;
  return value === 'owner' || value === 'edit' || value === 'comment' ? value : 'read';
}
/** Only an absent field uses the legacy owner fallback; an explicit grant wins. */
export function effectivePermission(permission: Permission | undefined, owner: boolean): Permission {
  return permission ?? (owner ? 'owner' : 'read');
}
export function canEdit(permission: Permission | null | undefined): boolean {
  return permission === 'edit' || permission === 'owner';
}
export function canComment(permission: Permission | null | undefined): boolean {
  return canEdit(permission) || permission === 'comment';
}
export function canResolveThread(permission: Permission | null, thread: Thread, login: string): boolean {
  return (
    thread.status !== 'resolved' &&
    (canEdit(permission) ||
      (canComment(permission) && thread.author.kind === 'human' && thread.author.login === login))
  );
}

export function permissionInSpace(
  space: { readonly id: string; readonly permission?: Permission } | null,
  owner: boolean,
  current: ReadonlyMap<string, Permission>,
): Permission | null {
  return space === null ? null : (current.get(space.id) ?? effectivePermission(space.permission, owner));
}
