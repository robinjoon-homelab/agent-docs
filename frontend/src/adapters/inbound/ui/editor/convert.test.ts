import { getSchema } from '@tiptap/core';
import { Node as PMNode } from '@tiptap/pm/model';
import { EditorState, TextSelection } from '@tiptap/pm/state';
import { describe, expect, it } from 'vitest';
import type { Block } from '../../../../domain/document/block';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { blocksToDoc, docToDrafts } from './convert';
import { anchorForSelection, findBlock, offsetToPos, posToOffset } from './positions';
import { blockIdPlugin, clearDuplicateIds } from './plugins/blockIds';
import { schemaExtensions } from './schema';

const schema = getSchema(schemaExtensions);

const blocks: Block[] = [
  { id: 'h000000001', type: 'heading', level: 2, text: '**Bold** title' },
  { id: 'p000000001', type: 'paragraph', text: 'a [link](https://x.dev) and [doc](doc:d1)<br>2 * 3 `code`' },
  { id: 'l000000001', type: 'listItem', style: 'task', depth: 2, checked: true, text: 'done' },
  { id: 'q000000001', type: 'quote', text: 'line<br>line' },
  { id: 'c000000001', type: 'callout', kind: 'warning', text: '*careful*' },
  { id: 'k000000001', type: 'code', language: 'mermaid', code: 'graph TD\n  A-->B' },
  {
    id: 't000000001',
    type: 'table',
    columns: [
      { id: 'col1', align: 'none' },
      { id: 'col2', align: 'center' },
    ],
    rows: [
      { id: 'row1', cells: ['**H1**', 'H2'] },
      { id: 'row2', cells: ['a<br>b', ''] },
    ],
  },
  { id: 'i000000001', type: 'image', assetId: 'asset-1', alt: 'diagram', caption: 'cap' },
  { id: 'd000000001', type: 'divider' },
];

let counter = 0;
const newId = (): string => `n${String(counter++).padStart(9, '0')}`;

function docOf(content: readonly Block[]): PMNode {
  return PMNode.fromJSON(schema, blocksToDoc(content, newId));
}

describe('block conversion', () => {
  it('round-trips every block type through the editor document unchanged', () => {
    const doc = docOf(blocks);
    const drafts = docToDrafts(doc.toJSON() as Parameters<typeof docToDrafts>[0], newId);
    expect(toSavableBlocks(drafts, blocks)).toEqual(blocks);
  });

  it('gives an empty document one paragraph that is dropped on save', () => {
    const doc = docOf([]);
    expect(doc.childCount).toBe(1);
    const drafts = docToDrafts(doc.toJSON() as Parameters<typeof docToDrafts>[0], newId);
    expect(toSavableBlocks(drafts, [])).toEqual([]);
  });
});

describe('plain-text offsets', () => {
  it('maps offsets across marks and hard breaks both ways', () => {
    const doc = docOf(blocks);
    const block = findBlock(doc, 'p000000001');
    if (block === null) throw new Error('missing block');
    // Plain text: "a link and doc\n2 * 3 code"
    const breakOffset = 'a link and doc\n'.length;
    const pos = offsetToPos(block.node, block.pos, breakOffset);
    expect(posToOffset(block.node, block.pos, pos)).toBe(breakOffset);
    expect(doc.textBetween(pos, pos + 5)).toBe('2 * 3');
  });

  it('builds a text anchor from a selection and a cell anchor inside tables', () => {
    const doc = docOf(blocks);
    const paragraph = findBlock(doc, 'p000000001');
    const table = findBlock(doc, 't000000001');
    if (paragraph === null || table === null) throw new Error('missing block');
    const from = offsetToPos(paragraph.node, paragraph.pos, 2);
    const to = offsetToPos(paragraph.node, paragraph.pos, 6);
    const textState = EditorState.create({ doc, selection: TextSelection.create(doc, from, to) });
    expect(anchorForSelection(textState)).toEqual({
      kind: 'text',
      blockId: 'p000000001',
      start: 2,
      end: 6,
      quote: 'link',
    });
    // Cursor inside the second row, first cell ("a<br>b").
    const cellText = table.pos + 1 + table.node.child(0).nodeSize + 3;
    const cellState = EditorState.create({ doc, selection: TextSelection.create(doc, cellText) });
    expect(anchorForSelection(cellState)).toEqual({
      kind: 'cell',
      blockId: 't000000001',
      rowId: 'row2',
      columnId: 'col1',
      quote: 'a\nb',
    });
  });
});

describe('anchors use the saved text', () => {
  it('shifts offsets past leading whitespace that saving trims', () => {
    const doc = docOf([{ id: 'p000000009', type: 'paragraph', text: '  hello world' }]);
    const block = findBlock(doc, 'p000000009');
    if (block === null) throw new Error('missing block');
    const from = offsetToPos(block.node, block.pos, 2);
    const to = offsetToPos(block.node, block.pos, 7);
    const state = EditorState.create({ doc, selection: TextSelection.create(doc, from, to) });
    expect(anchorForSelection(state)).toEqual({
      kind: 'text',
      blockId: 'p000000009',
      start: 0,
      end: 5,
      quote: 'hello',
    });
  });
});

describe('pasting', () => {
  it('drops block and row ids the document already uses and keeps the others', () => {
    const doc = docOf(blocks);
    const copied = docOf([
      { id: 'p000000001', type: 'paragraph', text: 'copy of an existing block' },
      { id: 'fresh00002', type: 'paragraph', text: 'new block' },
      {
        id: 'fresh00003',
        type: 'table',
        columns: [{ id: 'col1', align: 'none' }],
        rows: [{ id: 'row1', cells: ['x'] }],
      },
    ]);
    const slice = clearDuplicateIds(copied.slice(0, copied.content.size), doc);
    const ids: unknown[] = [];
    slice.content.forEach((node) => ids.push(node.attrs.id));
    const table = slice.content.child(2);
    expect(ids).toEqual([null, 'fresh00002', 'fresh00003']);
    expect([table.attrs.columns, table.child(0).attrs.id]).toEqual([[], null]);
  });
});

describe('block id plugin', () => {
  it('keeps the stored id on the first half of a split and gives the second half a new one', () => {
    const doc = docOf([{ id: 'p000000001', type: 'paragraph', text: 'hello world' }]);
    const state = EditorState.create({ doc, plugins: [blockIdPlugin(() => 'fresh00001')] });
    const split = state.tr.split(1 + 'hello'.length);
    const next = state.applyTransaction(split).state;
    const ids: unknown[] = [];
    next.doc.forEach((node) => ids.push(node.attrs.id));
    expect(ids).toEqual(['p000000001', 'fresh00001']);
  });
});
