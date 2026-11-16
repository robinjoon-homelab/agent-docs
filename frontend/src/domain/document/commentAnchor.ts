import type { Block } from './block';
import { blocksEqual } from './block';
export type CommentAnchorPreparation = 'ready' | 'save' | 'blocked';
/** Read-only viewers need no save; unsaved body or drawing input cannot be used as a server anchor. */
export function commentAnchorPreparation(input: {
  readonly writable: boolean;
  readonly base: readonly Block[];
  readonly current: readonly Block[];
  readonly drawingDraft: boolean;
}): CommentAnchorPreparation {
  if (input.drawingDraft) return 'blocked';
  if (input.writable) return 'save';
  const unchanged =
    input.base.length === input.current.length &&
    input.base.every((block, index) => {
      const current = input.current[index];
      return current !== undefined && blocksEqual(block, current);
    });
  return unchanged ? 'ready' : 'blocked';
}
