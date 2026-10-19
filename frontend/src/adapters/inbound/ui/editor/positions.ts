import type { JSONContent } from '@tiptap/core';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { EditorState } from '@tiptap/pm/state';
import type { SavedSelection } from '../../../../domain/document/inline';
import { savedSelection } from '../../../../domain/document/inline';
import type { Anchor } from '../../../../domain/thread/thread';
import type { AnchorError } from '../../../../domain/thread/anchor';
import { textAnchor } from '../../../../domain/thread/anchor';
import { inlineFromJSON } from './convert';

/*
 * Mapping between ProseMirror positions and the plain-text offsets that text anchors store. Plain text
 * counts every character of text and one "\n" per hard break, matching domain plainText().
 */

export type LocatedBlock = { readonly node: PMNode; readonly pos: number; readonly index: number };

export function findBlock(doc: PMNode, blockId: string): LocatedBlock | null {
  let found: LocatedBlock | null = null;
  doc.forEach((node, pos, index) => {
    if (found === null && node.attrs.id === blockId) found = { node, pos, index };
  });
  return found;
}

/** Top-level block containing a document position. */
export function blockAtPos(doc: PMNode, pos: number): LocatedBlock | null {
  const $pos = doc.resolve(Math.min(Math.max(pos, 0), doc.content.size));
  if ($pos.depth === 0) {
    const index = Math.min($pos.index(0), doc.childCount - 1);
    const node = doc.maybeChild(index);
    return node === null ? null : { node, pos: $pos.posAtIndex(index, 0), index };
  }
  return { node: $pos.node(1), pos: $pos.before(1), index: $pos.index(0) };
}

/**
 * A selection inside a textblock expressed against the text that will be stored. Inline text is
 * trimmed on save, so editor offsets are shifted; code keeps its text as is.
 */
export function savedRange(block: PMNode, start: number, end: number): SavedSelection {
  if (block.type.name === 'codeBlock') return { text: block.textContent, start, end };
  return savedSelection(inlineFromJSON((block.toJSON() as JSONContent).content), start, end);
}

/** Document position for a plain-text offset inside the textblock that starts at `blockPos`. */
export function offsetToPos(block: PMNode, blockPos: number, offset: number): number {
  let remaining = Math.max(0, offset);
  let position = blockPos + 1;
  for (let index = 0; index < block.childCount; index++) {
    const child = block.child(index);
    const length = child.isText ? (child.text?.length ?? 0) : 1;
    if (remaining <= length)
      return position + (child.isText ? remaining : remaining === 0 ? 0 : child.nodeSize);
    remaining -= length;
    position += child.nodeSize;
  }
  return position;
}

/** Plain-text offset of a document position inside the textblock that starts at `blockPos`. */
export function posToOffset(block: PMNode, blockPos: number, pos: number): number {
  const inside = Math.min(Math.max(pos - blockPos - 1, 0), block.content.size);
  return block.textBetween(0, inside, '', '\n').length;
}

export type CellLocation = {
  readonly rowId: string;
  readonly columnId: string;
  readonly rowIndex: number;
  readonly columnIndex: number;
};

/** Row/column of the table cell containing `pos`, if any. */
export function cellAt(state: EditorState, pos: number): (CellLocation & { table: LocatedBlock }) | null {
  const $pos = state.doc.resolve(pos);
  for (let depth = $pos.depth; depth > 0; depth--) {
    if ($pos.node(depth).type.name !== 'tableCell') continue;
    const table = blockAtPos(state.doc, pos);
    if (table === null || table.node.type.name !== 'table') return null;
    const rowIndex = $pos.index(depth - 2);
    const columnIndex = $pos.index(depth - 1);
    const columns = (table.node.attrs.columns ?? []) as readonly { id: string }[];
    const row = table.node.maybeChild(rowIndex);
    const rowId = typeof row?.attrs.id === 'string' ? row.attrs.id : null;
    const columnId = columns[columnIndex]?.id;
    if (rowId === null || columnId === undefined) return null;
    return { table, rowId, columnId, rowIndex, columnIndex };
  }
  return null;
}

/**
 * Anchor for the current selection: a cell inside tables, the whole block for an empty selection,
 * otherwise a text range clamped to the first selected block.
 */
export function anchorForSelection(state: EditorState): Anchor | AnchorError {
  const { from, to, empty } = state.selection;
  const cell = cellAt(state, from);
  if (cell !== null) {
    const cellNode = state.doc.resolve(from).parent;
    return {
      kind: 'cell',
      blockId: String(cell.table.node.attrs.id),
      rowId: cell.rowId,
      columnId: cell.columnId,
      quote: savedRange(cellNode, 0, 0).text,
    };
  }
  const block = blockAtPos(state.doc, from);
  if (block === null || typeof block.node.attrs.id !== 'string') return 'out_of_range';
  const blockId = block.node.attrs.id;
  if (empty || !block.node.isTextblock) return { kind: 'block', blockId };
  const end = Math.min(to, block.pos + block.node.nodeSize - 1);
  const saved = savedRange(
    block.node,
    posToOffset(block.node, block.pos, from),
    posToOffset(block.node, block.pos, end),
  );
  return textAnchor(blockId, saved.text, saved.start, saved.end);
}
