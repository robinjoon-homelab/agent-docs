import type { Block } from '../document/block';
import { cellPlainText } from '../document/blockText';
import type { CellAnchor, TextAnchor } from './thread';

export type AnchorError = 'empty' | 'out_of_range';

/** Builds a text anchor from a selection given as offsets into the block's plain text. */
export function textAnchor(
  blockId: string,
  blockText: string,
  start: number,
  end: number,
): TextAnchor | AnchorError {
  const from = Math.min(start, end);
  const to = Math.max(start, end);
  if (from < 0 || to > blockText.length) return 'out_of_range';
  const quote = blockText.slice(from, to);
  if (quote.trim() === '') return 'empty';
  return { kind: 'text', blockId, start: from, end: to, quote };
}

export function cellAnchor(table: Block, rowId: string, columnId: string): CellAnchor | AnchorError {
  if (table.type !== 'table') return 'out_of_range';
  const quote = cellPlainText(table, rowId, columnId);
  if (quote === null) return 'out_of_range';
  return { kind: 'cell', blockId: table.id, rowId, columnId, quote };
}

export function isAnchorError(value: unknown): value is AnchorError {
  return value === 'empty' || value === 'out_of_range';
}
