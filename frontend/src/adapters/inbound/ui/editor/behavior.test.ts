// @vitest-environment jsdom
import { Editor } from '@tiptap/core';
import { AllSelection, NodeSelection, TextSelection } from '@tiptap/pm/state';
import { GapCursor } from '@tiptap/pm/gapcursor';
import { CellSelection } from '@tiptap/pm/tables';
import { Gapcursor } from '@tiptap/extensions';
import { afterEach, describe, expect, it } from 'vitest';
import type { Block, TableBlock } from '../../../../domain/document/block';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { BlockBehavior } from './behavior';
import { applyBlocks, runTableOp } from './commands';
import { blocksToDoc, docToDrafts } from './convert';
import { schemaExtensions } from './schema';
import { cellAt } from './positions';

let counter = 0;
const newId = (): string => `n${String(counter++).padStart(9, '0')}`;
let editor: Editor | null = null;

function open(blocks: Block[]): Editor {
  editor = new Editor({
    element: document.createElement('div'),
    extensions: [...schemaExtensions, Gapcursor, BlockBehavior.configure({ newId })],
    content: blocksToDoc(blocks, newId),
  });
  return editor;
}

function saved(current: Editor): Block[] {
  return toSavableBlocks(docToDrafts(current.getJSON(), newId), []);
}

function press(current: Editor, key: string): void {
  const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
  current.view.someProp('handleKeyDown', (handler) => handler(current.view, event));
}

function typeText(current: Editor, text: string): void {
  for (const char of text) {
    const { from, to } = current.state.selection;
    const handled = current.view.someProp('handleTextInput', (handler) =>
      handler(current.view, from, to, char, () => current.state.tr.insertText(char, from, to)),
    );
    if (handled !== true) current.view.dispatch(current.state.tr.insertText(char, from, to));
  }
}

function cursorAt(current: Editor, pos: number): void {
  current.view.dispatch(current.state.tr.setSelection(TextSelection.create(current.state.doc, pos)));
}

afterEach(() => {
  editor?.destroy();
  editor = null;
});

describe('replacing content from outside', () => {
  it('replaces only blocks whose content changed and keeps the cursor where it was', () => {
    const current = open([
      { id: 'p000000001', type: 'paragraph', text: 'mine' },
      { id: 'p000000002', type: 'paragraph', text: 'theirs' },
    ]);
    cursorAt(current, 3);
    applyBlocks(
      current,
      saved(current),
      [
        { id: 'p000000001', type: 'paragraph', text: 'mine' },
        { id: 'p000000002', type: 'paragraph', text: 'theirs, edited by an agent' },
      ],
      newId,
    );
    expect(saved(current).map((block) => ('text' in block ? block.text : ''))).toEqual([
      'mine',
      'theirs, edited by an agent',
    ]);
    expect(current.state.selection.from).toBe(3);
  });
});

