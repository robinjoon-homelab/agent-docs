import { describe, expect, it } from 'vitest';
import { human } from '../../test-support/fixtures';
import type { Revision } from './document';
import { defaultRevision, orderedPair, revisionPeriod, togglePair } from './history';

function revision(number: number): Revision {
  return {
    revision: number,
    actor: human(),
    source: 'web',
    summary: '',
    changedBlockIds: [],
    createdAt: '2026-10-18T00:00:00Z',
    threadIds: [],
  };
}

describe('revisionPeriod', () => {
  it('covers whole local days and leaves open ends out', () => {
    const day = revisionPeriod('2026-10-18', '2026-10-18');
    expect(new Date(day.to ?? '').getTime() - new Date(day.from ?? '').getTime()).toBe(
      24 * 60 * 60 * 1000 - 1,
    );
    expect(revisionPeriod('', '')).toEqual({});
    expect(Object.keys(revisionPeriod('2026-10-01', ''))).toEqual(['from']);
  });
});

describe('defaultRevision', () => {
  const revisions = [revision(15), revision(14), revision(12)];

  it('uses the requested revision when it is listed', () => {
    expect(defaultRevision(revisions, 15, 12)).toBe(12);
  });

  it('otherwise picks the newest revision older than the current one', () => {
    expect(defaultRevision(revisions, 15, null)).toBe(14);
    expect(defaultRevision(revisions, 15, 99)).toBe(14);
  });

  it('falls back to the first listed revision or nothing', () => {
    expect(defaultRevision([revision(15)], 15, null)).toBe(15);
    expect(defaultRevision([], 15, null)).toBeNull();
  });
});

describe('togglePair', () => {
  it('unpicks a picked revision and keeps only the last two picks', () => {
    expect(togglePair([3, 5], 5)).toEqual([3]);
    expect(togglePair([3, 5], 7)).toEqual([5, 7]);
    expect(orderedPair(togglePair([9], 4))).toEqual({ from: 4, to: 9 });
    expect(orderedPair([9])).toBeNull();
  });
});
