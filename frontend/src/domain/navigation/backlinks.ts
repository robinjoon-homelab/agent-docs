import type { Block } from '../document/block';
import { parseInline } from '../document/inline';

const CANONICAL_PATH = /^\/(?:d|s\/[^/?#]+\/d)\/([^/?#]+)$/;

function fields(block: Block): readonly string[] {
  if ('text' in block) return [block.text];
  if (block.type === 'table') return block.rows.flatMap((row) => row.cells);
  return [];
}

function originOf(value: string): string | null {
  try {
    const parsed = new URL(value);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null;
    if (parsed.username !== '' || parsed.password !== '') return null;
    return parsed.origin;
  } catch {
    return null;
  }
}

function canonicalIdFromPath(pathAndRest: string): string | null {
  if (pathAndRest.startsWith('//') || pathAndRest.startsWith('/\\')) return null;
  const pathname = pathAndRest.split(/[?#]/, 1)[0] ?? '';
  const match = CANONICAL_PATH.exec(pathname);
  if (match?.[1] === undefined) return null;
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}

function targetId(href: string, appOrigin: string | null): string | null {
  if (href.startsWith('doc:')) return href.slice(4);
  if (href.startsWith('/')) return canonicalIdFromPath(href);
  if (appOrigin === null) return null;
  try {
    const parsed = new URL(href);
    if (originOf(href) !== appOrigin) return null;
    return canonicalIdFromPath(`${parsed.pathname}${parsed.search}${parsed.hash}`);
  } catch {
    return null;
  }
}

/** The fake backend uses the same inline AST as the editor, with code and escapes excluded. */
export function backlinkTargets(
  blocks: readonly Block[],
  appOrigin: string | null = null,
): ReadonlySet<string> {
  const normalizedOrigin = appOrigin === null ? null : originOf(appOrigin);
  const targets = blocks
    .flatMap(fields)
    .flatMap((text) => parseInline(text))
    .flatMap((run) => {
      if (run.kind !== 'text' || run.marks.code || run.href === null) return [];
      const id = targetId(run.href, normalizedOrigin);
      return id === null ? [] : [id];
    });
  return new Set(targets);
}