describe('editor behavior keeps block ids', () => {
  it('Enter at the start of a block inserts an empty block above and keeps the text on its id', () => {
    const current = open([{ id: 'p000000001', type: 'paragraph', text: 'keep me' }]);
    cursorAt(current, 1);
    press(current, 'Enter');
    const rows: [boolean, string][] = [];
    current.state.doc.forEach((node) => rows.push([node.attrs.id === 'p000000001', node.textContent]));
    expect(rows).toEqual([
      [false, ''],
      [true, 'keep me'],
    ]);
    expect(current.state.selection.$from.parent.textContent).toBe('keep me');
  });

  it('Enter at the start of a checked list item keeps its id and state and adds an empty item above', () => {
    const current = open([
      { id: 'l000000001', type: 'listItem', style: 'task', depth: 1, checked: true, text: 'buy milk' },
    ]);
    cursorAt(current, 1);
    press(current, 'Enter');
    const rows: unknown[][] = [];
    current.state.doc.forEach((node) =>
      rows.push([
        node.attrs.id === 'l000000001',
        node.attrs.style,
        node.attrs.depth,
        node.attrs.checked,
        node.textContent,
      ]),
    );
    expect(rows).toEqual([
      [false, 'task', 1, false, ''],
      [true, 'task', 1, true, 'buy milk'],
    ]);
  });

  it('Enter in a list item continues the list with the same style and depth', () => {
    const current = open([
      { id: 'l000000001', type: 'listItem', style: 'numbered', depth: 1, checked: false, text: 'one' },
    ]);
    cursorAt(current, 4);
    press(current, 'Enter');
    typeText(current, 'two');
    expect(saved(current)).toEqual([
      { id: 'l000000001', type: 'listItem', style: 'numbered', depth: 1, checked: false, text: 'one' },
      {
        id: expect.any(String) as string,
        type: 'listItem',
        style: 'numbered',
        depth: 1,
        checked: false,
        text: 'two',
      },
    ]);
  });

  it('markdown shortcuts change the block type without changing its id', () => {
    const current = open([{ id: 'p000000001', type: 'paragraph', text: '' }]);
    cursorAt(current, 1);
    typeText(current, '## Title');
    expect(saved(current)).toEqual([{ id: 'p000000001', type: 'heading', level: 2, text: 'Title' }]);
  });

  it('Backspace at the start of a heading turns it back into a paragraph with the same id', () => {
    const current = open([{ id: 'h000000001', type: 'heading', level: 1, text: 'Head' }]);
    cursorAt(current, 1);
    press(current, 'Backspace');
    expect(saved(current)).toEqual([{ id: 'h000000001', type: 'paragraph', text: 'Head' }]);
  });

  it('inserting a table column keeps column ids aligned with cells', () => {
    const current = open([
      {
        id: 't000000001',
        type: 'table',
        columns: [
          { id: 'c1', align: 'none' },
          { id: 'c2', align: 'right' },
        ],
        rows: [
          { id: 'r1', cells: ['A', 'B'] },
          { id: 'r2', cells: ['a', 'b'] },
        ],
      },
    ]);
    cursorAt(current, 4);
    runTableOp(current, { kind: 'insertColumn', right: true }, () => 'c9');
    const [table] = saved(current);
    expect(table).toMatchObject({
      columns: [
        { id: 'c1', align: 'none' },
        { id: 'c9', align: 'none' },
        { id: 'c2', align: 'right' },
      ],
      rows: [
        { id: 'r1', cells: ['A', '', 'B'] },
        { id: 'r2', cells: ['a', '', 'b'] },
      ],
    });
  });
});

it.each([false, true])(
  'preserves both endpoints of a surviving block selection after remote structural changes, reversed=%s',
  (reversed) => {
    const a: Block = { id: 'p000000001', type: 'paragraph', text: 'selected words' };
    const b: Block = { id: 'p000000002', type: 'paragraph', text: 'other block' };
    const c: Block = { id: 'p000000003', type: 'paragraph', text: 'remote addition' };
    const current = open([a, b]);
    current.view.dispatch(
      current.state.tr.setSelection(
        TextSelection.create(current.state.doc, reversed ? 8 : 3, reversed ? 3 : 8),
      ),
    );
    applyBlocks(current, saved(current), [a, b, c], newId);
    expect(current.state.selection.anchor).toBe(reversed ? 8 : 3);
    expect(current.state.selection.head).toBe(reversed ? 3 : 8);
    applyBlocks(current, saved(current), [c, a], newId);
    expect(saved(current).map((block) => block.id)).toEqual([c.id, a.id]);
    expect(current.state.selection.$anchor.parent.attrs.id).toBe(a.id);
    expect(current.state.selection.$anchor.parentOffset).toBe(reversed ? 7 : 2);
    expect(current.state.selection.$head.parent.attrs.id).toBe(a.id);
    expect(current.state.selection.$head.parentOffset).toBe(reversed ? 2 : 7);
  },
);

