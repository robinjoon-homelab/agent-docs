import type { JSONContent } from '@tiptap/core';
import type { Slice } from '@tiptap/pm/model';
import { CALLOUT_LABEL } from '../../../../domain/document/block';
import type { DraftBlock } from '../../../../domain/document/draft';
import type { Inline } from '../../../../domain/document/inline';
import { serializeInline } from '../../../../domain/document/inline';
import { docToDrafts } from './convert';

function edgeWhitespace(inline: Inline, edge: 'start' | 'end'): string {
  let text = '';
  for (const run of edge === 'start' ? inline : [...inline].reverse()) {
    if (run.kind === 'break') {
      text = edge === 'start' ? `${text}<br>` : `<br>${text}`;
      continue;
    }
    if (run.marks.code) break;
    const whitespace = (edge === 'start' ? /^\s*/ : /\s*$/).exec(run.text)?.[0] ?? '';
    text = edge === 'start' ? text + whitespace : whitespace + text;
    if (whitespace.length !== run.text.length) break;
  }
  return text;
}

function inlineMarkdown(inline: Inline): string {
  const markdown = serializeInline(inline);
  const leading = edgeWhitespace(inline, 'start');
  // Saving trims whitespace; copying must keep the exact selected boundaries.
  return markdown === '' ? leading : leading + markdown + edgeWhitespace(inline, 'end');
}

function paragraphMarkdown(text: string): string {
  const indentation = /^ {0,3}/.exec(text)?.[0] ?? '';
  const body = text.slice(indentation.length);
  if (/^\d+[.)]\s/.test(body)) return indentation + body.replace(/^(\d+)([.)])/, '$1\\$2');
  const startsBlock = /^(#{1,6}(\s|$)|>|[-*+]\s|`{3,}|~{3,}|\||<!--|(-{3,}|\*{3,}|_{3,})\s*$)/.test(body);
  return indentation + (startsBlock ? `\\${body}` : body);
}

function listMarkdown(block: Extract<DraftBlock, { type: 'listItem' }>, baseDepth: number): string {
  const marker = block.style === 'numbered' ? '1.' : '-';
  const task = block.style === 'task' ? (block.checked ? '[x] ' : '[ ] ') : '';
  const text = inlineMarkdown(block.text);
  const body = block.style !== 'task' && /^\[[ xX]]\s/.test(text) ? `\\${text}` : text;
  return `${'    '.repeat(Math.max(0, block.depth - baseDepth))}${marker} ${task}${body}`;
}

function codeMarkdown(block: Extract<DraftBlock, { type: 'code' }>): string {
  const longest = Math.max(0, ...Array.from(block.code.matchAll(/`+/g), (match) => match[0].length));
  const fence = '`'.repeat(Math.max(3, longest + 1));
  return `${fence}${block.language}\n${block.code}${block.code.endsWith('\n') ? '' : '\n'}${fence}`;
}

function tableMarkdown(block: Extract<DraftBlock, { type: 'table' }>): string {
  const delimiters = { none: '---', left: ':---', center: ':---:', right: '---:' };
  const rows = block.rows.map(
    (row) => `| ${row.cells.map((cell) => inlineMarkdown(cell).replaceAll('|', '\\|')).join(' | ')} |`,
  );
  const delimiter = `| ${block.columns.map((column) => delimiters[column.align]).join(' | ')} |`;
  return [rows[0], delimiter, ...rows.slice(1)].join('\n');
}

function imageMarkdown(
  block: Extract<DraftBlock, { type: 'image' }>,
  assetUrl: (assetId: string) => string,
): string {
  const alt = block.alt.replace(/[\\[\]]/g, '\\$&');
  const url = assetUrl(block.assetId).replace(/[\\<>]/g, '\\$&');
  const caption = block.caption === '' ? '' : ` "${block.caption.replace(/[\\"]/g, '\\$&')}"`;
  return `![${alt}](<${url}>${caption})`;
}

function blockMarkdown(block: DraftBlock, assetUrl: (assetId: string) => string, baseDepth: number): string {
  switch (block.type) {
    case 'paragraph':
      return paragraphMarkdown(inlineMarkdown(block.text));
    case 'heading':
      return `${'#'.repeat(block.level)} ${inlineMarkdown(block.text)}`;
    case 'listItem':
      return listMarkdown(block, baseDepth);
    case 'quote': {
      const text = inlineMarkdown(block.text);
      return `> ${text.startsWith('[!') ? '\\' : ''}${text}`;
    }
    case 'callout':
      return `> [!${CALLOUT_LABEL[block.kind]}]\n> ${inlineMarkdown(block.text)}`;
    case 'code':
      return codeMarkdown(block);
    case 'table':
      return tableMarkdown(block);
    case 'image':
      return imageMarkdown(block, assetUrl);
    case 'divider':
      return '---';
  }
}

/** Only the selected slice is copied, including edits that have not been saved yet. */
export function clipboardMarkdown(slice: Slice, assetUrl: (assetId: string) => string): string {
  const content = slice.content.toJSON() as JSONContent[] | null;
  // A rectangular cell selection contains rows without their enclosing table.
  const nodes = content?.[0]?.type === 'tableRow' ? [{ type: 'table', content }] : (content ?? []);
  const blocks = docToDrafts({ type: 'doc', content: nodes }, () => '');
  let baseDepth = 0;
  return blocks
    .map((block, index) => {
      const previous = blocks[index - 1];
      if (block.type === 'listItem') {
        baseDepth = previous?.type === 'listItem' ? Math.min(baseDepth, block.depth) : block.depth;
      }
      const separator =
        index === 0 ? '' : block.type === 'listItem' && previous?.type === 'listItem' ? '\n' : '\n\n';
      return separator + blockMarkdown(block, assetUrl, baseDepth);
    })
    .join('');
}
