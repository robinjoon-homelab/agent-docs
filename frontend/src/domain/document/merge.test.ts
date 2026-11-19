import { describe, expect, it } from 'vitest';
import { paragraph } from '../../test-support/fixtures';
import { conflictingBlocks, hasOrderConflict, mergeAfterConflict } from './merge';

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

  it('preserves a remote move and a local insertion with its predecessor', () => {
    const a = paragraph('a', 'one');
    const b = paragraph('b', 'two');
    const c = paragraph('c', 'three');
    const added = paragraph('x', 'local');
    expect(mergeAfterConflict([a, b, c], [a, added, b, c], [c, a, b], new Map())).toEqual([c, a, added, b]);
  });

  it('combines compatible moves and asks for a choice when the combined order cycles', () => {
    const a = paragraph('a', 'a');
    const b = paragraph('b', 'b');
    const c = paragraph('c', 'c');
    const d = paragraph('d', 'd');
    const base = [a, b, c, d];
    const mine = [b, a, c, d];
    const compatible = [a, b, d, c];
    const incompatible = [a, c, b, d];
    expect(hasOrderConflict(base, mine, compatible)).toBe(false);
    expect(mergeAfterConflict(base, mine, compatible, new Map())).toEqual([b, a, d, c]);
    expect(hasOrderConflict(base, mine, incompatible)).toBe(true);
    expect(mergeAfterConflict(base, mine, incompatible, new Map(), 'theirs')).toEqual(incompatible);
  });

  it('accepts identical concurrent edits', () => {
    expect(
      conflictingBlocks([paragraph('a', 'old')], [paragraph('a', 'same')], [paragraph('a', 'same')]),
    ).toEqual([]);
  });
});