it('uses a valid replacement selection only when the selected block itself disappears', () => {
  const a: Block = { id: 'p000000001', type: 'paragraph', text: 'deleted selection' };
  const b: Block = { id: 'p000000002', type: 'paragraph', text: 'survivor' };
  const current = open([a, b]);
  cursorAt(current, 3);
  applyBlocks(current, saved(current), [b], newId);
  expect(saved(current)).toEqual([b]);
  expect(current.state.selection.$anchor.parent.attrs.id).toBe(b.id);
  expect(current.state.selection.$anchor.parentOffset).toBeLessThanOrEqual('survivor'.length);
});

const selectionTable: TableBlock = {
  id: 'table00001',
  type: 'table',
  columns: [
    { id: 'col1', align: 'none' },
    { id: 'col2', align: 'none' },
  ],
  rows: [
    { id: 'row1', cells: ['A1', 'A2'] },
    { id: 'row2', cells: ['B1', 'B2'] },
  ],
};

function selectedCells(current: Editor): string[] {
  const selection = current.state.selection;
  if (!(selection instanceof CellSelection)) throw new Error('expected CellSelection');
  const cells: string[] = [];
  selection.forEachCell((node) => cells.push(node.textContent));
  return cells;
}

it.each([false, true])(
  'keeps the rectangular cell selection after a remote paragraph addition, reversed=%s',
  (reversed) => {
    const b: Block = { id: 'p000000002', type: 'paragraph', text: 'other block' };
    const c: Block = { id: 'p000000003', type: 'paragraph', text: 'remote addition' };
    const current = open([selectionTable, b]);
    const table = current.state.doc.child(0);
    const first = 2;
    const last = 2 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
    current.view.dispatch(
      current.state.tr.setSelection(
        CellSelection.create(current.state.doc, reversed ? last : first, reversed ? first : last),
      ),
    );
    applyBlocks(current, saved(current), [selectionTable, b, c], newId);
    expect(current.state.selection).toBeInstanceOf(CellSelection);
    expect(selectedCells(current)).toEqual(['A1', 'A2', 'B1', 'B2']);
    const selection = current.state.selection as CellSelection;
    expect(selection.$anchorCell.nodeAfter?.textContent).toBe(reversed ? 'B2' : 'A1');
    expect(selection.$headCell.nodeAfter?.textContent).toBe(reversed ? 'A1' : 'B2');
    const expanded: TableBlock = {
      ...selectionTable,
      columns: [{ id: 'col0', align: 'none' }, ...selectionTable.columns],
      rows: [
        { id: 'row0', cells: ['new', 'new', 'new'] },
        ...selectionTable.rows.map((row) => ({ ...row, cells: ['new', ...row.cells] })),
      ],
    };
    applyBlocks(current, saved(current), [c, expanded, b], newId);
    expect(selectedCells(current)).toEqual(['A1', 'A2', 'B1', 'B2']);
    expect(saved(current)).toEqual([c, expanded, b]);
  },
);

it('falls back to a valid selection when a selected table row was removed', () => {
  const current = open([selectionTable]);
  const table = current.state.doc.child(0);
  const last = 2 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
  current.view.dispatch(current.state.tr.setSelection(CellSelection.create(current.state.doc, 2, last)));
  const remaining: TableBlock = { ...selectionTable, rows: selectionTable.rows.slice(1) };
  applyBlocks(
    current,
    saved(current),
    [{ id: 'new0000001', type: 'paragraph', text: 'new' }, remaining],
    newId,
  );
  expect(current.state.selection).not.toBeInstanceOf(CellSelection);
  expect(current.state.selection.from).toBeGreaterThanOrEqual(0);
  expect(current.state.selection.to).toBeLessThanOrEqual(current.state.doc.content.size);
  expect(saved(current).at(-1)).toEqual(remaining);
});

