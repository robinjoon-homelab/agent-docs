import type { Editor } from '@tiptap/core';
import { Extension, InputRule } from '@tiptap/core';
import { TextSelection } from '@tiptap/pm/state';
import { goToNextCell, tableEditing } from '@tiptap/pm/tables';
import { clampDepth } from '../../../../domain/document/block';
import type { TextBlockType } from './commands';
import { cellAt } from './positions';
import { blockDecorationsPlugin } from './plugins/blockDecorations';
import { blockIdPlugin } from './plugins/blockIds';
import { threadMarksPlugin } from './plugins/threadMarks';

const DOWNGRADE_ON_BACKSPACE = new Set(['heading', 'quote', 'callout']);

function emptyTextCursor(editor: Editor): TextSelection | null {
  const { selection } = editor.state;
  return selection instanceof TextSelection && selection.empty ? selection : null;
}

function toParagraph(editor: Editor, pos: number): boolean {
  const node = editor.state.doc.nodeAt(pos);
  const paragraph = editor.schema.nodes.paragraph;
  if (node === null || paragraph === undefined) return false;
  editor.view.dispatch(editor.state.tr.setNodeMarkup(pos, paragraph, { id: node.attrs.id as unknown }));
  return true;
}

function setDepth(editor: Editor, pos: number, depth: number): boolean {
  editor.view.dispatch(editor.state.tr.setNodeAttribute(pos, 'depth', clampDepth(depth)));
  return true;
}

/**
 * Enter keeps ids where the content stays: list items continue the list, an empty list item leaves
 * the list, and Enter at the start of a block inserts an empty block above instead of moving the
 * text (and its id) into a new block.
 */
function handleEnter(editor: Editor): boolean {
  const cursor = emptyTextCursor(editor);
  if (cursor === null) return false;
  const { $from } = cursor;
  if (cellAt(editor.state, $from.pos) !== null) return editor.commands.setHardBreak();
  if ($from.depth !== 1 || $from.parent.type.name === 'codeBlock') return false;
  const block = $from.parent;
  const blockPos = $from.before(1);
  const isList = block.type.name === 'listItem';
  // An empty item of the same list (or an empty paragraph) goes above; the text keeps its node and id.
  const above = isList
    ? block.type.create({
        id: null,
        style: block.attrs.style as unknown,
        depth: block.attrs.depth as unknown,
        checked: false,
      })
    : editor.schema.nodes.paragraph?.create();
  if ($from.parentOffset === 0 && block.content.size > 0) {
    if (above === undefined) return false;
    editor.view.dispatch(editor.state.tr.insert(blockPos, above).scrollIntoView());
    return true;
  }
  if (isList) {
    if (block.content.size === 0) return toParagraph(editor, blockPos);
    const attrs = {
      id: null,
      style: block.attrs.style as unknown,
      depth: block.attrs.depth as unknown,
      checked: false,
    };
    editor.view.dispatch(editor.state.tr.split($from.pos, 1, [{ type: block.type, attrs }]).scrollIntoView());
    return true;
  }
  return false;
}

function handleBackspace(editor: Editor): boolean {
  const cursor = emptyTextCursor(editor);
  if (cursor === null || cursor.$from.depth !== 1 || cursor.$from.parentOffset !== 0) return false;
  const block = cursor.$from.parent;
  const blockPos = cursor.$from.before(1);
  if (block.type.name === 'listItem') {
    const depth = Number(block.attrs.depth);
    return depth > 0 ? setDepth(editor, blockPos, depth - 1) : toParagraph(editor, blockPos);
  }
  if (DOWNGRADE_ON_BACKSPACE.has(block.type.name)) return toParagraph(editor, blockPos);
  return false;
}

function handleTab(editor: Editor, direction: 1 | -1): boolean {
  const { state } = editor;
  if (cellAt(state, state.selection.from) !== null) {
    goToNextCell(direction)(state, editor.view.dispatch);
    return true;
  }
  const $from = state.selection.$from;
  if ($from.depth < 1) return false;
  const block = $from.node(1);
  if (block.type.name === 'listItem')
    return setDepth(editor, $from.before(1), Number(block.attrs.depth) + direction);
  if (block.type.name === 'codeBlock' && direction === 1) return editor.commands.insertContent('  ');
  return false;
}

/** Markdown shortcut that turns the paragraph being typed into another block type, keeping its id. */
function blockRule(find: RegExp, target: (match: RegExpMatchArray) => TextBlockType): InputRule {
  return new InputRule({
    find,
    handler: ({ state, range, match }) => {
      const $from = state.doc.resolve(range.from);
      if ($from.depth !== 1 || $from.parent.type.name !== 'paragraph') return null;
      const { type, ...attributes } = target(match);
      const nodeType = state.schema.nodes[type];
      if (nodeType === undefined) return null;
      state.tr.delete(range.from, range.to).setBlockType(range.from, range.from, nodeType, {
        ...attributes,
        id: $from.parent.attrs.id as unknown,
      });
    },
  });
}

export const BlockBehavior = Extension.create<{ newId: () => string }>({
  name: 'blockBehavior',
  priority: 1000,

  addOptions() {
    return { newId: () => '' };
  },

  addKeyboardShortcuts() {
    return {
      Enter: () => handleEnter(this.editor),
      Backspace: () => handleBackspace(this.editor),
      Tab: () => handleTab(this.editor, 1),
      'Shift-Tab': () => handleTab(this.editor, -1),
    };
  },

  addInputRules() {
    return [
      blockRule(/^(#{1,3})\s$/, (match) => ({
        type: 'heading',
        level: (match[1]?.length ?? 1) as 1 | 2 | 3,
      })),
      blockRule(/^[-*]\s$/, () => ({ type: 'listItem', style: 'bullet' })),
      blockRule(/^\d+\.\s$/, () => ({ type: 'listItem', style: 'numbered' })),
      blockRule(/^\[([ xX]?)\]\s$/, (match) => ({
        type: 'listItem',
        style: 'task',
        checked: (match[1] ?? '').toLowerCase() === 'x',
      })),
      blockRule(/^>\s$/, () => ({ type: 'quote' })),
      blockRule(/^```([\w+#-]*)\s$/, (match) => ({ type: 'codeBlock', language: match[1] ?? '' })),
    ];
  },

  addProseMirrorPlugins() {
    return [blockIdPlugin(this.options.newId), threadMarksPlugin(), blockDecorationsPlugin(), tableEditing()];
  },
});
