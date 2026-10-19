import type { Block } from './block';
import type { BlockDiff, ChangeKind, DocumentDiff } from './document';

export type DiffRow = {
  readonly block: Block;
  readonly marker: ChangeKind | null;
  readonly before: Block | null;
  /** Keys from `cellKey` for table cells that changed. */
  readonly changedCells: ReadonlySet<string>;
};

export const DIFF_MARKER: Record<ChangeKind, string> = { added: '+', modified: '~', removed: '−' };

export function cellKey(rowId: string, columnId: string): string {
  return `${rowId}:${columnId}`;
}

function row(block: Block, change: BlockDiff | undefined): DiffRow {
  return {
    block,
    marker: change?.change ?? null,
    before: change?.before ?? null,
    changedCells: new Set((change?.changedCells ?? []).map((cell) => cellKey(cell.rowId, cell.columnId))),
  };
}

/**
 * Lays out a comparison as the target document with change markers in the margin. Removed blocks are
 * shown where they used to be: right after the closest preceding block that still exists.
 */
export function buildDiffRows(
  fromBlocks: readonly Block[],
  toBlocks: readonly Block[],
  diff: DocumentDiff,
): DiffRow[] {
  const changes = new Map(diff.changes.map((change) => [change.blockId, change]));
  const rows = toBlocks.map((block) => row(block, changes.get(block.id)));
  const present = new Set(toBlocks.map((block) => block.id));
  fromBlocks.forEach((block, index) => {
    if (present.has(block.id)) return;
    const removed = changes.get(block.id);
    const previous = fromBlocks
      .slice(0, index)
      .reverse()
      .find((candidate) => rows.some((existing) => existing.block.id === candidate.id));
    const at =
      previous === undefined ? 0 : rows.findIndex((existing) => existing.block.id === previous.id) + 1;
    rows.splice(at, 0, {
      ...row(removed?.before ?? block, removed),
      marker: 'removed',
      before: removed?.before ?? block,
    });
  });
  return rows;
}

export function diffCounts(diff: DocumentDiff): Record<ChangeKind, number> {
  const counts: Record<ChangeKind, number> = { added: 0, modified: 0, removed: 0 };
  for (const change of diff.changes) counts[change.change]++;
  return counts;
}
