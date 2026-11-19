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

function sameOrder(a: readonly string[], b: readonly string[]): boolean {
  return a.length === b.length && a.every((id, index) => id === b[index]);
}

function sharedOrders(base: readonly Block[], mine: readonly Block[], theirs: readonly Block[]) {
  const local = new Set(mine.map((block) => block.id));
  const remote = new Set(theirs.map((block) => block.id));
  const shared = new Set(base.map((block) => block.id).filter((id) => local.has(id) && remote.has(id)));
  const order = (blocks: readonly Block[]) => blocks.map((block) => block.id).filter((id) => shared.has(id));
  return { base: order(base), mine: order(mine), theirs: order(theirs) };
}

function mergedOrder(orders: ReturnType<typeof sharedOrders>): readonly string[] | null {
  const { base, mine, theirs } = orders;
  if (sameOrder(base, mine)) return theirs;
  if (sameOrder(base, theirs) || sameOrder(mine, theirs)) return mine;

  // Each side may reverse a pair from the base. If the resulting precedence has a cycle, no ordering
  // can honor both sides. A consistent order has exactly one block at each predecessor count.
  const mineAt = new Map(mine.map((id, index) => [id, index]));
  const theirsAt = new Map(theirs.map((id, index) => [id, index]));
  const localPositions = base.map((id) => mineAt.get(id) as number);
  const remotePositions = base.map((id) => theirsAt.get(id) as number);
  const ranks = base.map(() => 0);
  for (let left = 0; left < base.length; left++) {
    for (let right = left + 1; right < base.length; right++) {
      const reversed =
        (localPositions[left] as number) > (localPositions[right] as number) ||
        (remotePositions[left] as number) > (remotePositions[right] as number);
      const rank = reversed ? left : right;
      ranks[rank] = (ranks[rank] as number) + 1;
    }
  }
  if (new Set(ranks).size !== base.length) return null;
  const result: string[] = [];
  base.forEach((id, index) => {
    result[ranks[index] as number] = id;
  });
  return result;
}

export function hasOrderConflict(
  base: readonly Block[],
  mine: readonly Block[],
  theirs: readonly Block[],
): boolean {
  return mergedOrder(sharedOrders(base, mine, theirs)) === null;
}

function insertUnplaced(result: Block[], side: readonly Block[], kept: ReadonlyMap<string, Block>): void {
  const placed = new Set(result.map((block) => block.id));
  let previous: string | null = null;
  for (const { id } of side) {
    const block = kept.get(id);
    if (block === undefined) continue;
    if (!placed.has(id)) {
      const index = previous === null ? 0 : result.findIndex((candidate) => candidate.id === previous) + 1;
      result.splice(index, 0, block);
      placed.add(id);
    }
    previous = id;
  }
}

/** Merges block contents and order independently. The caller checks for conflicts before automatically saving. */
export function mergeAfterConflict(
  base: readonly Block[],
  mine: readonly Block[],
  theirs: readonly Block[],
  choices: ReadonlyMap<string, ConflictChoice>,
  orderChoice?: ConflictChoice,
): Block[] {
  const sources: Sources = { base: byId(base), mine: byId(mine), theirs: byId(theirs), choices };
  const kept = new Map<string, Block>();
  for (const block of mine) {
    const other = sources.theirs.get(block.id);
    if (other !== undefined) kept.set(block.id, resolveBoth(block.id, block, other, sources));
    else if (keepMineOnly(block.id, block, sources)) kept.set(block.id, block);
  }
  for (const block of theirs) {
    if (!sources.mine.has(block.id) && keepTheirsOnly(block.id, block, sources)) kept.set(block.id, block);
  }
  const orders = sharedOrders(base, mine, theirs);
  const compatible = mergedOrder(orders);
  const preferTheirs =
    compatible === null
      ? orderChoice === 'theirs'
      : sameOrder(orders.base, orders.mine) && !sameOrder(orders.mine, orders.theirs);
  const ordered = compatible ?? (preferTheirs ? orders.theirs : orders.mine);
  const result = ordered.map((id) => kept.get(id) as Block);
  insertUnplaced(result, preferTheirs ? theirs : mine, kept);
  insertUnplaced(result, preferTheirs ? mine : theirs, kept);
  return result;
}

/** Includes edit/delete conflicts and distinct additions using the same block id. */
export function conflictingBlocks(
  base: readonly Block[],
  mine: readonly Block[],
  theirs: readonly Block[],
): string[] {
  const before = byId(base);
  const local = byId(mine);
  const remote = byId(theirs);
  const equal = (a: Block | undefined, b: Block | undefined): boolean =>
    a === undefined || b === undefined ? a === b : blocksEqual(a, b);
  return [...new Set([...before.keys(), ...local.keys(), ...remote.keys()])].filter(
    (id) =>
      !equal(local.get(id), before.get(id)) &&
      !equal(remote.get(id), before.get(id)) &&
      !equal(local.get(id), remote.get(id)),
  );
}
