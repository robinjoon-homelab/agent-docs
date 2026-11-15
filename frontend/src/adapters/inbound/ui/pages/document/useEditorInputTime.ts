import { useEffect, useRef } from 'react';
import type { DocumentEditorSession, InputKind } from '../../../../../application/document/editorSession';
import { InputTimeTracker } from '../../../../../application/statistics/inputTimeTracker';
import type { StatisticsUseCases } from '../../../../../application/statistics/statisticsUseCases';
import { mergeInputIntervals } from '../../../../../domain/statistics/statistics';

function segment() {
  const tabId = crypto.randomUUID();
  const make = () => new InputTimeTracker(tabId, () => crypto.randomUUID());
  return {
    tabId,
    closed: false,
    trackers: { body: make(), title: make(), path: make(), status: make(), tags: make(), assignee: make() },
  };
}

export function useEditorInputTime(
  session: DocumentEditorSession,
  documentId: string,
  statistics: StatisticsUseCases,
) {
  const retained = useRef<ReturnType<typeof segment>[]>([]);
  const current = useRef<ReturnType<typeof segment> | null>(null);
  const active = () => {
    if (current.current === null || current.current.closed) {
      current.current = segment();
      retained.current.push(current.current);
    }
    return current.current;
  };
  const mark = (kind: InputKind) => active().trackers[kind].input(new Date());
  useEffect(() => {
    const closeOwner = (owner: ReturnType<typeof segment>) => {
      owner.closed = true;
      const watermark = Object.values(owner.trackers)
        .flatMap((tracker) => tracker.closeWatermark() ?? [])
        .sort()
        .at(-1);
      if (watermark !== undefined) void statistics.close(documentId, owner.tabId, watermark);
    };
    const prune = () => {
      retained.current = retained.current.filter(
        (owner) =>
          !owner.closed || Object.values(owner.trackers).some((tracker) => tracker.hasPendingInput()),
      );
    };
    const close = () => {
      for (const owner of retained.current) closeOwner(owner);
      prune();
    };
    session.setInputBatches((kinds) => {
      const groups = retained.current.flatMap((owner) => {
        const snapshots = kinds.flatMap((kind) => {
          const tracker = owner.trackers[kind];
          const trace = tracker.snapshot();
          return trace === null ? [] : [{ tracker, trace }];
        });
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
      return {
        trace: { batchId: crypto.randomUUID(), traces: groups.map((group) => group.trace) },
        unchanged: () => {
          for (const group of groups)
            for (const snapshot of group.snapshots) snapshot.tracker.unchanged(snapshot.trace);
          prune();
        },
        accepted: () => {
          for (const group of groups) {
            for (const snapshot of group.snapshots) snapshot.tracker.accepted(snapshot.trace);
            if (group.owner.closed) closeOwner(group.owner);
          }
          prune();
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
  return { mark, discard };
}
