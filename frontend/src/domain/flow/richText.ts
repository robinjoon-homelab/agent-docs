import { plainText } from '../document/inline';

/** Flow descriptions stay plain strings; the viewer shows this markdown subset so long specifications stay readable. */
export type RichBlock =
  | { readonly kind: 'paragraph'; readonly lines: readonly string[] }
  | { readonly kind: 'heading'; readonly text: string }
  | { readonly kind: 'list'; readonly ordered: boolean; readonly items: readonly string[] }
  | {
      readonly kind: 'table';
      readonly header: readonly string[];
      readonly rows: readonly (readonly string[])[];
    };

const BULLET = /^\s*[-*]\s+(.*)$/;
const ORDERED = /^\s*\d+[.)]\s+(.*)$/;
const HEADING = /^#{1,4}\s+(.*)$/;
const SEPARATOR = /^:?-{3,}:?$/;

function isTable(line: string): boolean {
  return line.trim().startsWith('|');
}

function cells(line: string): string[] {
  const body = line
    .trim()
    .replace(/^\|/, '')
    .replace(/(?<!\\)\|$/, '');
  return body.split(/(?<!\\)\|/).map((cell) => cell.trim().replace(/\\\|/g, '|'));
}

function startsBlock(line: string): boolean {
  return BULLET.test(line) || ORDERED.test(line) || isTable(line) || HEADING.test(line);
}

export function richBlocks(source: string): RichBlock[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  const blocks: RichBlock[] = [];
  let i = 0;
  const at = (index: number) => lines[index] ?? '';
  while (i < lines.length) {
    const line = at(i);
    if (!line.trim()) {
      i++;
      continue;
    }
    const heading = HEADING.exec(line);
    if (heading) {
      blocks.push({ kind: 'heading', text: heading[1] ?? '' });
      i++;
    } else if (BULLET.test(line) || ORDERED.test(line)) {
      const pattern = BULLET.test(line) ? BULLET : ORDERED;
      const items: string[] = [];
      while (i < lines.length && pattern.test(at(i))) items.push(at(i++).replace(pattern, '$1'));
      blocks.push({ kind: 'list', ordered: pattern === ORDERED, items });
    } else if (isTable(line)) {
      const rows: string[][] = [];
      while (i < lines.length && isTable(at(i))) rows.push(cells(at(i++)));
      const headed = rows.length > 1 && (rows[1] ?? []).every((cell) => SEPARATOR.test(cell));
      blocks.push({
        kind: 'table',
        header: headed ? (rows[0] ?? []) : [],
        rows: headed ? rows.slice(2) : rows,
      });
    } else {
      const paragraph: string[] = [];
      while (i < lines.length && at(i).trim() && !startsBlock(at(i))) paragraph.push(at(i++));
      blocks.push({ kind: 'paragraph', lines: paragraph });
    }
  }
  return blocks;
}

/** Text for one-line previews: syntax removed, cells and items joined by spaces. */
export function richPlain(source: string): string {
  return richBlocks(source)
    .flatMap((block) =>
      block.kind === 'paragraph'
        ? block.lines
        : block.kind === 'heading'
          ? [block.text]
          : block.kind === 'list'
            ? block.items
            : [...block.header, ...block.rows.flat()],
    )
    .map((text) => plainText(text))
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
}
