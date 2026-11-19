import type { JSONContent } from '@tiptap/core';
import type { ColumnAlign } from '../../../../domain/document/block';
import { BREAK, parseInline, textRun } from '../../../../domain/document/inline';
import { inlineToJSON } from './convert';

function textNode(type: string, text: string, attrs: Record<string, unknown> = {}): JSONContent {
  return { type, attrs, content: inlineToJSON(parseInline(text)) };
}

/** Quoted CSV fields may contain delimiters, newlines and doubled quotes. */
export function delimitedRows(text: string, delimiter: string): string[][] | null {
  const rows: string[][] = [[]];
  let field = '';
  let quoted = false;
  let closed = false;
  for (let i = 0; i < text.length; i++) {
    const char = text[i] ?? '';
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
        closed = true;
      } else field += char;
    } else if (char === delimiter || char === '\n') {
      rows[rows.length - 1]?.push(field);
      field = '';
      closed = false;
      if (char === '\n') rows.push([]);
    } else if (char === '"' && field === '' && !closed) quoted = true;
    else if (closed || char === '"') return null;
    else field += char;
  }
  if (quoted) return null;
  rows[rows.length - 1]?.push(field);
  if (text.endsWith('\n')) rows.pop();
  return rows;
}

function gridNode(rows: readonly string[][], aligns: readonly ColumnAlign[], markdown: boolean): JSONContent {
  return {
    type: 'table',
    attrs: { columns: aligns.map((align) => ({ align })) },
    content: rows.map((cells) => ({
      type: 'tableRow',
      content: aligns.map((_, index) => ({
        type: 'tableCell',
        content: inlineToJSON(markdown ? parseInline(cells[index] ?? '') : literalCell(cells[index] ?? '')),
      })),
    })),
  };
}

function literalCell(text: string) {
  return text.split('\n').flatMap((line, index) => (index === 0 ? [textRun(line)] : [BREAK, textRun(line)]));
}

function pipeCells(line: string): string[] {
  const body = line
    .trim()
    .replace(/^\|/, '')
    .replace(/(?<!\\)\|$/, '');
  const cells: string[] = [];
  let cell = '';
  for (let i = 0; i < body.length; i++) {
    if (body[i] === '\\' && body[i + 1] === '|') {
      cell += '|';
      i++;
    } else if (body[i] === '|') {
      cells.push(cell.trim());
      cell = '';
    } else cell += body[i] ?? '';
  }
  cells.push(cell.trim());
  return cells;
}

function tableAt(lines: readonly string[], index: number): JSONContent | null {
  const header = lines[index] ?? '';
  const separator = pipeCells(lines[index + 1] ?? '');
  if (!header.includes('|') || !separator.every((cell) => /^:?-{3,}:?$/.test(cell))) return null;
  const cells = pipeCells(header);
  if (cells.length !== separator.length) return null;
  const aligns = separator.map<ColumnAlign>((cell) =>
    cell.startsWith(':') ? (cell.endsWith(':') ? 'center' : 'left') : cell.endsWith(':') ? 'right' : 'none',
  );
  const rows = [cells];
  for (let i = index + 2; i < lines.length && lines[i]?.includes('|'); i++)
    rows.push(pipeCells(lines[i] ?? ''));
  return gridNode(rows, aligns, true);
}

const UUID_PART = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';
const ASSET_REF = new RegExp(`^asset:(${UUID_PART})$`, 'i');
const ASSET_PATH = new RegExp(
  `^\\/api\\/(?:documents\\/${UUID_PART}\\/)?assets\\/(${UUID_PART})\\/content$`,
  'i',
);
const MOCKUP_IMAGE = /^!\[((?:\\.|[^\]\\])*)\]\((<[^>]+>|[^\s()]+)\s+"mockup"\)$/;

function unescapeMarkdown(text: string): string {
  return text.replace(/\\(.)/g, '$1');
}

function mockupAssetId(target: string, allowedOrigin?: string): string | null {
  const clean = target.startsWith('<') && target.endsWith('>') ? target.slice(1, -1) : target;
  const direct = ASSET_REF.exec(clean);
  if (direct?.[1] !== undefined) return direct[1].toLowerCase();
  const relative = ASSET_PATH.exec(clean);
  if (relative?.[1] !== undefined) return relative[1].toLowerCase();
  if (allowedOrigin === undefined) return null;
  try {
    const parsed = new URL(clean);
    if (parsed.origin !== allowedOrigin) return null;
    const match = ASSET_PATH.exec(parsed.pathname);
    return match?.[1]?.toLowerCase() ?? null;
  } catch {
    return null;
  }
}