it('preserves a supported gap cursor between surviving atom blocks after remote additions', () => {
  const left: Block = { id: 'divider001', type: 'divider' };
  const right: Block = { id: 'divider002', type: 'divider' };
  const b: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
  const c: Block = { id: 'paragraph2', type: 'paragraph', text: 'remote' };
  const current = open([left, right, b]);
  const position = current.state.doc.resolve(1);
  expect(new GapCursor(position).getBookmark().resolve(current.state.doc)).toBeInstanceOf(GapCursor);
  current.view.dispatch(current.state.tr.setSelection(new GapCursor(position)));
  applyBlocks(current, saved(current), [left, right, b, c], newId);
  expect(current.state.selection).toBeInstanceOf(GapCursor);
  expect(current.state.selection.head).toBe(1);
  applyBlocks(current, saved(current), [c, left, right, b], newId);
  expect(current.state.selection).toBeInstanceOf(GapCursor);
  expect(current.state.selection.$head.nodeBefore?.attrs.id).toBe(left.id);
  expect(current.state.selection.$head.nodeAfter?.attrs.id).toBe(right.id);
});

it('keeps native AllSelection over the whole replacement document', () => {
  const a: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
  const b: Block = { id: 'divider001', type: 'divider' };
  const current = open([a]);
  current.view.dispatch(current.state.tr.setSelection(new AllSelection(current.state.doc)));
  applyBlocks(current, saved(current), [a, b], newId);
  expect(current.state.selection).toBeInstanceOf(AllSelection);
  expect(current.state.selection.from).toBe(0);
  expect(current.state.selection.to).toBe(current.state.doc.content.size);
});

it('preserves the selected atom by block ID after the whole body is replaced', () => {
  const a: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
  const b: Block = { id: 'divider001', type: 'divider' };
  const current = open([b]);
  current.view.dispatch(current.state.tr.setSelection(NodeSelection.create(current.state.doc, 0)));
  applyBlocks(current, saved(current), [a, b], newId);
  expect(current.state.selection).toBeInstanceOf(NodeSelection);
  expect((current.state.selection as NodeSelection).node.attrs.id).toBe(b.id);
});

it('uses the native fallback when a formerly valid gap is replaced by an inline-content boundary', () => {
  const left: Block = { id: 'divider001', type: 'divider' };
  const right: Block = { id: 'divider002', type: 'divider' };
  const paragraph: Block = { id: 'paragraph1', type: 'paragraph', text: 'remote paragraph' };
  const current = open([left, right]);
  current.view.dispatch(current.state.tr.setSelection(new GapCursor(current.state.doc.resolve(1))));
  applyBlocks(current, saved(current), [left, paragraph, right], newId);
  // The old right block remains, but text before it makes the former gap invalid.
  expect(current.state.selection).not.toBeInstanceOf(GapCursor);
  expect(current.state.selection).toBeInstanceOf(NodeSelection);
  expect((current.state.selection as NodeSelection).node.attrs.id).toBe(right.id);
});

function expandedSelectionTable(): TableBlock {
  return {
    ...selectionTable,
    columns: [{ id: 'col0', align: 'none' }, ...selectionTable.columns],
    rows: [
      { id: 'row0', cells: ['new', 'new', 'new'] },
      ...selectionTable.rows.map((row) => ({ ...row, cells: ['new', ...row.cells] })),
    ],
  };
}

