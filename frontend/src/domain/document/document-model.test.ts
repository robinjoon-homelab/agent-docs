import { describe, expect, it } from 'vitest';
import { agent, human, paragraph, summary } from '../../test-support/fixtures';
import type { Block, TableBlock } from './block';
import { blocksEqual } from './block';
import { blockPlainText } from './blockText';
import { buildDiffRows } from './diffView';
import { isValidPath, movedPath, normalizePath, parseTags } from './document';
import { toDraftBlock, toSavableBlocks } from './draft';
import { textRun } from './inline';
import { listOrdinals } from './listOrdinals';
import { mergeAfterConflict } from './merge';
import { buildPathTree, isFolderOpen } from './pathTree';
import { recentAgentChanges } from './agentChanges';

const table: TableBlock = {
  id: 'tttttttttt',
  type: 'table',
  columns: [
    { id: 'c1', align: 'none' },
    { id: 'c2', align: 'right' },
  ],
  rows: [
    { id: 'r1', cells: ['**Name**', 'Count'] },
    { id: 'r2', cells: ['a\\*b', '2 * 3'] },
  ],
};

describe('blockPlainText', () => {
  it('uses the reader-visible text of each cell', () => {
    expect(blockPlainText(table)).toBe('Name\tCount\na*b\t2 * 3');
  });
});

describe('blocksEqual', () => {
  it('ignores the server hash and compares table cells', () => {
    expect(blocksEqual({ ...table, hash: 'abcd1234' } as Block, table)).toBe(true);
    expect(blocksEqual(table, { ...table, rows: [{ id: 'r1', cells: ['x', 'y'] }] })).toBe(false);
  });
});

describe('toSavableBlocks', () => {
  it('keeps stored markdown for untouched blocks and cells', () => {
    const base: Block[] = [paragraph('pppppppppp', 'a\\*b and 2 * 3'), table];
    const saved = toSavableBlocks(base.map(toDraftBlock), base);
    expect(saved).toEqual(base);
  });

  it('serializes edited text, trims it and drops empty paragraphs, headings, quotes and callouts', () => {
    const saved = toSavableBlocks(
      [
        {
          id: 'pppppppppp',
          type: 'paragraph',
          text: [textRun('  new '), textRun('bold', { bold: true }), textRun(' ')],
        },
        { id: 'eeeeeeeeee', type: 'paragraph', text: [textRun('   ')] },
        { id: 'hhhhhhhhhh', type: 'heading', level: 1, text: [] },
        { id: 'qqqqqqqqqq', type: 'quote', text: [textRun(' ')] },
        { id: 'cccccccccc', type: 'callout', kind: 'tip', text: [] },
        {
          id: 'llllllllll',
          type: 'listItem',
          style: 'bullet',
          depth: 9,
          checked: true,
          text: [textRun('item')],
        },
      ],
      [],
    );
    expect(saved).toEqual([
      { id: 'pppppppppp', type: 'paragraph', text: 'new **bold**' },
      { id: 'llllllllll', type: 'listItem', style: 'bullet', depth: 5, checked: false, text: 'item' },
    ]);
  });
});

describe('listOrdinals', () => {
  it('numbers consecutive items per depth and restarts after other blocks', () => {
    const blocks = [
      { id: 'a', type: 'listItem', style: 'numbered' as const, depth: 0 },
      { id: 'b', type: 'listItem', style: 'numbered' as const, depth: 1 },
      { id: 'c', type: 'listItem', style: 'numbered' as const, depth: 1 },
      { id: 'd', type: 'listItem', style: 'numbered' as const, depth: 0 },
      { id: 'e', type: 'listItem', style: 'numbered' as const, depth: 1 },
      { id: 'f', type: 'paragraph' },
      { id: 'g', type: 'listItem', style: 'numbered' as const, depth: 0 },
      { id: 'h', type: 'listItem', style: 'bullet' as const, depth: 0 },
      { id: 'i', type: 'listItem', style: 'numbered' as const, depth: 0 },
    ];
    expect(Object.fromEntries(listOrdinals(blocks))).toEqual({ a: 1, b: 1, c: 2, d: 2, e: 1, g: 1, i: 1 });
  });
});

describe('mergeAfterConflict', () => {
  const base = [paragraph('a', 'A'), paragraph('b', 'B'), paragraph('c', 'C')];

  it('takes each side that changed a block and the choice for conflicts', () => {
    const mine = [paragraph('a', 'A mine'), paragraph('b', 'B mine'), paragraph('c', 'C')];
    const theirs = [paragraph('a', 'A'), paragraph('b', 'B theirs'), paragraph('c', 'C theirs')];
    const merged = mergeAfterConflict(base, mine, theirs, new Map([['b', 'theirs' as const]]));
    expect(merged).toEqual([
      paragraph('a', 'A mine'),
      paragraph('b', 'B theirs'),
      paragraph('c', 'C theirs'),
    ]);
  });

  it('keeps their new blocks after the closest surviving neighbour and honours removals', () => {
    const mine = [paragraph('a', 'A'), paragraph('n', 'my new')];
    const theirs = [
      paragraph('a', 'A'),
      paragraph('x', 'their new'),
      paragraph('b', 'B'),
      paragraph('c', 'C'),
    ];
    const merged = mergeAfterConflict(base, mine, theirs, new Map());
    expect(merged.map((block) => block.id)).toEqual(['a', 'x', 'n']);
  });

  it('keeps their edit of a block I removed unless I chose my removal', () => {
    const mine = [paragraph('a', 'A'), paragraph('c', 'C')];
    const theirs = [paragraph('a', 'A'), paragraph('b', 'B edited'), paragraph('c', 'C')];
    expect(mergeAfterConflict(base, mine, theirs, new Map()).map((block) => block.id)).toEqual([
      'a',
      'b',
      'c',
    ]);
    expect(
      mergeAfterConflict(base, mine, theirs, new Map([['b', 'mine' as const]])).map((b) => b.id),
    ).toEqual(['a', 'c']);
  });
});