function lineNode(line: string, allowedOrigin?: string): JSONContent | null {
  const mockup = MOCKUP_IMAGE.exec(line.trim());
  if (mockup) {
    const assetId = mockupAssetId(mockup[2] ?? '', allowedOrigin);
    if (assetId !== null) {
      return {
        type: 'image',
        attrs: { assetId, alt: unescapeMarkdown(mockup[1] ?? ''), caption: 'mockup' },
      };
    }
  }
  const heading = /^(#{1,6})\s+(.+)$/.exec(line);
  if (heading)
    return textNode('heading', heading[2] ?? '', { level: Math.min(3, (heading[1] ?? '').length) });
  const list = /^(\s*)([-+*]|\d+[.)])\s+(?:\[([ xX])\]\s+)?(.*)$/.exec(line);
  if (list)
    return textNode('listItem', list[4] ?? '', {
      depth: Math.min(5, Math.floor((list[1] ?? '').length / 4)),
      style: list[3] !== undefined ? 'task' : /\d/.test(list[2] ?? '') ? 'numbered' : 'bullet',
      checked: /[xX]/.test(list[3] ?? ''),
    });
  if (/^>\s?/.test(line)) return textNode('quote', line.replace(/^>\s?/, ''));
  if (/^\s*(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) return { type: 'divider' };
  return null;
}

function markdownNodes(text: string, allowedOrigin?: string): JSONContent[] | null {
  const lines = text.split('\n');
  const nodes: JSONContent[] = [];
  let recognized = false;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? '';
    const fence = /^(`{3,}|~{3,})(\S*)\s*$/.exec(line);
    const table = tableAt(lines, i);
    if (line.trim() === '$$') {
      const code: string[] = [];
      while (++i < lines.length && lines[i]?.trim() !== '$$') code.push(lines[i] ?? '');
      if (i === lines.length) return null;
      nodes.push({
        type: 'codeBlock',
        attrs: { language: 'math', displayMath: true },
        content: code.length === 0 ? [] : [{ type: 'text', text: code.join('\n') }],
      });
      recognized = true;
    } else if (fence) {
      const code: string[] = [];
      while (
        ++i < lines.length &&
        !new RegExp(`^${(fence[1] ?? '')[0]}{${(fence[1] ?? '').length},}\\s*$`).test(lines[i] ?? '')
      )
        code.push(lines[i] ?? '');
      nodes.push({
        type: 'codeBlock',
        attrs: { language: fence[2] },
        content: code.join('\n') === '' ? [] : [{ type: 'text', text: code.join('\n') }],
      });
      recognized = true;
    } else if (table) {
      nodes.push(table);
      i += table.content?.length ?? 0;
      recognized = true;
    } else if (line !== '') {
      const block = lineNode(line, allowedOrigin);
      const paragraph = textNode('paragraph', line);
      recognized ||=
        block !== null ||
        JSON.stringify(paragraph.content) !== JSON.stringify([{ type: 'text', text: line }]);
      nodes.push(block ?? paragraph);
    }
  }
  return recognized ? nodes : null;
}

/** HTML keeps the browser's rich paste path; plain prose keeps native text insertion. */
export function pastedTextNodes(text: string, csv = false, allowedOrigin?: string): JSONContent[] | null {
  const normalized = text.replace(/\r\n?/g, '\n');
  for (const delimiter of csv ? [','] : ['\t', ',']) {
    if (!normalized.includes(delimiter) && !(csv && delimiter === ',')) continue;
    const rows = delimitedRows(normalized, delimiter);
    const width = rows?.[0]?.length ?? 0;
    if (
      rows &&
      (width > 1 || csv) &&
      rows.every((row) => row.length === width) &&
      (csv || delimiter === '\t' || rows.length > 1)
    )
      return [
        gridNode(
          rows,
          Array.from({ length: width }, () => 'none'),
          false,
        ),
      ];
  }
  return markdownNodes(normalized, allowedOrigin);
}
