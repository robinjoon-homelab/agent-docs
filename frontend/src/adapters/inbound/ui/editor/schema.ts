import type { NodeViewRendererProps } from '@tiptap/core';
import { Extension, Node, callOrReturn, getExtensionField, mergeAttributes } from '@tiptap/core';
import Bold from '@tiptap/extension-bold';
import Code from '@tiptap/extension-code';
import Document from '@tiptap/extension-document';
import HardBreak from '@tiptap/extension-hard-break';
import Italic from '@tiptap/extension-italic';
import Link from '@tiptap/extension-link';
import Strike from '@tiptap/extension-strike';
import Text from '@tiptap/extension-text';
import type { Node as PMNode } from '@tiptap/pm/model';
import type { ViewMutationRecord } from '@tiptap/pm/view';
import { CALLOUT_KINDS, CALLOUT_LABEL } from '../../../../domain/document/block';

/*
 * ProseMirror schema for the flat block model. Every top-level node is one stored block and carries
 * its server id in `id`; ids are never copied on split (keepOnSplit: false) so the block-id plugin can
 * give the new half a fresh id while the original keeps the stored one.
 */

declare module '@tiptap/core' {
  interface NodeConfig<Options, Storage> {
    /** prosemirror-tables role ("table", "row", "cell"); copied into the node spec by TableRoles. */
    tableRole?: string | ((this: { name: string; options: Options; storage: Storage }) => string);
  }
}

/** Copies `tableRole` into node specs so prosemirror-tables can find the table, row and cell types. */
export const TableRoles = Extension.create({
  name: 'tableRoles',
  extendNodeSchema(extension) {
    const context = {
      name: extension.name,
      options: extension.options as unknown,
      storage: extension.storage as unknown,
    };
    const tableRole: unknown = callOrReturn(getExtensionField(extension, 'tableRole', context));
    return { tableRole };
  },
});

const idAttribute = {
  id: {
    default: null,
    keepOnSplit: false,
    parseHTML: (element: HTMLElement) => element.getAttribute('data-block-id'),
    renderHTML: (attributes: Record<string, unknown>) =>
      typeof attributes.id === 'string' ? { 'data-block-id': attributes.id } : {},
  },
};

export const BlockDocument = Document.extend({ content: 'block+' });

export const ParagraphNode = Node.create({
  name: 'paragraph',
  group: 'block',
  content: 'inline*',
  priority: 1000,
  addAttributes: () => ({ ...idAttribute }),
  parseHTML: () => [{ tag: 'p' }],
  renderHTML: ({ HTMLAttributes }) => ['p', HTMLAttributes, 0],
});

export const HeadingNode = Node.create({
  name: 'heading',
  group: 'block',
  content: 'inline*',
  defining: true,
  addAttributes: () => ({
    ...idAttribute,
    level: { default: 1, rendered: false },
  }),
  // Pasted h4-h6 become level 3: the platform has three heading levels.
  parseHTML: () =>
    [1, 2, 3, 4, 5, 6].map((level) => ({ tag: `h${level}`, attrs: { level: Math.min(level, 3) } })),
  renderHTML: ({ node, HTMLAttributes }) => [`h${String(node.attrs.level)}`, HTMLAttributes, 0],
});

function listItemView({ node, getPos, editor }: NodeViewRendererProps) {
  let current: PMNode = node;
  const dom = document.createElement('div');
  dom.className = 'li';
  const checkbox = document.createElement('input');
  checkbox.type = 'checkbox';
  checkbox.className = 'li-check';
  checkbox.contentEditable = 'false';
  checkbox.setAttribute('aria-label', '완료');
  const content = document.createElement('div');
  content.className = 'li-content';
  dom.append(checkbox, content);

  const apply = (next: PMNode): void => {
    const style = String(next.attrs.style);
    dom.dataset.style = style;
    dom.dataset.depth = String(next.attrs.depth);
    dom.style.setProperty('--depth', String(next.attrs.depth));
    if (typeof next.attrs.id === 'string') dom.dataset.blockId = next.attrs.id;
    checkbox.hidden = style !== 'task';
    checkbox.checked = next.attrs.checked === true;
    checkbox.disabled = !editor.isEditable;
    dom.classList.toggle('li-done', style === 'task' && next.attrs.checked === true);
  };
  apply(node);

  checkbox.addEventListener('mousedown', (event) => event.preventDefault());
  checkbox.addEventListener('change', () => {
    const position = getPos();
    if (!editor.isEditable || typeof position !== 'number') {
      checkbox.checked = current.attrs.checked === true;
      return;
    }
    editor.view.dispatch(editor.state.tr.setNodeAttribute(position, 'checked', checkbox.checked));
  });

  return {
    dom,
    contentDOM: content,
    update: (next: PMNode) => {
      if (next.type !== current.type) return false;
      current = next;
      apply(next);
      return true;
    },
    stopEvent: (event: Event) => event.target === checkbox,
    ignoreMutation: (mutation: ViewMutationRecord) =>
      mutation.target === checkbox || (mutation.type === 'attributes' && mutation.target === dom),
  };
}

