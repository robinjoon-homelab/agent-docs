import type { Block } from './block';
import { parseInline } from './inline';

/** Assets reachable from current or historical blocks, excluding literal code. */
export function assetReferences(blocks: readonly Block[]): string[] {
  const ids = new Set<string>();
  for (const block of blocks) {
    if (block.type === 'image') ids.add(block.assetId);
    const texts =
      'text' in block ? [block.text] : block.type === 'table' ? block.rows.flatMap((row) => row.cells) : [];
    for (const text of texts) {
      for (const run of parseInline(text)) {
        if (run.kind === 'text' && run.href !== null && /^asset:[0-9a-f-]{36}$/i.test(run.href))
          ids.add(run.href.slice(6));
      }
    }
  }
  return [...ids];
}
