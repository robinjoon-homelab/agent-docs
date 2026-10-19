import type { Block } from '../document/block';
import { blockPlainText, cellPlainText } from '../document/blockText';
import type { DocumentDiff } from '../document/document';
import type { Thread } from './thread';
import { isDetached } from './thread';

export type RestoreImpact = { readonly revertedBlocks: number; readonly detachedThreads: number };

function survives(thread: Thread, restored: ReadonlyMap<string, Block>): boolean {
  const anchor = thread.anchor;
  if (anchor.kind === 'document') return true;
  const block = restored.get(anchor.blockId);
  if (block === undefined) return false;
  if (anchor.kind === 'text') return blockPlainText(block).includes(anchor.quote);
  if (anchor.kind === 'cell')
    return block.type === 'table' && cellPlainText(block, anchor.rowId, anchor.columnId) !== null;
  return true;
}

/**
 * Estimate shown in the restore confirmation. `diff` compares the target revision with the current
 * document; a thread detaches when its block disappears or its quoted text is no longer in the block.
 * It is an estimate: the server re-resolves anchors after the restore.
 */
export function estimateRestore(
  diff: DocumentDiff,
  restoredBlocks: readonly Block[],
  threads: readonly Thread[],
): RestoreImpact {
  const restored = new Map(restoredBlocks.map((block) => [block.id, block]));
  const touched = new Set(diff.changes.map((change) => change.blockId));
  const detached = threads.filter((thread) => {
    if (isDetached(thread) || thread.status === 'resolved') return false;
    if (thread.anchor.kind === 'document' || !touched.has(thread.anchor.blockId)) return false;
    return !survives(thread, restored);
  });
  return { revertedBlocks: diff.changes.length, detachedThreads: detached.length };
}
