import type { Permission } from '../../../../../domain/space/permission';
import type { RevocationReason } from '../../../../../domain/document/documentEvents';
import { useCallback, useEffect, useRef } from 'react';
import type { DocumentEventsUseCases } from '../../../../../application/document/documentEventsUseCases';

export interface DocumentEventRefresh {
  /** Session-owned queued three-way merge, preserving pending input and conflict choices. */
  refreshDocument(): Promise<void>;
  /** useThreadState.reload owns mutation and request generation protection. */
  reloadThreads(): Promise<void>;
  readonly revoked?: boolean;
  onError(error: unknown): void;
  onPermission?(permission: Permission): Promise<void>;
  onRevoked?(reason: RevocationReason): void;
}

export function useDocumentEvents(
  documentId: string,
  events: DocumentEventsUseCases,
  refresh: DocumentEventRefresh,
): () => void {
  const cancel = useRef<() => void>(() => {});
  const latest = useRef(refresh);
  useEffect(() => {
    latest.current = refresh;
  }, [refresh]);
  const revoked = refresh.revoked === true;
  useEffect(() => {
    if (revoked) return;
    let active = true;
    let running = false;
    let pending = false;
    const drain = async () => {
      if (running) return;
      running = true;
      try {
        while (active && pending) {
          pending = false;
          const target = latest.current;
          await Promise.all([target.refreshDocument(), target.reloadThreads()]);
        }
      } catch (error) {
        if (active) latest.current.onError(error);
      } finally {
        running = false;
      }
    };
    const stop = events.watch(
      documentId,
      () => {
        if (!active) return;
        pending = true;
        void drain();
      },
      {
        permission: (permission) => {
          if (active)
            void latest.current.onPermission?.(permission).catch((error: unknown) => {
              if (active) latest.current.onError(error);
            });
        },
        revoked: (reason) => {
          active = false;
          pending = false;
          latest.current.onRevoked?.(reason);
        },
      },
    );
    const stopAll = () => {
      active = false;
      pending = false;
      stop();
    };
    cancel.current = stopAll;
    return stopAll;
  }, [documentId, events, revoked]);
  return useCallback(() => cancel.current(), []);
}
