import type { Revision, RevisionSource } from './document';

export const REVISION_SOURCE_LABEL: Record<RevisionSource, string> = {
  web: '웹',
  mcp: 'MCP',
  rest: 'REST',
  system: '자동',
};

export type RevisionActorFilter = 'all' | 'human' | 'agent';

export const REVISION_FILTER_LABEL: Record<RevisionActorFilter, string> = {
  all: '전체',
  human: '사람',
  agent: '에이전트',
};

/**
 * Revision to compare with the current document when the history opens: the one asked for in the URL
 * if it is listed, otherwise the newest revision older than the current one (comparing the current
 * revision with itself shows nothing).
 */
export function defaultRevision(
  revisions: readonly Revision[],
  currentRevision: number,
  requested: number | null,
): number | null {
  if (requested !== null && revisions.some((revision) => revision.revision === requested)) return requested;
  const older = revisions.filter((revision) => revision.revision < currentRevision);
  const newest = older.reduce<number | null>(
    (best, revision) => (best === null || revision.revision > best ? revision.revision : best),
    null,
  );
  return newest ?? revisions[0]?.revision ?? null;
}

/** Two-revision picking: clicking a picked revision unpicks it, a third pick drops the oldest pick. */
export function togglePair(pair: readonly number[], revision: number): number[] {
  if (pair.includes(revision)) return pair.filter((picked) => picked !== revision);
  return [...pair, revision].slice(-2);
}

/** Older revision first, so the diff reads "from → to". */
export function orderedPair(pair: readonly number[]): { readonly from: number; readonly to: number } | null {
  const [first, second] = pair;
  if (first === undefined || second === undefined) return null;
  return { from: Math.min(first, second), to: Math.max(first, second) };
}

export type RevisionPeriod = { readonly from?: string; readonly to?: string };

/**
 * Period filter from two local calendar dates ("YYYY-MM-DD", either may be empty) to ISO-8601 instants:
 * from the start of the first day to the end of the last day, in the viewer's time zone.
 */
export function revisionPeriod(fromDate: string, toDate: string): RevisionPeriod {
  const from = fromDate === '' ? undefined : new Date(`${fromDate}T00:00:00`).toISOString();
  const to = toDate === '' ? undefined : new Date(`${toDate}T23:59:59.999`).toISOString();
  return { ...(from === undefined ? {} : { from }), ...(to === undefined ? {} : { to }) };
}
