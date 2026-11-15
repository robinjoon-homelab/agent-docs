import { useEffect, useRef } from 'react';
import type { DocumentEventsUseCases } from '../../../../../application/document/documentEventsUseCases';

export interface DocumentEventRefresh {
  /** Session-owned queued three-way merge, preserving pending input and conflict choices. */
  refreshDocument(): Promise<void>;
  /** useThreadState.reload owns mutation and request generation protection. */
  reloadThreads(): Promise<void>;
  onError(error: unknown): void;
}

export function useDocumentEvents(
  documentId: string,
  events: DocumentEventsUseCases,
  refresh: DocumentEventRefresh,
): void {
  const latest = useRef(refresh);
  useEffect(() => {
    latest.current = refresh;
  }, [refresh]);
  useEffect(() => {
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
    const stop = events.watch(documentId, () => {
      pending = true;
      void drain();
    });
    return () => {
      active = false;
      stop();
    };
  }, [documentId, events]);
}
