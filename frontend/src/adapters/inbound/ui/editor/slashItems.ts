import type { Editor, Range } from '@tiptap/core';
import { HEADING_LEVELS } from '../../../../domain/document/block';
import type { TextBlockType } from './commands';
import { convertBlock, insertBlock, newTableJSON } from './commands';
import { blockAtPos } from './positions';
import type { SuggestionItem } from './suggestions';

type SlashEntry = {
  readonly key: string;
  readonly label: string;
  readonly aliases: readonly string[];
  readonly hint?: string;
  readonly apply: (editor: Editor) => void;
};

function convert(target: TextBlockType) {
  return (editor: Editor) => {
    convertBlock(editor, target);
  };
}

/** The "/" menu. Each entry first removes the typed "/query" and then changes or inserts a block. */
export function slashItems(
  newId: () => string,
  pickImage: () => void,
  openDrawing: (anchorBlockId: string | null) => void = () => undefined,
): (query: string) => SuggestionItem[] {
  const entries: readonly SlashEntry[] = [
    ...HEADING_LEVELS.map((level) => ({
      key: `h${level}`,
      label: `제목 ${level}`,
      aliases: [`h${level}`, 'heading', '제목'],
      hint: '#'.repeat(level),
      apply: convert({ type: 'heading', level }),
    })),
    {
      key: 'bullet',
      label: '목록',
      aliases: ['list', 'bullet', 'ul'],
      hint: '-',
      apply: convert({ type: 'listItem', style: 'bullet' }),
    },
    {
      key: 'numbered',
      label: '번호 목록',
      aliases: ['numbered', 'ol', 'number'],
      hint: '1.',
      apply: convert({ type: 'listItem', style: 'numbered' }),
    },
    {
      key: 'task',
      label: '체크리스트',
      aliases: ['todo', 'task', 'check'],
      hint: '[ ]',
      apply: convert({ type: 'listItem', style: 'task', checked: false }),
    },
    {
      key: 'quote',
      label: '인용',
      aliases: ['quote', 'blockquote'],
      hint: '>',
      apply: convert({ type: 'quote' }),
    },
    {
      key: 'callout',
      label: '콜아웃',
      aliases: ['callout', 'note', 'tip', 'warning'],
      apply: convert({ type: 'callout', kind: 'note' }),
    },
    {
      key: 'code',
      label: '코드',
      aliases: ['code', 'pre'],
      hint: '```',
      apply: convert({ type: 'codeBlock', language: '' }),
    },
    {
      key: 'mermaid',
      label: '다이어그램',
      aliases: ['mermaid', 'diagram', 'chart'],
      apply: convert({ type: 'codeBlock', language: 'mermaid' }),
    },
    {
      key: 'table',
      label: '표',
      aliases: ['table', 'grid'],
      apply: (editor) => insertBlock(editor, newTableJSON(newId)),
    },
    { key: 'mockup', label: '시안', aliases: ['mockup', 'html'], apply: () => pickImage() },
    {
      key: 'drawing',
      label: '손그림',
      aliases: ['drawing', 'excalidraw', 'sketch', '그림'],
      apply: (editor) => {
        const block = blockAtPos(editor.state.doc, editor.state.selection.from);
        openDrawing(block === null ? null : String(block.node.attrs.id));
      },
    },
    {
      key: 'attachment',
      label: '파일 첨부',
      aliases: ['file', 'attachment', 'pdf'],
      apply: () => pickImage(),
    },
    {
      key: 'math',
      label: '수식',
      aliases: ['math', 'latex'],
      apply: convert({ type: 'codeBlock', language: 'math', displayMath: true }),
    },
    { key: 'image', label: '이미지', aliases: ['image', 'img', 'picture', '사진'], apply: () => pickImage() },
    {
      key: 'divider',
      label: '구분선',
      aliases: ['divider', 'hr', 'line', '---'],
      hint: '---',
      apply: (editor) => insertBlock(editor, { type: 'divider', attrs: { id: newId() } }),
    },
  ];
  return (query) => {
    const needle = query.trim().toLowerCase();
    return entries
      .filter(
        (entry) =>
          needle === '' ||
          entry.label.includes(needle) ||
          entry.aliases.some((alias) => alias.startsWith(needle)),
      )
      .map((entry) => ({
        key: entry.key,
        label: entry.label,
        hint: entry.hint,
        run: (editor: Editor, range: Range) => {
          editor.chain().focus().deleteRange(range).run();
          entry.apply(editor);
        },
      }));
  };
}