it.each([
  [false, false],
  [false, true],
  [true, false],
  [true, true],
])(
  'keeps stable cell endpoints on table insertion and deletion, whole-body=%s reversed=%s',
  (wholeBody, reversed) => {
    const b: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
    const c: Block = { id: 'paragraph2', type: 'paragraph', text: 'remote' };
    const current = open([selectionTable, b]);
    const table = current.state.doc.child(0);
    const first = 2;
    const last = 2 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
    current.view.dispatch(
      current.state.tr.setSelection(
        CellSelection.create(current.state.doc, reversed ? last : first, reversed ? first : last),
      ),
    );
    const expanded = expandedSelectionTable();
    const target = wholeBody ? [c, expanded, b] : [expanded, b];
    applyBlocks(current, saved(current), target, newId);
    expect(selectedCells(current)).toEqual(['A1', 'A2', 'B1', 'B2']);
    let selection = current.state.selection as CellSelection;
    expect(selection.$anchorCell.parent.attrs.id).toBe(reversed ? 'row2' : 'row1');
    expect(selection.$headCell.parent.attrs.id).toBe(reversed ? 'row1' : 'row2');
    expect(selection.$anchorCell.nodeAfter?.textContent).toBe(reversed ? 'B2' : 'A1');
    expect(selection.$headCell.nodeAfter?.textContent).toBe(reversed ? 'A1' : 'B2');
    applyBlocks(current, saved(current), [selectionTable, b], newId);
    expect(selectedCells(current)).toEqual(['A1', 'A2', 'B1', 'B2']);
    selection = current.state.selection as CellSelection;
    expect(selection.$anchorCell.nodeAfter?.textContent).toBe(reversed ? 'B2' : 'A1');
    expect(selection.$headCell.nodeAfter?.textContent).toBe(reversed ? 'A1' : 'B2');
    expect(saved(current)).toEqual([selectionTable, b]);
  },
);

it.each([
  [false, 'cursor'],
  [false, 'forward'],
  [false, 'backward'],
  [true, 'cursor'],
  [true, 'forward'],
  [true, 'backward'],
] as const)('keeps text selection in its stable cell, whole-body=%s kind=%s', (wholeBody, kind) => {
  const b: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
  const c: Block = { id: 'paragraph2', type: 'paragraph', text: 'remote' };
  const current = open([selectionTable, b]);
  const table = current.state.doc.child(0);
  const start = 3 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
  const anchor = kind === 'cursor' ? 1 : kind === 'forward' ? 0 : 2;
  const head = kind === 'cursor' ? 1 : kind === 'forward' ? 2 : 0;
  current.view.dispatch(
    current.state.tr.setSelection(TextSelection.create(current.state.doc, start + anchor, start + head)),
  );
  const expanded = expandedSelectionTable();
  applyBlocks(current, saved(current), wholeBody ? [c, expanded, b] : [expanded, b], newId);
  expect(current.state.selection).toBeInstanceOf(TextSelection);
  expect(cellAt(current.state, current.state.selection.anchor)).toMatchObject({
    rowId: 'row2',
    columnId: 'col2',
  });
  expect(cellAt(current.state, current.state.selection.head)).toMatchObject({
    rowId: 'row2',
    columnId: 'col2',
  });
  expect(current.state.selection.$anchor.parentOffset).toBe(anchor);
  expect(current.state.selection.$head.parentOffset).toBe(head);
  applyBlocks(current, saved(current), [selectionTable, b], newId);
  expect(cellAt(current.state, current.state.selection.anchor)).toMatchObject({
    rowId: 'row2',
    columnId: 'col2',
  });
  expect(current.state.selection.$anchor.parentOffset).toBe(anchor);
  expect(current.state.selection.$head.parentOffset).toBe(head);
});

it('keeps rectangular cell selection when only cell text changes in the same top-level order', () => {
  const current = open([selectionTable]);
  const table = current.state.doc.child(0);
  const last = 2 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
  current.view.dispatch(current.state.tr.setSelection(CellSelection.create(current.state.doc, 2, last)));
  const changed: TableBlock = {
    ...selectionTable,
    rows: [{ id: 'row1', cells: ['A1 changed', 'A2'] }, ...selectionTable.rows.slice(1)],
  };
  applyBlocks(current, saved(current), [changed], newId);
  expect(selectedCells(current)).toEqual(['A1 changed', 'A2', 'B1', 'B2']);
});

