import type { Node as PMNode } from '@tiptap/pm/model';
import { Fragment, Slice } from '@tiptap/pm/model';
import type { EditorView } from '@tiptap/pm/view';
import type { JSONContent } from '@tiptap/core';
import type { Grid } from '../../../../domain/document/tableOps';
import type { Inline } from '../../../../domain/document/inline';
import { tableFromJSON, tableToJSON } from './convert';
import { randomBlockId } from './ids';
import { pastedTextNodes } from './pasteText';
import { cellAt } from './positions';

function overlayGrid(target: Grid<Inline>, source: Grid<Inline>, row: number, column: number): Grid<Inline> {
  const width = Math.max(target.columns.length, column + source.columns.length);
  const columns = Array.from(
    { length: width },
    (_, c) => target.columns[c] ?? { id: randomBlockId(), align: 'none' as const },
  );
  const rows = Array.from({ length: Math.max(target.rows.length, row + source.rows.length) }, (_, r) => ({
    id: target.rows[r]?.id ?? randomBlockId(),
    cells: Array.from(
      { length: width },
      (_, c) => source.rows[r - row]?.cells[c - column] ?? target.rows[r]?.cells[c] ?? [],
    ),
  }));
  return { columns, rows };
}

function fillCells(view: EditorView, pasted: PMNode): boolean {
  if (pasted.type.name !== 'table') return false;
  const location =
    cellAt(view.state, view.state.selection.from) ??
    cellAt(view.state, Math.min(view.state.selection.from + 1, view.state.doc.content.size));
  if (location === null) return false;
  const { table, rowIndex, columnIndex } = location;
  const target = tableFromJSON(table.node.toJSON() as JSONContent, randomBlockId);
  const source = tableFromJSON(pasted.toJSON() as JSONContent, randomBlockId);
  const grid = overlayGrid(target, source, rowIndex, columnIndex);
  const replacement = view.state.schema.nodeFromJSON(tableToJSON(target.id, grid.columns, grid.rows));
  view.dispatch(
    view.state.tr.replaceWith(table.pos, table.pos + table.node.nodeSize, replacement).scrollIntoView(),
  );
  return true;
}

/** Runs only on paste, never on composition/text input transactions. */
export function pasteStructuredText(view: EditorView, event: ClipboardEvent): boolean {
  if (!view.editable || view.composing || view.state.selection.$from.parent.type.name === 'codeBlock')
    return false;
  const data = event.clipboardData;
  if (!data || data.getData('text/html') !== '') return false;
  const csv = data.getData('text/csv');
  const nodes = pastedTextNodes(csv || data.getData('text/plain'), csv !== '');
  if (nodes === null || nodes.length === 0) return false;
  const content = nodes.map((node) => view.state.schema.nodeFromJSON(node));
  const first = content[0];
  if (content.length === 1 && first !== undefined && fillCells(view, first)) return true;
  // Native slice fitting retains the surrounding block id for inline insertion.
  const fragment = Fragment.fromArray(content);
  const open = content.every((node) => node.isTextblock) ? 1 : 0;
  view.dispatch(view.state.tr.replaceSelection(new Slice(fragment, open, open)).scrollIntoView());
  return true;
}
