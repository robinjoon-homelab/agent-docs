import type { Block } from './block';
import { designAssetReferences } from './designAssetReferences';
import { parseInline } from './inline';

/** Assets reachable from current or historical blocks, excluding literal code. */
export function assetReferences(blocks: readonly Block[]): string[] {
  const ids = designAssetReferences(blocks.filter((block) => block.type === 'flowDesign'));
  for (const block of blocks) {
    if (block.type === 'image') ids.add(block.assetId);
    if (block.type === 'flowElement' && block.kind === 'image') {
      const flowAsset = /^asset:([0-9a-f-]{36})$/i.exec(block.value)?.[1];
      if (flowAsset) ids.add(flowAsset);
    }
    if (block.type === 'drawing') {
      ids.add(block.svgAssetId);
      ids.add(block.sceneAssetId);
    }
    const texts =
      'text' in block ? [block.text] : block.type === 'table' ? block.rows.flatMap((row) => row.cells) : [];
    if (
      block.type === 'flowDesign' &&
      typeof block.design.description === 'string' &&
      (block.design.kind === 'screen' || block.design.kind === 'state' || block.design.kind === 'annotation')
    ) {
      texts.push(block.design.description);
    }
    for (const text of texts) {
      for (const run of parseInline(text)) {
        if (run.kind === 'text' && run.href !== null && /^asset:[0-9a-f-]{36}$/i.test(run.href))
          ids.add(run.href.slice(6));
      }
    }
  }
  return [...ids];
}
