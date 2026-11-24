import type { FlowDesignBlock } from '../document/designBlock';
import { designAddress, designAddressKey } from './designAddress';

/** Each occurrence has one effective annotation. A state can hide the base annotation as well as replace it. */
export function designAnnotationIndex(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state?: string | null,
) {
  const result = new Map<string, FlowDesignBlock>();
  for (const block of blocks) {
    const a = block.design;
    if (a.kind !== 'annotation' || a.screen !== screen || (a.state && a.state !== state)) continue;
    const target = designAddress(a.target);
    if (!target) continue;
    const key = designAddressKey(target);
    const old = result.get(key);
    // A state-specific decision wins, including a hidden annotation. In the same scope a visible annotation owns the click.
    if (
      !old ||
      (a.state && !old.design.state) ||
      (a.state === old.design.state && old.design.enabled === false && a.enabled !== false)
    ) {
      result.set(key, block);
    }
  }
  return result;
}
