import type { Editor, JSONContent } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';
import type { Block, ColumnAlign } from '../../../../domain/document/block';
import { blocksEqual } from '../../../../domain/document/block';
import type { Inline } from '../../../../domain/document/inline';
import type { Grid } from '../../../../domain/document/tableOps';
import {
  deleteColumn,
  deleteRow,
  emptyGrid,
  insertColumn,
  insertRow,
  moveColumn,
  moveRow,
  setColumnAlign,
} from '../../../../domain/document/tableOps';
import { blocksToDoc, tableFromJSON, tableToJSON } from './convert';
import type { LocatedBlock } from './positions';
import { blockAtPos, cellAt, findBlock } from './positions';

/*
 * Editor commands that keep block ids stable: converting a block changes its type but never its id,
 * so comment anchors and history keep pointing at the same block.
 */

export type TextBlockType =
  | { readonly type: 'paragraph' }
  | { readonly type: 'heading'; readonly level: 1 | 2 | 3 }
  | { readonly type: 'listItem'; readonly style: 'bullet' | 'numbered' | 'task'; readonly checked?: boolean }
  | { readonly type: 'quote' }
  | { readonly type: 'callout'; readonly kind: string }
  | { readonly type: 'codeBlock'; readonly language: string };

export function currentBlock(editor: Editor): LocatedBlock | null {
  return blockAtPos(editor.state.doc, editor.state.selection.from);
}

/** Changes the type of the block holding the cursor, keeping its id (and list depth when relevant). */
export function convertBlock(editor: Editor, target: TextBlockType): boolean {
  const block = currentBlock(editor);
  if (block === null || !block.node.isTextblock) return false;
  const { type, ...attributes } = target;
  const nodeType = editor.schema.nodes[type];
  if (nodeType === undefined) return false;
  const attrs = {
    ...attributes,
    id: block.node.attrs.id as unknown,
    ...(type === 'listItem' ? { depth: (block.node.attrs.depth as unknown) ?? 0 } : {}),
  };
  const tr = editor.state.tr.setBlockType(block.pos, block.pos + block.node.nodeSize - 1, nodeType, attrs);
  editor.view.dispatch(tr.scrollIntoView());
  editor.view.focus();
  return true;
}

/**
 * Puts an atom or structured block (table, image, divider) in place of the current block when it is
 * an empty paragraph, otherwise after it. A paragraph follows so the user can keep typing.
 */
export function insertBlock(editor: Editor, content: JSONContent): void {
  const block = currentBlock(editor);
  const { state } = editor;
  const node = state.schema.nodeFromJSON(content);
  const isEmptyParagraph =
    block !== null && block.node.type.name === 'paragraph' && block.node.content.size === 0;
  const tr = state.tr;
  let at: number;
  if (block === null) at = state.doc.content.size;
  else if (isEmptyParagraph) {
    tr.delete(block.pos, block.pos + block.node.nodeSize);
    at = block.pos;
  } else at = block.pos + block.node.nodeSize;
  tr.insert(at, node);
  const after = at + node.nodeSize;
  if (after >= tr.doc.content.size || tr.doc.resolve(after).nodeAfter?.isTextblock !== true) {
    tr.insert(after, state.schema.nodes.paragraph?.create() ?? node);
  }
  const target = node.isTextblock || node.type.name === 'table' ? at + 1 : after + 1;
  tr.setSelection(TextSelection.near(tr.doc.resolve(Math.min(target, tr.doc.content.size))));
  editor.view.dispatch(tr.scrollIntoView());
  editor.view.focus();
}

/** Inserts an empty paragraph after the block at `blockPos` and puts the cursor in it. */
export function insertParagraphAfter(editor: Editor, blockPos: number, text = ''): void {
  const { state } = editor;
  const block = state.doc.nodeAt(blockPos);
  const paragraph = state.schema.nodes.paragraph;
  if (block === null || paragraph === undefined) return;
  const at = blockPos + block.nodeSize;
  const node = paragraph.create(null, text === '' ? null : state.schema.text(text));
  const tr = state.tr.insert(at, node);
  tr.setSelection(TextSelection.create(tr.doc, at + 1 + text.length));
  editor.view.dispatch(tr.scrollIntoView());
  editor.view.focus();
}

export function moveBlock(editor: Editor, blockPos: number, direction: -1 | 1): void {
  const { state } = editor;
  const block = blockAtPos(state.doc, blockPos + 1);
  if (block === null) return;
  const neighbourIndex = block.index + direction;
  if (neighbourIndex < 0 || neighbourIndex >= state.doc.childCount) return;
  const neighbour = state.doc.child(neighbourIndex);
  const tr = state.tr.delete(block.pos, block.pos + block.node.nodeSize);
  const insertAt = direction === 1 ? block.pos + neighbour.nodeSize : block.pos - neighbour.nodeSize;
  tr.insert(insertAt, block.node);
  tr.setSelection(NodeSelection.create(tr.doc, insertAt));
  editor.view.dispatch(tr.scrollIntoView());
}

export function deleteBlockAt(editor: Editor, blockPos: number): void {
  const { state } = editor;
  const node = state.doc.nodeAt(blockPos);
  if (node === null) return;
  const tr = state.tr.delete(blockPos, blockPos + node.nodeSize);
  if (tr.doc.childCount === 0) tr.insert(0, state.schema.nodes.paragraph?.create() ?? node);
  editor.view.dispatch(tr);
}

