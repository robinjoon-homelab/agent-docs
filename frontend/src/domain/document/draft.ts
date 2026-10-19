import type {
  Block,
  CalloutKind,
  CodeBlock,
  DividerBlock,
  HeadingLevel,
  ImageBlock,
  ListStyle,
  TableBlock,
  TableColumn,
} from './block';
import { clampDepth, withoutHash } from './block';
import type { Inline } from './inline';
import { parseInline, serializeInlinePreserving } from './inline';

/**
 * Editor-side form of a block: inline fields are ASTs instead of markdown. The editor adapter produces
 * drafts; `toSavableBlocks` turns them back into the stored block shape.
 */
export type DraftRow = { readonly id: string; readonly cells: readonly Inline[] };

export type DraftBlock =
  | { readonly id: string; readonly type: 'heading'; readonly level: HeadingLevel; readonly text: Inline }
  | { readonly id: string; readonly type: 'paragraph'; readonly text: Inline }
  | {
      readonly id: string;
      readonly type: 'listItem';
      readonly style: ListStyle;
      readonly depth: number;
      readonly checked: boolean;
      readonly text: Inline;
    }
  | { readonly id: string; readonly type: 'quote'; readonly text: Inline }
  | { readonly id: string; readonly type: 'callout'; readonly kind: CalloutKind; readonly text: Inline }
  | CodeBlock
  | ImageBlock
  | DividerBlock
  | {
      readonly id: string;
      readonly type: 'table';
      readonly columns: readonly TableColumn[];
      readonly rows: readonly DraftRow[];
    };

export function toDraftBlock(stored: Block): DraftBlock {
  const block = withoutHash(stored);
  switch (block.type) {
    case 'heading':
    case 'paragraph':
    case 'listItem':
    case 'quote':
    case 'callout':
      return { ...block, text: parseInline(block.text) };
    case 'table':
      return {
        id: block.id,
        type: 'table',
        columns: block.columns,
        rows: block.rows.map((row) => ({ id: row.id, cells: row.cells.map(parseInline) })),
      };
    case 'code':
    case 'image':
    case 'divider':
      return block;
  }
}

function originalText(base: Block | undefined): string | undefined {
  if (base === undefined || !('text' in base)) return undefined;
  return base.text;
}

function originalCell(
  base: Block | undefined,
  rowId: string,
  columnId: string | undefined,
): string | undefined {
  if (base?.type !== 'table' || columnId === undefined) return undefined;
  const columnIndex = base.columns.findIndex((column) => column.id === columnId);
  return base.rows.find((row) => row.id === rowId)?.cells[columnIndex];
}

function toTable(draft: Extract<DraftBlock, { type: 'table' }>, base: Block | undefined): TableBlock {
  return {
    id: draft.id,
    type: 'table',
    columns: draft.columns,
    rows: draft.rows.map((row) => ({
      id: row.id,
      cells: draft.columns.map((column, index) =>
        serializeInlinePreserving(row.cells[index] ?? [], originalCell(base, row.id, column.id)),
      ),
    })),
  };
}

function toBlock(draft: DraftBlock, base: Block | undefined): Block {
  switch (draft.type) {
    case 'heading':
    case 'paragraph':
    case 'quote':
    case 'callout':
      return { ...draft, text: serializeInlinePreserving(draft.text, originalText(base)) };
    case 'listItem':
      return {
        ...draft,
        depth: clampDepth(draft.depth),
        checked: draft.style === 'task' && draft.checked,
        text: serializeInlinePreserving(draft.text, originalText(base)),
      };
    case 'table':
      return toTable(draft, base);
    case 'image':
      return { ...draft, alt: draft.alt.trim(), caption: draft.caption.trim() };
    case 'code':
    case 'divider':
      return draft;
  }
}

/** The server rejects these blocks without text, so empty ones stay local until the user types. */
const DROPPED_WHEN_EMPTY: ReadonlySet<string> = new Set(['paragraph', 'heading', 'quote', 'callout']);

/**
 * Builds the block list sent on save: inline ASTs become markdown (keeping the stored markdown when
 * the visible content is unchanged), text is trimmed, and empty paragraphs, headings, quotes and
 * callouts are dropped.
 */
export function toSavableBlocks(drafts: readonly DraftBlock[], base: readonly Block[]): Block[] {
  const baseById = new Map(base.map((block) => [block.id, block]));
  return drafts
    .map((draft) => toBlock(draft, baseById.get(draft.id)))
    .filter((block) => !(DROPPED_WHEN_EMPTY.has(block.type) && 'text' in block && block.text === ''));
}
