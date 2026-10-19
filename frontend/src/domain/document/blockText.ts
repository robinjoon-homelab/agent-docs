import type { Block, TableBlock } from './block';
import { plainText } from './inline';

/**
 * Reader-visible text of a block. Text anchors store offsets into this string, so it must match what
 * the editor shows: markup removed, `<br>` as "\n", link text only, escapes resolved.
 */
export function blockPlainText(block: Block): string {
  switch (block.type) {
    case 'heading':
    case 'paragraph':
    case 'listItem':
    case 'quote':
    case 'callout':
      return plainText(block.text);
    case 'code':
      return block.code;
    case 'image':
      return block.caption;
    case 'table':
      return block.rows.map((row) => row.cells.map(plainText).join('\t')).join('\n');
    case 'divider':
      return '';
  }
}

export function cellPlainText(table: TableBlock, rowId: string, columnId: string): string | null {
  const columnIndex = table.columns.findIndex((column) => column.id === columnId);
  const row = table.rows.find((candidate) => candidate.id === rowId);
  const cell = columnIndex === -1 ? undefined : row?.cells[columnIndex];
  return cell === undefined ? null : plainText(cell);
}

export function supportsTextAnchor(block: Block): boolean {
  return ['heading', 'paragraph', 'listItem', 'quote', 'callout', 'code'].includes(block.type);
}

/** One-line preview of a block for diff lists, conflict cards and search. */
export function blockPreview(block: Block, maxLength = 120): string {
  const text = block.type === 'divider' ? '───' : blockPlainText(block).replace(/\s+/g, ' ').trim();
  return text.length > maxLength ? `${text.slice(0, maxLength - 1)}…` : text;
}