describe('buildDiffRows', () => {
  it('marks changes and shows removed blocks where they were', () => {
    const from = [paragraph('a', 'A'), paragraph('b', 'B'), paragraph('c', 'C')];
    const to = [paragraph('a', 'A2'), paragraph('c', 'C'), paragraph('d', 'D')];
    const rows = buildDiffRows(from, to, {
      from: 1,
      to: 2,
      unified: '',
      changes: [
        { blockId: 'a', change: 'modified', before: from[0] ?? null, after: to[0] ?? null, changedCells: [] },
        { blockId: 'b', change: 'removed', before: from[1] ?? null, after: null, changedCells: [] },
        { blockId: 'd', change: 'added', before: null, after: to[2] ?? null, changedCells: [] },
      ],
    });
    expect(rows.map((row) => [row.block.id, row.marker])).toEqual([
      ['a', 'modified'],
      ['b', 'removed'],
      ['c', null],
      ['d', 'added'],
    ]);
  });
});

describe('buildPathTree', () => {
  it('groups documents into folders by path segment, folders first', () => {
    const tree = buildPathTree([
      summary({ id: '1', path: 'readme' }),
      summary({ id: '2', path: 'guides/setup' }),
      summary({ id: '3', path: 'guides/deploy' }),
    ]);
    expect(tree.map((node) => node.name)).toEqual(['guides', 'readme']);
    expect(tree[0]?.children.map((node) => [node.name, node.document?.id])).toEqual([
      ['deploy', '3'],
      ['setup', '2'],
    ]);
  });
});

describe('recentAgentChanges', () => {
  it('labels recent agent changes and ignores humans and old changes', () => {
    const now = new Date('2026-10-18T10:00:00Z');
    const marks = recentAgentChanges(
      [
        { blockId: 'a', actor: agent('Claude'), revision: 15, at: '2026-10-18T09:58:00Z' },
        { blockId: 'b', actor: human(), revision: 16, at: '2026-10-18T09:59:00Z' },
        { blockId: 'c', actor: agent('Codex'), revision: 2, at: '2026-10-10T09:00:00Z' },
      ],
      now,
    );
    expect([...marks.values()]).toEqual([{ blockId: 'a', label: 'Claude · 2분 전 · rev 15' }]);
  });
});

describe('document inputs', () => {
  it('accepts 1..10 segments of letters, digits and . _ - only', () => {
    expect(isValidPath('설계/api_v2.1-draft')).toBe(true);
    expect(isValidPath(Array.from({ length: 10 }, () => 'a').join('/'))).toBe(true);
    expect(isValidPath(Array.from({ length: 11 }, () => 'a').join('/'))).toBe(false);
    expect(isValidPath('my doc')).toBe(false);
    expect(isValidPath('/guides')).toBe(false);
    expect(isValidPath('a'.repeat(81))).toBe(false);
    expect(isValidPath(Array.from({ length: 4 }, () => 'a'.repeat(80)).join('/'))).toBe(false);
  });

  it('moves a document into a folder or the space root keeping its own name', () => {
    expect(movedPath('guides/setup', 'design/api')).toBe('design/api/setup');
    expect(movedPath('guides/setup', '')).toBe('setup');
  });

  it('normalizes paths and tags', () => {
    expect(normalizePath(' /guides// setup / ')).toBe('guides/setup');
    expect(parseTags('a, b,,a , c')).toEqual(['a', 'b', 'c']);
  });
});

describe('isFolderOpen', () => {
  it('opens only the folders around the open document until someone toggles one', () => {
    const none = new Map<string, boolean>();

    expect(isFolderOpen('guides', 'guides/deploy/ci', none)).toBe(true);
    expect(isFolderOpen('guides/deploy', 'guides/deploy/ci', none)).toBe(true);
    expect(isFolderOpen('guides', 'guides', none)).toBe(true);
    expect(isFolderOpen('guide', 'guides/deploy/ci', none)).toBe(false);
    expect(isFolderOpen('design', 'guides/deploy/ci', none)).toBe(false);
    expect(isFolderOpen('design', null, none)).toBe(false);
  });

  it('follows the reader once they open or close a folder', () => {
    const toggled = new Map([
      ['guides', false],
      ['design', true],
    ]);

    expect(isFolderOpen('guides', 'guides/setup', toggled)).toBe(false);
    expect(isFolderOpen('design', 'guides/setup', toggled)).toBe(true);
  });
});
