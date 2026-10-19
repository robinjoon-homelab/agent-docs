import type { Thread } from '../../../../domain/thread/thread';

/** Resolved threads have no highlight, but their cards still need an anchor for layout and scrolling. */
export function threadAnchorElement(
  root: ParentNode,
  threads: readonly Thread[],
  threadId: string,
): HTMLElement | null {
  const highlighted = root.querySelector<HTMLElement>(`[data-thread-id="${CSS.escape(threadId)}"]`);
  if (highlighted !== null) return highlighted;
  const thread = threads.find((candidate) => candidate.id === threadId);
  if (thread === undefined || thread.anchor.kind === 'document') return null;
  return root.querySelector<HTMLElement>(`[data-block-id="${CSS.escape(thread.anchor.blockId)}"]`);
}
