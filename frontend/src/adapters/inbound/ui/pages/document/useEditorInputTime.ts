import { useEffect, useRef, useState } from 'react';
import type {
  DocumentEditorSession,
  DrawingInputRecorder,
  InputKind,
  InputReservation,
} from '../../../../../application/document/editorSession';
import { InputTimeTracker } from '../../../../../application/statistics/inputTimeTracker';
import type { StatisticsUseCases } from '../../../../../application/statistics/statisticsUseCases';
import type { InputTrace } from '../../../../../domain/statistics/statistics';
import { mergeInputIntervals } from '../../../../../domain/statistics/statistics';

function segment() {
  const tabId = crypto.randomUUID();
  const make = () => new InputTimeTracker(tabId, () => crypto.randomUUID());
  return {
    tabId,
    closed: false,
    make,
    trackers: { body: make(), title: make(), path: make(), status: make(), tags: make(), assignee: make() },
    /** One tracker per open drawing dialog, so cancelling one drawing never touches another's input. */
    drawings: new Map<string, InputTimeTracker>(),
  };
}

type Owner = ReturnType<typeof segment>;
type Frozen = { readonly owner: Owner; readonly tracker: InputTimeTracker; readonly trace: InputTrace };

function trackersOf(owner: Owner): InputTimeTracker[] {
  return [...Object.values(owner.trackers), ...owner.drawings.values()];
}

export function useEditorInputTime(
  session: DocumentEditorSession,
  documentId: string,
  statistics: StatisticsUseCases,
) {
  const retained = useRef<Owner[]>([]);
  const current = useRef<Owner | null>(null);
  const reservations = useRef(new Map<string, readonly Frozen[]>());
  const active = (): Owner => {
    if (current.current === null || current.current.closed) {
      current.current = segment();
      retained.current.push(current.current);
    }
    return current.current;
  };
  const mark = (kind: InputKind, at: Date = new Date()) => active().trackers[kind].input(at);
  // Built once: the page passes the same recorder to the editor on every render.
  const [drawing] = useState((): DrawingInputRecorder => ({
    mark: (drawingKey, at) => {
      const owner = active();
      const tracker = owner.drawings.get(drawingKey) ?? owner.make();
      owner.drawings.set(drawingKey, tracker);
      tracker.input(at);
    },
    reserve: (drawingKey): InputReservation | null => {
      const frozen = retained.current.flatMap((owner): Frozen[] => {
        const tracker = owner.drawings.get(drawingKey);
        const trace = tracker?.snapshot() ?? null;
        return tracker === undefined || trace === null ? [] : [{ owner, tracker, trace }];
      });
      if (frozen.length === 0) return null;
      const id = crypto.randomUUID();
      reservations.current.set(id, frozen);
      return { id };
    },
    discard: (drawingKey) => {
      const dropped = retained.current.flatMap((owner) => {
        const tracker = owner.drawings.get(drawingKey);
        if (tracker === undefined) return [];
        const trace = tracker.snapshot();
        if (trace !== null) tracker.unchanged(trace);
        owner.drawings.delete(drawingKey);
        return [tracker];
      });
      for (const [id, frozen] of reservations.current) {
        if (frozen.some((entry) => dropped.includes(entry.tracker))) reservations.current.delete(id);
      }
    },
  }));
  useEffect(() => {
    const closeOwner = (owner: Owner) => {
      owner.closed = true;
      const watermark = trackersOf(owner)
        .flatMap((tracker) => tracker.closeWatermark() ?? [])
        .sort()
        .at(-1);
      if (watermark !== undefined) void statistics.close(documentId, owner.tabId, watermark);
    };
    const prune = () => {
      retained.current = retained.current.filter(
        (owner) => !owner.closed || trackersOf(owner).some((tracker) => tracker.hasPendingInput()),
      );
    };
    const close = () => {
      for (const owner of retained.current) closeOwner(owner);
      prune();
    };
    session.setInputBatches((kinds, held) => {
      // A reservation is used exactly as frozen; drawing input after it stays for the next drawing save.
      const frozen = held.flatMap((reservation) => reservations.current.get(reservation.id) ?? []);
      const groups = retained.current.flatMap((owner) => {
        const fresh = kinds.flatMap((kind) => {
          const tracker = owner.trackers[kind];
          const trace = tracker.snapshot();
          return trace === null ? [] : [{ tracker, trace }];
        });
        const snapshots = [...fresh, ...frozen.filter((entry) => entry.owner === owner)];
        if (snapshots.length === 0) return [];
        const intervals = mergeInputIntervals(snapshots.flatMap(({ trace }) => trace.intervals));
        const trace = {
          tabId: owner.tabId,
          batchId: crypto.randomUUID(),
          intervals,
          firstInputAt: intervals[0]?.firstInputAt ?? '',
          lastInputAt: intervals.at(-1)?.lastInputAt ?? '',
        };
        return [{ owner, snapshots, trace }];
      });
      if (groups.length === 0) return null;
      const release = () => {
        for (const reservation of held) reservations.current.delete(reservation.id);
        prune();
      };
      return {
        trace: { batchId: crypto.randomUUID(), traces: groups.map((group) => group.trace) },
        unchanged: () => {
          for (const group of groups)
            for (const snapshot of group.snapshots) snapshot.tracker.unchanged(snapshot.trace);
          release();
        },
        accepted: () => {
          for (const group of groups) {
            for (const snapshot of group.snapshots) snapshot.tracker.accepted(snapshot.trace);
            if (group.owner.closed) closeOwner(group.owner);
          }
          release();
        },
      };
    });
    const hidden = () => {
      if (document.visibilityState === 'hidden') close();
    };
    document.addEventListener('visibilitychange', hidden);
    window.addEventListener('pagehide', close);
    return () => {
      document.removeEventListener('visibilitychange', hidden);
      window.removeEventListener('pagehide', close);
      close();
    };
  }, [session, documentId, statistics]);
  const discard = (kind: InputKind) => {
    for (const owner of retained.current) {
      const tracker = owner.trackers[kind];
      const trace = tracker.snapshot();
      if (trace !== null) tracker.unchanged(trace);
    }
  };
  return { mark, discard, drawing };
}