/** Starts a ProseMirror move-drag of a whole block from the block handle. */
export function startBlockDrag(editor: Editor, blockPos: number, dataTransfer: DataTransfer): void {
  const view = editor.view;
  const selection = NodeSelection.create(view.state.doc, blockPos);
  view.dispatch(view.state.tr.setSelection(selection));
  view.dragging = { slice: selection.content(), move: true };
  const dom = view.nodeDOM(blockPos);
  if (dom instanceof HTMLElement) dataTransfer.setDragImage(dom, 0, 0);
  dataTransfer.setData('text/plain', '');
  dataTransfer.effectAllowed = 'move';
}

/* Tables ---------------------------------------------------------------------------------------- */

export type TableOp =
  | { readonly kind: 'insertRow'; readonly below: boolean }
  | { readonly kind: 'deleteRow' }
  | { readonly kind: 'moveRow'; readonly by: -1 | 1 }
  | { readonly kind: 'insertColumn'; readonly right: boolean }
  | { readonly kind: 'deleteColumn' }
  | { readonly kind: 'moveColumn'; readonly by: -1 | 1 }
  | { readonly kind: 'align'; readonly align: ColumnAlign };

type Cursor = { readonly row: number; readonly column: number };

function applyOp(
  grid: Grid<Inline>,
  op: TableOp,
  at: Cursor,
  newId: () => string,
): { grid: Grid<Inline>; cursor: Cursor } {
  switch (op.kind) {
    case 'insertRow': {
      const row = at.row + (op.below ? 1 : 0);
      return {
        grid: insertRow(grid, Math.max(row, 1), newId(), []),
        cursor: { row: Math.max(row, 1), column: at.column },
      };
    }
    case 'deleteRow':
      return { grid: deleteRow(grid, at.row), cursor: { row: Math.max(0, at.row - 1), column: at.column } };
    case 'moveRow':
      return {
        grid: moveRow(grid, at.row, at.row + op.by),
        cursor: { row: at.row + op.by, column: at.column },
      };
    case 'insertColumn': {
      const column = at.column + (op.right ? 1 : 0);
      return { grid: insertColumn(grid, column, newId(), []), cursor: { row: at.row, column } };
    }
    case 'deleteColumn':
      return {
        grid: deleteColumn(grid, at.column),
        cursor: { row: at.row, column: Math.max(0, at.column - 1) },
      };
    case 'moveColumn':
      return {
        grid: moveColumn(grid, at.column, at.column + op.by),
        cursor: { row: at.row, column: at.column + op.by },
      };
    case 'align':
      return { grid: setColumnAlign(grid, at.column, op.align), cursor: at };
  }
}

function cellPosition(table: PMNode, tablePos: number, cursor: Cursor): number {
  const rowIndex = Math.min(Math.max(cursor.row, 0), table.childCount - 1);
  let pos = tablePos + 1;
  for (let index = 0; index < rowIndex; index++) pos += table.child(index).nodeSize;
  const row = table.child(rowIndex);
  const columnIndex = Math.min(Math.max(cursor.column, 0), row.childCount - 1);
  pos += 1;
  for (let index = 0; index < columnIndex; index++) pos += row.child(index).nodeSize;
  return pos + 1;
}

/** Row/column edits run on the domain table model and replace the table node in one step. */
export function runTableOp(editor: Editor, op: TableOp, newId: () => string): boolean {
  const { state } = editor;
  const cell = cellAt(state, state.selection.from);
  if (cell === null) return false;
  const current = tableFromJSON(cell.table.node.toJSON() as JSONContent, newId);
  const result = applyOp(current, op, { row: cell.rowIndex, column: cell.columnIndex }, newId);
  const json = tableToJSON(current.id, result.grid.columns, result.grid.rows);
  const node = state.schema.nodeFromJSON(json);
  const tr = state.tr.replaceWith(cell.table.pos, cell.table.pos + cell.table.node.nodeSize, node);
  tr.setSelection(TextSelection.create(tr.doc, cellPosition(node, cell.table.pos, result.cursor)));
  editor.view.dispatch(tr);
  editor.view.focus();
  return true;
}

export function newTableJSON(newId: () => string): JSONContent {
  const grid = emptyGrid<Inline>(3, 2, newId, []);
  return tableToJSON(newId(), grid.columns, grid.rows);
}

/* Replacing content from outside (server merge, conflict resolution) --------------------------- */

function sameOrder(a: readonly Block[], b: readonly Block[]): boolean {
  return a.length === b.length && a.every((block, index) => block.id === b[index]?.id);
}

/**
 * Brings the editor from `current` (its blocks as they would be saved) to `target`. When only block
 * contents differ, just those blocks are replaced, so the cursor and the blocks the user is editing
 * stay put; when blocks were added, removed or reordered the whole body is replaced.
 * Not added to undo history: undoing must not bring back another writer's old text.
 */
export function applyBlocks(
  editor: Editor,
  current: readonly Block[],
  target: readonly Block[],
  newId: () => string,
): void {
  const { state } = editor;
  const tr = state.tr;
  if (sameOrder(current, target)) {
    const changed = target.filter((block, index) => !blocksEqual(block, current[index] as Block));
    const located = changed
      .map((block) => ({ block, at: findBlock(state.doc, block.id) }))
      .filter((entry) => entry.at !== null)
      .sort((a, b) => (b.at?.pos ?? 0) - (a.at?.pos ?? 0));
    for (const { block, at } of located) {
      if (at === null) continue;
      const node = state.schema.nodeFromJSON(blocksToDoc([block], newId).content?.[0] ?? {});
      tr.replaceWith(at.pos, at.pos + at.node.nodeSize, node);
    }
  } else {
    const doc = state.schema.nodeFromJSON(blocksToDoc(target, newId));
    tr.replaceWith(0, state.doc.content.size, doc.content);
  }
  if (!tr.docChanged) return;
  editor.view.dispatch(tr.setMeta('addToHistory', false).setMeta('preventUpdate', true));
}
