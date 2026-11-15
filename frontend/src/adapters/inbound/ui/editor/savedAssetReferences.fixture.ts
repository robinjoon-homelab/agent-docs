import type { Block } from '../../../../domain/document/block';
import { savedAssetReferences } from './savedAssetReferences';

/** Public server-confirmed body state; failed or unrelated saves do not invent references. */
export function savedReferenceFixture(initial: readonly string[] = []) {
  let blocks: readonly Block[] = [];
  const listeners = new Set<() => void>();
  const notify = () => {
    for (const listener of listeners) listener();
  };
  const session = {
    baseBlocks: () => blocks,
    subscribe: (listener: () => void) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
  const save = (ids: readonly string[]) => {
    blocks = ids.map((assetId, index) => ({
      id: `image${index}`,
      type: 'image',
      assetId,
      alt: '',
      caption: '',
    }));
    notify();
  };
  save(initial);
  return { references: savedAssetReferences(session), save, notify };
}
