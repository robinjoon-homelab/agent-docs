export type SpaceWeekUsage = {
  readonly slug: string;
  readonly name: string;
  readonly documents: number;
  readonly comments: number;
};

export type WeekUsage = { readonly weekStart: string; readonly spaces: readonly SpaceWeekUsage[] };

export type Usage = {
  readonly weeks: readonly WeekUsage[];
  readonly agentResponseMinutesMedian: number | null;
  readonly revisitedDocuments: number;
};

export type WeekTotal = { readonly weekStart: string; readonly documents: number; readonly comments: number };

export function weeklyTotals(usage: Usage): WeekTotal[] {
  return [...usage.weeks]
    .sort((a, b) => a.weekStart.localeCompare(b.weekStart))
    .map((week) => ({
      weekStart: week.weekStart,
      documents: week.spaces.reduce((sum, space) => sum + space.documents, 0),
      comments: week.spaces.reduce((sum, space) => sum + space.comments, 0),
    }));
}

export function formatMinutes(minutes: number | null): string {
  if (minutes === null) return '—';
  if (minutes < 60) return `${Math.round(minutes)}분`;
  return `${(minutes / 60).toFixed(1)}시간`;
}

export type WeekBar = WeekTotal & { readonly documentsRatio: number; readonly commentsRatio: number };

/** Bar lengths (0..1) relative to the busiest value across all weeks; an empty range gives empty bars. */
export function weekBars(totals: readonly WeekTotal[]): WeekBar[] {
  const max = Math.max(0, ...totals.flatMap((total) => [total.documents, total.comments]));
  return totals.map((total) => ({
    ...total,
    documentsRatio: max === 0 ? 0 : total.documents / max,
    commentsRatio: max === 0 ? 0 : total.comments / max,
  }));
}