it.each(['row', 'column'] as const)(
  'uses a valid fallback only after the selected %s disappears in a table-only update',
  (removed) => {
    const current = open([selectionTable]);
    const table = current.state.doc.child(0);
    const start = 3 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
    cursorAt(current, start + 1);
    const target: TableBlock =
      removed === 'row'
        ? { ...selectionTable, rows: selectionTable.rows.slice(0, 1) }
        : {
            ...selectionTable,
            columns: selectionTable.columns.slice(0, 1),
            rows: selectionTable.rows.map((row) => ({ ...row, cells: row.cells.slice(0, 1) })),
          };
    applyBlocks(current, saved(current), [target], newId);
    const cell = cellAt(current.state, current.state.selection.head);
    expect(removed === 'row' ? cell?.rowId : cell?.columnId).not.toBe(removed === 'row' ? 'row2' : 'col2');
    expect(current.state.selection.from).toBeGreaterThanOrEqual(0);
    expect(current.state.selection.to).toBeLessThanOrEqual(current.state.doc.content.size);
  },
);

it.each(['node', 'gap', 'all'] as const)(
  'keeps supported %s selection when only other blocks are replaced',
  (kind) => {
    const left: Block = { id: 'divider001', type: 'divider' };
    const right: Block = { id: 'divider002', type: 'divider' };
    const body: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
    const current = open([selectionTable, left, right, body]);
    const start = current.state.doc.child(0).nodeSize;
    const selection =
      kind === 'node'
        ? NodeSelection.create(current.state.doc, start)
        : kind === 'gap'
          ? new GapCursor(current.state.doc.resolve(start + 1))
          : new AllSelection(current.state.doc);
    current.view.dispatch(current.state.tr.setSelection(selection));
    applyBlocks(
      current,
      saved(current),
      [expandedSelectionTable(), left, right, { ...body, text: 'updated body' }],
      newId,
    );
    if (kind === 'node') {
      expect(current.state.selection).toBeInstanceOf(NodeSelection);
      expect((current.state.selection as NodeSelection).node.attrs.id).toBe(left.id);
    } else if (kind === 'gap') {
      expect(current.state.selection).toBeInstanceOf(GapCursor);
      expect(current.state.selection.$head.nodeBefore?.attrs.id).toBe(left.id);
      expect(current.state.selection.$head.nodeAfter?.attrs.id).toBe(right.id);
    } else {
      expect(current.state.selection).toBeInstanceOf(AllSelection);
      expect(current.state.selection.from).toBe(0);
      expect(current.state.selection.to).toBe(current.state.doc.content.size);
    }
  },
);

it('preserves native text mapping for an untouched paragraph after an earlier table grows', () => {
  const body: Block = { id: 'paragraph1', type: 'paragraph', text: 'body' };
  const current = open([selectionTable, body]);
  const start = current.state.doc.child(0).nodeSize + 1;
  current.view.dispatch(
    current.state.tr.setSelection(TextSelection.create(current.state.doc, start + 1, start + 3)),
  );
  applyBlocks(current, saved(current), [expandedSelectionTable(), body], newId);
  expect(current.state.selection.$anchor.parent.attrs.id).toBe(body.id);
  expect(current.state.selection.$head.parent.attrs.id).toBe(body.id);
  expect(current.state.selection.$anchor.parentOffset).toBe(1);
  expect(current.state.selection.$head.parentOffset).toBe(3);
});

it.each(['row', 'column'] as const)(
  'keeps a valid fallback after a CellSelection endpoint %s is deleted without changing block order',
  (removed) => {
    const current = open([selectionTable]);
    const table = current.state.doc.child(0);
    const last = 2 + table.child(0).nodeSize + table.child(1).child(0).nodeSize;
    current.view.dispatch(current.state.tr.setSelection(CellSelection.create(current.state.doc, 2, last)));
    const target: TableBlock =
      removed === 'row'
        ? { ...selectionTable, rows: selectionTable.rows.slice(0, 1) }
        : {
            ...selectionTable,
            columns: selectionTable.columns.slice(0, 1),
            rows: selectionTable.rows.map((row) => ({ ...row, cells: row.cells.slice(0, 1) })),
          };
    applyBlocks(current, saved(current), [target], newId);
    expect(current.state.selection).not.toBeInstanceOf(CellSelection);
    expect(current.state.selection.from).toBeGreaterThanOrEqual(0);
    expect(current.state.selection.to).toBeLessThanOrEqual(current.state.doc.content.size);
  },
);
