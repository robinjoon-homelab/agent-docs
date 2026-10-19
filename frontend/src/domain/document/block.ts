/**
 * The document body is a flat ordered list of blocks. Nesting is expressed with list item `depth`,
 * never with child blocks. Inline text fields hold GFM inline markdown (see inline.ts).
 */

export type HeadingLevel = 1 | 2 | 3;
export type ListStyle = 'bullet' | 'numbered' | 'task';
export type CalloutKind = 'note' | 'tip' | 'important' | 'warning' | 'caution';
export type ColumnAlign = 'none' | 'left' | 'center' | 'right';

export type TableColumn = { readonly id: string; readonly align: ColumnAlign };
export type TableRow = { readonly id: string; readonly cells: readonly string[] };

export type HeadingBlock = {
  readonly id: string;
  readonly type: 'heading';
  readonly level: HeadingLevel;
  readonly text: string;
};
export type ParagraphBlock = { readonly id: string; readonly type: 'paragraph'; readonly text: string };
export type ListItemBlock = {
  readonly id: string;
  readonly type: 'listItem';
  readonly style: ListStyle;
  readonly depth: number;
  readonly checked: boolean;
  readonly text: string;
};
export type QuoteBlock = { readonly id: string; readonly type: 'quote'; readonly text: string };
export type CalloutBlock = {
  readonly id: string;
  readonly type: 'callout';
  readonly kind: CalloutKind;
  readonly text: string;
};
export type CodeBlock = {
  readonly id: string;
  readonly type: 'code';
  readonly language: string;
  readonly code: string;
};
export type TableBlock = {
  readonly id: string;
  readonly type: 'table';
  readonly columns: readonly TableColumn[];
  /** rows[0] is the header row. */
  readonly rows: readonly TableRow[];
};
export type ImageBlock = {
  readonly id: string;
  readonly type: 'image';
  readonly assetId: string;
  readonly alt: string;
  readonly caption: string;
};
export type DividerBlock = { readonly id: string; readonly type: 'divider' };

export type Block =
  | HeadingBlock
  | ParagraphBlock
  | ListItemBlock
  | QuoteBlock
  | CalloutBlock
  | CodeBlock
  | TableBlock
  | ImageBlock
  | DividerBlock;

export type BlockType = Block['type'];

/** Server responses carry an 8-hex content hash per block. */
export type StoredBlock = Block & { readonly hash: string };

export const MAX_LIST_DEPTH = 5;
export const CALLOUT_KINDS: readonly CalloutKind[] = ['note', 'tip', 'important', 'warning', 'caution'];

export const CALLOUT_LABEL: Record<CalloutKind, string> = {
  note: 'NOTE',
  tip: 'TIP',
  important: 'IMPORTANT',
  warning: 'WARNING',
  caution: 'CAUTION',
};

const ID_ALPHABET = 'abcdefghijklmnopqrstuvwxyz0123456789';

/**
 * New ids are 10 chars of [a-z0-9]. Randomness is passed in so the domain stays free of platform APIs.
 * `randomByte` must return integers in 0..255.
 */
export function newBlockId(randomByte: () => number): string {
  let id = '';
  while (id.length < 10) {
    const byte = randomByte();
    // 252 is the largest multiple of 36 below 256; rejecting the rest keeps the alphabet uniform.
    if (byte < 252) id += ID_ALPHABET[byte % 36] ?? '';
  }
  return id;
}

export function clampDepth(depth: number): number {
  return Math.min(MAX_LIST_DEPTH, Math.max(0, Math.trunc(depth)));
}

export function withoutHash(block: Block | StoredBlock): Block {
  if (!('hash' in block)) return block;
  const copy: Record<string, unknown> = { ...block };
  delete copy.hash;
  return copy as Block;
}

function sameArray<T>(a: readonly T[], b: readonly T[], same: (x: T, y: T) => boolean): boolean {
  return a.length === b.length && a.every((item, index) => same(item, b[index] as T));
}

/** Structural equality ignoring the server hash. */
export function blocksEqual(a: Block, b: Block): boolean {
  if (a.type === 'table' && b.type === 'table') {
    return (
      a.id === b.id &&
      sameArray(a.columns, b.columns, (x, y) => x.id === y.id && x.align === y.align) &&
      sameArray(a.rows, b.rows, (x, y) => x.id === y.id && sameArray(x.cells, y.cells, (p, q) => p === q))
    );
  }
  const left = withoutHash(a) as Record<string, unknown>;
  const right = withoutHash(b) as Record<string, unknown>;
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  return [...keys].every((key) => left[key] === right[key]);
}
