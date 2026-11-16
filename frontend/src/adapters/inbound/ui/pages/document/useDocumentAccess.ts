import type { Space } from '../../../../../domain/space/space';
import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import type { AgentDocument } from '../../../../../domain/document/document';
import type { RevocationReason } from '../../../../../domain/document/documentEvents';
import { canEdit, effectivePermission, type Permission } from '../../../../../domain/space/permission';
import { isApiError, isNotVisible } from '../../../../../application/shared/ports/errors';

type AccessSession = {
  setWritable(writable: boolean): void;
  refreshRemote(): Promise<void>;
  getState(): { readonly status: string };
};
type AccessState = {
  permission: Permission | null;
  revoked: RevocationReason | null;
  generation: number;
  basisReady: boolean;
  needsRecovery: boolean;
  spaceId: string;
};
/** Keep the mounted editor and its draft while synchronously closing every write path. */
export function useDocumentAccess(
  document: AgentDocument,
  owner: boolean,
  session: AccessSession,
  get: () => Promise<AgentDocument>,
  options?: {
    readonly revoked?: () => void;
    readonly onError?: (failure: unknown) => void;
    readonly spaces?: {
      readonly get: (slug: string) => Promise<Space>;
      readonly update: (space: Space) => void;
    };
  },
) {
  const initial = effectivePermission(document.permission, owner);
  const [grant, setGrant] = useState<Permission | null>(initial);
  const [verified, setVerified] = useState(true);
  const [writable, setWritable] = useState(canEdit(initial) && document.trashedAt === null);
  const [revoked, setRevoked] = useState<RevocationReason | null>(null);
  const [resuming, setResuming] = useState(false);
  const [recoveryError, setRecoveryError] = useState<unknown>(null);
  const current = useRef<AccessState>({
    permission: initial,
    revoked: null,
    generation: 0,
    basisReady: canEdit(initial) && document.trashedAt === null,
    needsRecovery: false,
    spaceId: document.space.id,
  });
  const alive = useRef(true);
  useLayoutEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      current.current.generation += 1;
    };
  }, []);
  useLayoutEffect(() => {
    session.setWritable(
      canEdit(current.current.permission) && document.trashedAt === null && !current.current.needsRecovery,
    );
  }, [session, document.trashedAt]);
  const pause = () => {
    current.current.basisReady = false;
    current.current.needsRecovery = true;
    session.setWritable(false);
    setVerified(false);
    setWritable(false);
  };
  const revoke = (reason: RevocationReason) => {
    if (!alive.current) return;
    current.current = {
      ...current.current,
      permission: null,
      revoked: reason,
      generation: current.current.generation + 1,
    };
    pause();
    options?.revoked?.();
    setGrant(null);
    setRevoked(reason);
    setResuming(false);
    setRecoveryError(null);
  };
  const error = (failure: unknown): boolean => {
    if (!alive.current) return true;
    if (isApiError(failure, 'unauthenticated')) {
      revoke('unauthenticated');
      return true;
    }
    if (isNotVisible(failure)) {
      revoke('not_found');
      return true;
    }
    return false;
  };
  const refreshSpace = async (latest: AgentDocument, generation: number): Promise<void> => {
    const spaces = options?.spaces;
    if (spaces === undefined) return;
    const space = await spaces.get(latest.space.slug);
    if (generation !== current.current.generation) return;
    if (space.id !== latest.space.id)
      throw new Error('스페이스가 바뀌어 권한을 확인하지 못했습니다. 다시 시도하세요.');
    spaces.update(space);
  };
  const recover = async (): Promise<void> => {
    if (!alive.current || current.current.revoked !== null) return;
    const generation = ++current.current.generation;
    pause();
    setResuming(true);
    setRecoveryError(null);
    let stage: 'document' | 'space' | 'merge' = 'document';
    try {
      const latest = await get();
      if (generation !== current.current.generation) return;
      const actual = effectivePermission(latest.permission, owner);
      if (actual !== current.current.permission) {
        pause();
        setResuming(true);
      }
      current.current.permission = actual;
      setGrant(actual);
      stage = 'space';
      await refreshSpace(latest, generation);
      if (generation !== current.current.generation) return;
      stage = 'merge';
      await session.refreshRemote();
      if (generation !== current.current.generation) return;
      current.current.spaceId = latest.space.id;
      current.current.needsRecovery = false;
      current.current.basisReady = canEdit(actual) && latest.trashedAt === null;
      const open = current.current.basisReady && session.getState().status !== 'conflict';
      session.setWritable(open);
      setVerified(true);
      setWritable(open);
    } catch (failure) {
      if (generation !== current.current.generation) return;
      // A space lookup failure cannot establish that this document was revoked or moved.
      if ((stage !== 'space' || isApiError(failure, 'unauthenticated')) && error(failure)) return;
      pause();
      setRecoveryError(failure);
    } finally {
      if (generation === current.current.generation) setResuming(false);
    }
  };
  const receivePermission = async (permission: Permission): Promise<void> => {
    if (!alive.current || current.current.revoked !== null) return;
    current.current.permission = permission;
    setGrant(permission);
    await recover();
  };
  const refresh = async (): Promise<void> => {
    if (!alive.current || current.current.revoked !== null) return;
    if (current.current.needsRecovery) return recover();
    const generation = ++current.current.generation;
    try {
      const latest = await get();
      if (generation !== current.current.generation) return;
      const actual = effectivePermission(latest.permission, owner);
      if (actual !== current.current.permission) {
        await receivePermission(actual);
        return;
      }
      // Navigation grants are independent of the editor's ordinary remote merge.
      void refreshSpace(latest, generation).catch((failure: unknown) => {
        if (generation !== current.current.generation) return;
        if (isApiError(failure, 'unauthenticated')) error(failure);
        else options?.onError?.(failure);
      });
      await session.refreshRemote();
      if (generation !== current.current.generation) return;
      current.current.spaceId = latest.space.id;
    } catch (failure) {
      if (generation !== current.current.generation) return;
      if (!error(failure)) options?.onError?.(failure);
    }
  };
  const beginConflictResolution = (): boolean => {
    if (
      !alive.current ||
      current.current.revoked !== null ||
      current.current.needsRecovery ||
      !current.current.basisReady ||
      !canEdit(current.current.permission)
    )
      return false;
    // A verified grant permits the explicit resolution and its retry, even if the save itself fails.
    session.setWritable(true);
    setWritable(true);
    return true;
  };
  const currentAccess = useCallback(() => current.current, []);
  // A grant observed on the stream is not a verified editing baseline; comments remain available while recovering.
  const permission = canEdit(grant) && !verified ? 'comment' : grant;
  return {
    permission,
    verified,
    writable,
    revoked,
    resuming,
    recoveryError,
    currentAccess,
    revoke,
    error,
    receivePermission,
    beginConflictResolution,
    refresh,
  };
}
