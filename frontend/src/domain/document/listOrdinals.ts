import type { ListStyle } from './block';

export type ListLike = {
  readonly id: string;
  readonly type: string;
  readonly style?: ListStyle;
  readonly depth?: number;
};

/**
 * Numbers for numbered list items. Each item is its own block, so numbering follows consecutive items:
 * a non-list block restarts every level, a shallower item restarts deeper levels, and a bullet or
 * task item at the same depth restarts that level.
 */
export function listOrdinals(blocks: readonly ListLike[]): Map<string, number> {
  const ordinals = new Map<string, number>();
  let counters: number[] = [];
  for (const block of blocks) {
    if (block.type !== 'listItem') {
      counters = [];
      continue;
    }
    const depth = block.depth ?? 0;
    counters = counters.slice(0, depth + 1);
    if (block.style !== 'numbered') {
      counters[depth] = 0;
      continue;
    }
    const next = (counters[depth] ?? 0) + 1;
    counters[depth] = next;
    ordinals.set(block.id, next);
  }
  return ordinals;
}
