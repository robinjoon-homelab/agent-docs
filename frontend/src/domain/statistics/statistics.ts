export type EditMeasure = { readonly milliseconds: number; readonly count: number };
export type EditStatistics = {
  readonly human: EditMeasure;
  readonly agentExplicit: EditMeasure;
  readonly agentEstimated: EditMeasure;
  readonly openCount: number;
};
export const HUMAN_INPUT_IDLE_MS = 120_000;
export type InputInterval = { readonly firstInputAt: string; readonly lastInputAt: string };

/** Join continuous input across fields while preserving gaps of at least 120 seconds. */
export function mergeInputIntervals(intervals: readonly InputInterval[]): readonly InputInterval[] {
  const merged: InputInterval[] = [];
  const sorted = [...intervals].sort((a, b) => Date.parse(a.firstInputAt) - Date.parse(b.firstInputAt));
  for (const next of sorted) {
    const previous = merged.at(-1);
    if (
      previous !== undefined &&
      Date.parse(next.firstInputAt) - Date.parse(previous.lastInputAt) < HUMAN_INPUT_IDLE_MS
    ) {
      merged[merged.length - 1] = {
        firstInputAt: previous.firstInputAt,
        lastInputAt:
          Date.parse(next.lastInputAt) > Date.parse(previous.lastInputAt)
            ? next.lastInputAt
            : previous.lastInputAt,
      };
    } else {
      merged.push({ ...next });
    }
  }
  return merged;
}

export type InputTrace = {
  readonly tabId: string;
  readonly batchId: string;
  readonly intervals: readonly InputInterval[];
  readonly firstInputAt: string;
  readonly lastInputAt: string;
};
export function emptyEditStatistics(): EditStatistics {
  return {
    human: { milliseconds: 0, count: 0 },
    agentExplicit: { milliseconds: 0, count: 0 },
    agentEstimated: { milliseconds: 0, count: 0 },
    openCount: 0,
  };
}

/** One stable document-write batch can contain pending input from several foreground scopes. */
export type InputTraceBatch = { readonly batchId: string; readonly traces: readonly InputTrace[] };
export type InputTracePayload = InputTrace | InputTraceBatch;
