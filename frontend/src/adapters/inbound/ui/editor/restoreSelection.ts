import { GapCursor } from '@tiptap/pm/gapcursor';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { EditorState, Transaction } from '@tiptap/pm/state';
import { NodeSelection, TextSelection } from '@tiptap/pm/state';
import { CellSelection } from '@tiptap/pm/tables';
import { blockAtPos, cellAt, findBlock } from './positions';

type SelectionPoint = { readonly blockId: string; readonly offset: number };
type CellPoint = { readonly tableId: string; readonly rowId: string; readonly columnId: string };

function selectionPoint(doc: PMNode, position: number): SelectionPoint | null {
  const block = blockAtPos(doc, position);
  return block === null || typeof block.node.attrs.id !== 'string'
    ? null
    : { blockId: block.node.attrs.id, offset: position - block.pos };
}

function restoredPosition(doc: PMNode, point: SelectionPoint | null, fallback: number): number {
  const block = point === null ? null : findBlock(doc, point.blockId);
  return block === null || point === null
    ? fallback
    : block.pos + Math.max(1, Math.min(point.offset, block.node.nodeSize - 1));
}

function cellPoint(state: EditorState, position: number): CellPoint | null {
  const cell = cellAt(state, position);
  return cell === null || typeof cell.table.node.attrs.id !== 'string'
    ? null
    : { tableId: cell.table.node.attrs.id, rowId: cell.rowId, columnId: cell.columnId };
}

function cellPosition(doc: PMNode, point: CellPoint | null): number | null {
  if (point === null) return null;
  const table = findBlock(doc, point.tableId);
  if (table === null || table.node.type.spec.tableRole !== 'table') return null;
  const columns = table.node.attrs.columns as readonly { readonly id: string }[];
  const column = columns.findIndex((entry) => entry.id === point.columnId);
  if (column < 0) return null;
  let found: number | null = null;
  table.node.forEach((row, rowOffset) => {
    if (row.attrs.id !== point.rowId) return;
    row.forEach((cell, cellOffset, index) => {
      if (index === column && cell.type.spec.tableRole === 'cell')
        found = table.pos + rowOffset + cellOffset + 2;
    });
  });
  return found;
}

function restoredTextPosition(
  state: EditorState,
  tr: Transaction,
  position: number,
  replaced: ReadonlySet<string>,
): number {
  const fallback = tr.mapping.map(position);
  const point = selectionPoint(state.doc, position);
  if (point === null || !replaced.has(point.blockId)) return fallback;
  const cell = cellPoint(state, position);
  if (cell === null) return restoredPosition(tr.doc, point, fallback);
  const target = cellPosition(tr.doc, cell);
  const node = target === null ? null : tr.doc.nodeAt(target);
  return target === null || node === null
    ? fallback
    : target + 1 + Math.min(state.doc.resolve(position).parentOffset, node.content.size);
}

function restoreGap(state: EditorState, tr: Transaction): void {
  const point = selectionPoint(state.doc, state.selection.head);
  const block = point === null ? null : findBlock(tr.doc, point.blockId);
  if (point === null || block === null) return;
  const position = tr.doc.resolve(block.pos + Math.min(point.offset, block.node.nodeSize));
  // Resolve through the public bookmark API to keep ProseMirror's gap validity and fallback rules.
  tr.setSelection(new GapCursor(position).getBookmark().resolve(tr.doc));
}

/** Recover touched selections after any block replacement; untouched text keeps native position mapping. */
export function restoreSelection(state: EditorState, tr: Transaction, replaced: ReadonlySet<string>): void {
  const selection = state.selection;
  if (selection instanceof CellSelection) {
    const anchor = cellPosition(tr.doc, cellPoint(state, selection.$anchorCell.pos + 1));
    const head = cellPosition(tr.doc, cellPoint(state, selection.$headCell.pos + 1));
    if (anchor !== null && head !== null) tr.setSelection(CellSelection.create(tr.doc, anchor, head));
  } else if (selection instanceof GapCursor) {
    restoreGap(state, tr);
  } else if (selection instanceof TextSelection) {
    const points = [selectionPoint(state.doc, selection.anchor), selectionPoint(state.doc, selection.head)];
    if (!points.some((point) => point !== null && replaced.has(point.blockId))) return;
    const anchor = restoredTextPosition(state, tr, selection.anchor, replaced);
    const head = restoredTextPosition(state, tr, selection.head, replaced);
    tr.setSelection(TextSelection.between(tr.doc.resolve(anchor), tr.doc.resolve(head)));
  } else if (selection instanceof NodeSelection) {
    const point = selectionPoint(state.doc, selection.anchor);
    const block = point === null ? null : findBlock(tr.doc, point.blockId);
    if (block !== null && NodeSelection.isSelectable(block.node))
      tr.setSelection(NodeSelection.create(tr.doc, block.pos));
  }
  // AllSelection already maps to the entire new document through ProseMirror's native map().
}
