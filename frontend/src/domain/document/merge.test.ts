import { describe, expect, it } from 'vitest';
import { paragraph } from '../../test-support/fixtures';
import { conflictingBlocks, mergeAfterConflict } from './merge';

describe('three-way block merge', () => {
  it('preserves independent edits and remote insertions after their predecessor', () => {
    const base = [paragraph('a', 'one'), paragraph('b', 'two')];
    const mine = [paragraph('a', 'mine'), paragraph('b', 'two')];
    const theirs = [paragraph('a', 'one'), paragraph('c', 'new'), paragraph('b', 'theirs')];
    expect(conflictingBlocks(base, mine, theirs)).toEqual([]);
    expect(mergeAfterConflict(base, mine, theirs, new Map())).toEqual([
      paragraph('a', 'mine'),
      paragraph('c', 'new'),
      paragraph('b', 'theirs'),
    ]);
  });

  it('requires a choice for edit/delete conflicts in both directions', () => {
    const base = [paragraph('a', 'old')];
    expect(conflictingBlocks(base, [], [paragraph('a', 'new')])).toEqual(['a']);
    expect(conflictingBlocks(base, [paragraph('a', 'new')], [])).toEqual(['a']);
    expect(mergeAfterConflict(base, [], [paragraph('a', 'new')], new Map([['a', 'mine']]))).toEqual([]);
    expect(mergeAfterConflict(base, [paragraph('a', 'new')], [], new Map([['a', 'theirs']]))).toEqual([]);
  });

  it('accepts identical concurrent edits', () => {
    expect(
      conflictingBlocks([paragraph('a', 'old')], [paragraph('a', 'same')], [paragraph('a', 'same')]),
    ).toEqual([]);
  });
});
