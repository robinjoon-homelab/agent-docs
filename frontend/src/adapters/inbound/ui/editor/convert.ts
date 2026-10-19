import type { JSONContent } from '@tiptap/core';
import type {
  Block,
  CalloutKind,
  ColumnAlign,
  HeadingLevel,
  ListStyle,
  TableColumn,
} from '../../../../domain/document/block';
import { clampDepth } from '../../../../domain/document/block';
import type { DraftBlock, DraftRow } from '../../../../domain/document/draft';
import { toDraftBlock } from '../../../../domain/document/draft';
import type { Inline, InlineMarks, InlineRun } from '../../../../domain/document/inline';
import { BREAK, compactInline, textRun } from '../../../../domain/document/inline';

/*
 * Conversion between the domain block model and the editor's ProseMirror JSON. The inline AST is the
 * bridge: domain/document/inline.ts owns markdown, this file only maps runs to marks and back.
 */

type MarkJSON = { type: string; attrs?: Record<string, unknown> };

const MARK_ORDER: readonly (keyof InlineMarks)[] = ['bold', 'italic', 'strike', 'code'];

function runToJSON(run: InlineRun): JSONContent {
  if (run.kind === 'break') return { type: 'hardBreak' };
  const marks: MarkJSON[] = MARK_ORDER.filter((mark) => run.marks[mark]).map((type) => ({ type }));
  if (run.href !== null) marks.push({ type: 'link', attrs: { href: run.href } });
  return marks.length === 0 ? { type: 'text', text: run.text } : { type: 'text', text: run.text, marks };
}

export function inlineToJSON(inline: Inline): JSONContent[] {
  return compactInline(inline).map(runToJSON);
}

function textNodeToRun(node: JSONContent): InlineRun | null {
  if (node.type === 'hardBreak') return BREAK;
  if (node.type !== 'text' || node.text === undefined || node.text === '') return null;
  const types = new Set((node.marks ?? []).map((mark) => mark.type));
  const link = (node.marks ?? []).find((mark) => mark.type === 'link');
  const href = typeof link?.attrs?.href === 'string' ? link.attrs.href : null;
  return textRun(
    node.text,
    {
      bold: types.has('bold'),
      italic: types.has('italic'),
      strike: types.has('strike'),
      code: types.has('code'),
    },
    href,
  );
}

export function inlineFromJSON(content: readonly JSONContent[] | undefined): Inline {
  return compactInline((content ?? []).map(textNodeToRun).filter((run): run is InlineRun => run !== null));
}

function textBlock(type: string, attrs: Record<string, unknown>, text: Inline): JSONContent {
  const content = inlineToJSON(text);
  return content.length === 0 ? { type, attrs } : { type, attrs, content };
}

function draftToJSON(draft: DraftBlock): JSONContent {
  switch (draft.type) {
    case 'heading':
      return textBlock('heading', { id: draft.id, level: draft.level }, draft.text);
    case 'paragraph':
      return textBlock('paragraph', { id: draft.id }, draft.text);
    case 'listItem':
      return textBlock(
        'listItem',
        { id: draft.id, style: draft.style, depth: draft.depth, checked: draft.checked },
        draft.text,
      );
    case 'quote':
      return textBlock('quote', { id: draft.id }, draft.text);
    case 'callout':
      return textBlock('callout', { id: draft.id, kind: draft.kind }, draft.text);
    case 'code':
      return draft.code === ''
        ? { type: 'codeBlock', attrs: { id: draft.id, language: draft.language } }
        : {
            type: 'codeBlock',
            attrs: { id: draft.id, language: draft.language },
            content: [{ type: 'text', text: draft.code }],
          };
    case 'table':
      return tableToJSON(draft.id, draft.columns, draft.rows);
    case 'image':
      return {
        type: 'image',
        attrs: { id: draft.id, assetId: draft.assetId, alt: draft.alt, caption: draft.caption },
      };
    case 'divider':
      return { type: 'divider', attrs: { id: draft.id } };
  }
}

