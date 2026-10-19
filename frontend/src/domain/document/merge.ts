import type { Block } from './block';
import { blocksEqual } from './block';

export type ConflictChoice = 'mine' | 'theirs';

type Sources = {
  readonly base: ReadonlyMap<string, Block>;
  readonly mine: ReadonlyMap<string, Block>;
  readonly theirs: ReadonlyMap<string, Block>;
  readonly choices: ReadonlyMap<string, ConflictChoice>;
};

function changed(block: Block, base: Block | undefined): boolean {
  return base === undefined || !blocksEqual(block, base);
}

function resolveBoth(id: string, mine: Block, theirs: Block, sources: Sources): Block {
  const choice = sources.choices.get(id);
  if (choice !== undefined) return choice === 'mine' ? mine : theirs;
  return changed(mine, sources.base.get(id)) ? mine : theirs;
}

/** A block only I still have: keep it unless they removed it and I did not touch it. */
function keepMineOnly(id: string, mine: Block, sources: Sources): boolean {
  const choice = sources.choices.get(id);
  if (choice !== undefined) return choice === 'mine';
  return changed(mine, sources.base.get(id));
}

/** A block only they still have: keep their additions and edits, accept my untouched removals. */
function keepTheirsOnly(id: string, theirs: Block, sources: Sources): boolean {
  const base = sources.base.get(id);
  if (base === undefined) return true;
  const choice = sources.choices.get(id);
  if (choice !== undefined) return choice === 'theirs';
  return changed(theirs, base);
}

function byId(blocks: readonly Block[]): Map<string, Block> {
  return new Map(blocks.map((block) => [block.id, block]));
}

/**
 * Three-way block merge used after a save conflict. `base` is what my edits started from, `theirs` is
 * the current server content, and `choices` holds the user's pick for each conflicting block.
 * Non-conflicting blocks take whichever side changed them; my order wins, and their new blocks are
 * placed after the closest preceding block that survived.
 */
export function mergeAfterConflict(
  base: readonly Block[],
  mine: readonly Block[],
  theirs: readonly Block[],
  choices: ReadonlyMap<string, ConflictChoice>,
): Block[] {
  const sources: Sources = { base: byId(base), mine: byId(mine), theirs: byId(theirs), choices };
  const result: Block[] = [];
  for (const block of mine) {
    const other = sources.theirs.get(block.id);
    if (other !== undefined) result.push(resolveBoth(block.id, block, other, sources));
    else if (keepMineOnly(block.id, block, sources)) result.push(block);
  }
  theirs.forEach((block, index) => {
    if (sources.mine.has(block.id) || !keepTheirsOnly(block.id, block, sources)) return;
    const previous = theirs
      .slice(0, index)
      .reverse()
      .find((candidate) => result.some((kept) => kept.id === candidate.id));
    const at = previous === undefined ? 0 : result.findIndex((kept) => kept.id === previous.id) + 1;
    result.splice(at, 0, block);
  });
  return result;
}