/** One list item per block; consecutive items look like a list. Nesting is the `depth` attribute. */
export const ListItemNode = Node.create({
  name: 'listItem',
  group: 'block',
  content: 'inline*',
  defining: true,
  addAttributes: () => ({
    ...idAttribute,
    style: { default: 'bullet', keepOnSplit: true, rendered: false },
    depth: { default: 0, keepOnSplit: true, rendered: false },
    checked: { default: false, keepOnSplit: false, rendered: false },
  }),
  parseHTML: () => [
    { tag: 'div.li' },
    {
      // Pasted HTML lists become one block per item.
      tag: 'li',
      getAttrs: (element: HTMLElement) => ({
        style: element.parentElement?.tagName === 'OL' ? 'numbered' : 'bullet',
      }),
    },
  ],
  renderHTML: ({ node, HTMLAttributes }) => [
    'div',
    mergeAttributes(HTMLAttributes, {
      class: 'li',
      'data-style': String(node.attrs.style),
      'data-depth': String(node.attrs.depth),
    }),
    ['div', { class: 'li-content' }, 0],
  ],
  addNodeView: () => listItemView,
});

export const QuoteNode = Node.create({
  name: 'quote',
  group: 'block',
  content: 'inline*',
  defining: true,
  addAttributes: () => ({ ...idAttribute }),
  parseHTML: () => [{ tag: 'blockquote' }],
  renderHTML: ({ HTMLAttributes }) => ['blockquote', HTMLAttributes, 0],
});

function calloutView({ node, getPos, editor }: NodeViewRendererProps) {
  const dom = document.createElement('div');
  dom.className = 'callout';
  const header = document.createElement('div');
  header.className = 'callout-kind';
  header.contentEditable = 'false';
  const select = document.createElement('select');
  select.setAttribute('aria-label', '콜아웃 종류');
  for (const kind of CALLOUT_KINDS) {
    const option = document.createElement('option');
    option.value = kind;
    option.textContent = CALLOUT_LABEL[kind];
    select.append(option);
  }
  const label = document.createElement('span');
  header.append(select, label);
  const content = document.createElement('div');
  content.className = 'callout-content';
  dom.append(header, content);

  const apply = (next: PMNode): void => {
    const kind = String(next.attrs.kind) as keyof typeof CALLOUT_LABEL;
    dom.dataset.kind = kind;
    if (typeof next.attrs.id === 'string') dom.dataset.blockId = next.attrs.id;
    select.value = kind;
    label.textContent = CALLOUT_LABEL[kind];
    select.hidden = !editor.isEditable;
    label.hidden = editor.isEditable;
  };
  apply(node);
  select.addEventListener('change', () => {
    const position = getPos();
    if (typeof position === 'number')
      editor.view.dispatch(editor.state.tr.setNodeAttribute(position, 'kind', select.value));
  });

  return {
    dom,
    contentDOM: content,
    update: (next: PMNode) => {
      if (next.type.name !== 'callout') return false;
      apply(next);
      return true;
    },
    stopEvent: (event: Event) => header.contains(event.target as globalThis.Node),
    ignoreMutation: (mutation: ViewMutationRecord) =>
      header.contains(mutation.target) || (mutation.type === 'attributes' && mutation.target === dom),
  };
}

export const CalloutNode = Node.create({
  name: 'callout',
  group: 'block',
  content: 'inline*',
  defining: true,
  addAttributes: () => ({ ...idAttribute, kind: { default: 'note', rendered: false } }),
  parseHTML: () => [{ tag: 'div.callout' }],
  renderHTML: ({ node, HTMLAttributes }) => [
    'div',
    mergeAttributes(HTMLAttributes, { class: 'callout', 'data-kind': String(node.attrs.kind) }),
    ['div', { class: 'callout-content' }, 0],
  ],
  addNodeView: () => calloutView,
});

