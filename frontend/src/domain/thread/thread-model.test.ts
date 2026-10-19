import { describe, expect, it } from 'vitest';
import { paragraph, thread } from '../../test-support/fixtures';
import { textAnchor } from './anchor';
import { commentParts } from './commentBody';
import { layoutRail } from './railLayout';
import { estimateRestore } from './restoreImpact';

describe('textAnchor', () => {
  it('orders offsets and quotes the selected plain text', () => {
    expect(textAnchor('aaaaaaaaaa', 'hello world', 11, 6)).toEqual({
      kind: 'text',
      blockId: 'aaaaaaaaaa',
      start: 6,
      end: 11,
      quote: 'world',
    });
  });

  it('rejects blank and out-of-range selections', () => {
    expect(textAnchor('aaaaaaaaaa', 'a  b', 1, 3)).toBe('empty');
    expect(textAnchor('aaaaaaaaaa', 'abc', 0, 4)).toBe('out_of_range');
  });
});

describe('commentParts', () => {
  it('separates fenced code from inline lines and closes an open fence at the end', () => {
    expect(commentParts('see:\n```kotlin\nval a = 1\n```\ndone\n~~~\nopen')).toEqual([
      { kind: 'line', text: 'see:' },
      { kind: 'code', language: 'kotlin', code: 'val a = 1' },
      { kind: 'line', text: 'done' },
      { kind: 'code', language: '', code: 'open' },
    ]);
  });
});

describe('layoutRail', () => {
  it('aligns cards to anchors, pushes overlapping ones down and puts floating cards last', () => {
    const placements = layoutRail(
      [
        { id: 'doc', anchorTop: null, height: 50 },
        { id: 'b', anchorTop: 120, height: 100 },
        { id: 'a', anchorTop: 100, height: 40 },
      ],
      10,
    );
    expect(placements).toEqual([
      { id: 'a', top: 100 },
      { id: 'b', top: 150 },
      { id: 'doc', top: 260 },
    ]);
  });
});

describe('estimateRestore', () => {
  it('counts reverted blocks and open threads whose anchor would disappear', () => {
    const impact = estimateRestore(
      {
        from: 2,
        to: 5,
        unified: '',
        changes: [
          { blockId: 'aaaaaaaaaa', change: 'modified', before: null, after: null, changedCells: [] },
          { blockId: 'bbbbbbbbbb', change: 'added', before: null, after: null, changedCells: [] },
        ],
      },
      [paragraph('aaaaaaaaaa', 'hello there')],
      [
        thread({ id: 'kept' }),
        thread({
          id: 'gone',
          anchor: { kind: 'text', blockId: 'bbbbbbbbbb', start: 0, end: 3, quote: 'new' },
        }),
        thread({
          id: 'quote-missing',
          anchor: { kind: 'text', blockId: 'aaaaaaaaaa', start: 0, end: 4, quote: 'bye' },
        }),
        thread({ id: 'resolved', status: 'resolved', anchor: { kind: 'block', blockId: 'bbbbbbbbbb' } }),
      ],
    );
    expect(impact).toEqual({ revertedBlocks: 2, detachedThreads: 2 });
  });
});
