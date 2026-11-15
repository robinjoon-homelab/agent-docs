import type { Block } from '../document/block';
import { parseInline } from '../document/inline';

function fields(block: Block): readonly string[] {
  if ('text' in block) return [block.text];
  if (block.type === 'table') return block.rows.flatMap((row) => row.cells);
  return [];
}

/** The fake backend uses the same inline AST as the editor, with code and escapes excluded. */
export function backlinkTargets(blocks: readonly Block[]): ReadonlySet<string> {
  const targets = blocks
    .flatMap(fields)
    .flatMap((text) => parseInline(text))
    .flatMap((run) => {
      if (run.kind !== 'text' || run.marks.code || run.href === null) return [];
      return run.href.startsWith('doc:') ? [run.href.slice(4)] : [];
    });
  return new Set(targets);
}