export const CodeBlockNode = Node.create({
  name: 'codeBlock',
  group: 'block',
  content: 'text*',
  marks: '',
  code: true,
  defining: true,
  addAttributes: () => ({ ...idAttribute, language: { default: '', rendered: false } }),
  parseHTML: () => [{ tag: 'pre', preserveWhitespace: 'full' }],
  renderHTML: ({ HTMLAttributes }) => ['pre', HTMLAttributes, ['code', 0]],
});

export const TableNode = Node.create({
  name: 'table',
  group: 'block',
  content: 'tableRow+',
  isolating: true,
  tableRole: 'table',
  addAttributes: () => ({ ...idAttribute, columns: { default: [], rendered: false } }),
  parseHTML: () => [{ tag: 'table' }],
  renderHTML: ({ HTMLAttributes }) => [
    'div',
    { class: 'table-scroll' },
    ['table', mergeAttributes(HTMLAttributes, { class: 'doc-table' }), ['tbody', 0]],
  ],
});

export const TableRowNode = Node.create({
  name: 'tableRow',
  content: 'tableCell+',
  tableRole: 'row',
  addAttributes: () => ({
    id: {
      default: null,
      parseHTML: (element: HTMLElement) => element.getAttribute('data-row-id'),
      renderHTML: (attributes: Record<string, unknown>) =>
        typeof attributes.id === 'string' ? { 'data-row-id': attributes.id } : {},
    },
  }),
  parseHTML: () => [{ tag: 'tr' }],
  renderHTML: ({ HTMLAttributes }) => ['tr', HTMLAttributes, 0],
});

/** Cells hold inline text only; colspan/rowspan exist because prosemirror-tables reads them, never merged. */
export const TableCellNode = Node.create({
  name: 'tableCell',
  content: 'inline*',
  isolating: true,
  tableRole: 'cell',
  addAttributes: () => ({
    colspan: { default: 1, rendered: false },
    rowspan: { default: 1, rendered: false },
    colwidth: { default: null, rendered: false },
  }),
  parseHTML: () => [{ tag: 'td' }, { tag: 'th' }],
  renderHTML: ({ HTMLAttributes }) => ['td', HTMLAttributes, 0],
});

export const ImageNode = Node.create({
  name: 'image',
  group: 'block',
  atom: true,
  selectable: true,
  addAttributes: () => ({
    ...idAttribute,
    assetId: { default: '', rendered: false },
    alt: { default: '', rendered: false },
    caption: { default: '', rendered: false },
  }),
  parseHTML: () => [{ tag: 'figure[data-asset-id]' }],
  renderHTML: ({ node, HTMLAttributes }) => [
    'figure',
    mergeAttributes(HTMLAttributes, { 'data-asset-id': String(node.attrs.assetId) }),
  ],
});

export const DividerNode = Node.create({
  name: 'divider',
  group: 'block',
  atom: true,
  selectable: true,
  addAttributes: () => ({ ...idAttribute }),
  parseHTML: () => [{ tag: 'hr' }],
  renderHTML: ({ HTMLAttributes }) => ['div', mergeAttributes(HTMLAttributes, { class: 'divider' }), ['hr']],
});

/** Inline code may sit inside bold/italic/strike, matching what inline markdown can express. */
export const CodeMark = Code.extend({ excludes: '' });

export const DocLink = Link.extend({ inclusive: false }).configure({
  openOnClick: false,
  autolink: true,
  linkOnPaste: true,
  defaultProtocol: 'https',
  isAllowedUri: (url, context) => url.startsWith('doc:') || context.defaultValidate(url),
  HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: null },
});

/** Node and mark extensions without node views or plugins: enough to build the schema in tests. */
export const schemaExtensions = [
  TableRoles,
  BlockDocument,
  Text,
  ParagraphNode,
  HeadingNode,
  ListItemNode,
  QuoteNode,
  CalloutNode,
  CodeBlockNode,
  TableNode,
  TableRowNode,
  TableCellNode,
  ImageNode,
  DividerNode,
  HardBreak,
  Bold,
  Italic,
  Strike,
  CodeMark,
  DocLink,
];
