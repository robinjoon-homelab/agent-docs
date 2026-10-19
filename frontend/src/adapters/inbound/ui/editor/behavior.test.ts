// @vitest-environment jsdom
import { Editor } from '@tiptap/core';
import { TextSelection } from '@tiptap/pm/state';
import { afterEach, describe, expect, it } from 'vitest';
import type { Block } from '../../../../domain/document/block';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { BlockBehavior } from './behavior';
import { applyBlocks, runTableOp } from './commands';
import { blocksToDoc, docToDrafts } from './convert';
import { schemaExtensions } from './schema';

let counter = 0;
const newId = (): string => `n${String(counter++).padStart(9, '0')}`;
let editor: Editor | null = null;

function open(blocks: Block[]): Editor {
  editor = new Editor({
    element: document.createElement('div'),
    extensions: [...schemaExtensions, BlockBehavior.configure({ newId })],
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