export function tableToJSON(
  id: string | null,
  columns: readonly TableColumn[],
  rows: readonly DraftRow[],
): JSONContent {
  return {
    type: 'table',
    attrs: { id, columns: columns.map((column) => ({ id: column.id, align: column.align })) },
    content: rows.map((row) => ({
      type: 'tableRow',
      attrs: { id: row.id },
      content: columns.map((_, index) => {
        const content = inlineToJSON(row.cells[index] ?? []);
        return content.length === 0 ? { type: 'tableCell' } : { type: 'tableCell', content };
      }),
    })),
  };
}

/** Editor content for stored blocks. An empty document still needs one (empty) paragraph. */
export function blocksToDoc(blocks: readonly Block[], newId: () => string): JSONContent {
  const content = blocks.map((block) => draftToJSON(toDraftBlock(block)));
  return {
    type: 'doc',
    content: content.length > 0 ? content : [{ type: 'paragraph', attrs: { id: newId() } }],
  };
}

function attr<T>(node: JSONContent, key: string, fallback: T): T {
  const value: unknown = node.attrs?.[key];
  return value === undefined || value === null ? fallback : (value as T);
}

function columnsOf(node: JSONContent, width: number, newId: () => string): TableColumn[] {
  const stored = attr<readonly { id?: unknown; align?: unknown }[]>(node, 'columns', []);
  return Array.from({ length: width }, (_, index) => {
    const column = stored[index];
    return {
      id: typeof column?.id === 'string' ? column.id : newId(),
      align: (typeof column?.align === 'string' ? column.align : 'none') as ColumnAlign,
    };
  });
}

export function tableFromJSON(
  node: JSONContent,
  newId: () => string,
): { id: string; columns: TableColumn[]; rows: DraftRow[] } {
  const rows = node.content ?? [];
  const width = Math.max(1, ...rows.map((row) => row.content?.length ?? 0));
  return {
    id: attr(node, 'id', newId()),
    columns: columnsOf(node, width, newId),
    rows: rows.map((row) => ({
      id: attr(row, 'id', newId()),
      cells: Array.from({ length: width }, (_, index) => inlineFromJSON(row.content?.[index]?.content)),
    })),
  };
}

function nodeToDraft(node: JSONContent, newId: () => string): DraftBlock | null {
  const id = attr(node, 'id', newId());
  const text = inlineFromJSON(node.content);
  switch (node.type) {
    case 'heading':
      return { id, type: 'heading', level: attr<HeadingLevel>(node, 'level', 1), text };
    case 'paragraph':
      return { id, type: 'paragraph', text };
    case 'listItem':
      return {
        id,
        type: 'listItem',
        style: attr<ListStyle>(node, 'style', 'bullet'),
        depth: clampDepth(attr(node, 'depth', 0)),
        checked: attr(node, 'checked', false),
        text,
      };
    case 'quote':
      return { id, type: 'quote', text };
    case 'callout':
      return { id, type: 'callout', kind: attr<CalloutKind>(node, 'kind', 'note'), text };
    case 'codeBlock':
      return {
        id,
        type: 'code',
        language: attr(node, 'language', ''),
        code: (node.content ?? []).map((child) => child.text ?? '').join(''),
      };
    case 'table':
      return { ...tableFromJSON(node, newId), id, type: 'table' };
    case 'image':
      return {
        id,
        type: 'image',
        assetId: attr(node, 'assetId', ''),
        alt: attr(node, 'alt', ''),
        caption: attr(node, 'caption', ''),
      };
    case 'divider':
      return { id, type: 'divider' };
    default:
      return null;
  }
}

/** Reads the editor document back into drafts; ids missing on brand-new nodes are filled in. */
export function docToDrafts(doc: JSONContent, newId: () => string): DraftBlock[] {
  return (doc.content ?? [])
    .map((node) => nodeToDraft(node, newId))
    .filter((draft): draft is DraftBlock => draft !== null);
}
