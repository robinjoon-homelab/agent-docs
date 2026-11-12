import { getSchema } from '@tiptap/core';
import { Node as PMNode } from '@tiptap/pm/model';
import { TextSelection } from '@tiptap/pm/state';
import { CellSelection } from '@tiptap/pm/tables';
import { describe, expect, it } from 'vitest';
import type { Block } from '../../../../domain/document/block';
import { clipboardMarkdown } from './clipboardMarkdown';
import { blocksToDoc } from './convert';
import { schemaExtensions } from './schema';

const schema = getSchema(schemaExtensions);
const assetUrl = (id: string): string => `https://docs.example/api/assets/${id}/content`;

function docOf(blocks: Block[]): PMNode {
  return PMNode.fromJSON(
    schema,
    blocksToDoc(blocks, () => 'newblock01'),
  );
}

function copy(blocks: Block[]): string {
  const doc = docOf(blocks);
  return clipboardMarkdown(doc.slice(0, doc.content.size), assetUrl);
}

describe('clipboard markdown', () => {
  it('keeps heading, inline formatting, links, quotes and callouts', () => {
    expect(
      copy([
        { id: 'heading001', type: 'heading', level: 2, text: '**제목**' },
        {
          id: 'paragraph1',
          type: 'paragraph',
          text: '*강조* ~~취소~~ `code` [웹](https://example.com) [문서](doc:d1)<br>다음 줄',
        },
        { id: 'quote00001', type: 'quote', text: '인용' },
        { id: 'callout001', type: 'callout', kind: 'warning', text: '**주의**' },
        { id: 'divider001', type: 'divider' },
      ]),
    ).toBe(
      '## **제목**\n\n*강조* ~~취소~~ `code` [웹](https://example.com) [문서<br>](doc:d1)다음 줄' +
        '\n\n> 인용\n\n> [!WARNING]\n> **주의**\n\n---',
    );
  });

  it('copies only the selected text and closes formatting at the selection boundaries', () => {
    const doc = docOf([
      { id: 'paragraph1', type: 'paragraph', text: '앞 **선택한 내용** 뒤' },
      { id: 'paragraph2', type: 'paragraph', text: '선택하지 않은 문단' },
    ]);
    const slice = TextSelection.create(doc, 4, 8).content();
    expect(clipboardMarkdown(slice, assetUrl)).toBe('**택한 내**');
    expect(doc.textContent).toBe('앞 선택한 내용 뒤선택하지 않은 문단');
  });

  it('keeps partial blocks when the selection crosses block boundaries', () => {
    const doc = docOf([
      { id: 'paragraph1', type: 'paragraph', text: 'skip **first**' },
      { id: 'heading001', type: 'heading', level: 3, text: '*second* skip' },
    ]);
    const from = 1 + 'skip '.length;
    const to = doc.child(0).nodeSize + 1 + 'second'.length;
    expect(clipboardMarkdown(TextSelection.create(doc, from, to).content(), assetUrl)).toBe(
      '**first**\n\n### *second*',
    );
  });

  it('preserves whitespace and hard breaks at the edges of a partial selection', () => {
    const doc = docOf([{ id: 'paragraph1', type: 'paragraph', text: 'x **bold** y<br>z' }]);
    expect(clipboardMarkdown(TextSelection.create(doc, 2, 8).content(), assetUrl)).toBe(' **bold** ');
    expect(clipboardMarkdown(TextSelection.create(doc, 9, 10).content(), assetUrl)).toBe('<br>');
  });

  it('keeps adjacent list items together with nesting and task states', () => {
    expect(
      copy([
        { id: 'listitem01', type: 'listItem', style: 'bullet', depth: 0, checked: false, text: '상위' },
        { id: 'listitem02', type: 'listItem', style: 'numbered', depth: 1, checked: false, text: '하위' },
        { id: 'listitem03', type: 'listItem', style: 'task', depth: 0, checked: true, text: '완료' },
        { id: 'listitem04', type: 'listItem', style: 'task', depth: 0, checked: false, text: '예정' },
        { id: 'paragraph1', type: 'paragraph', text: '다음 문단' },
      ]),
    ).toBe('- 상위\n    1. 하위\n- [x] 완료\n- [ ] 예정\n\n다음 문단');
  });

  it('copies a selected nested list as a list instead of an indented code block', () => {
    expect(
      copy([
        { id: 'listitem01', type: 'listItem', style: 'numbered', depth: 2, checked: false, text: '하위' },
        { id: 'listitem02', type: 'listItem', style: 'bullet', depth: 3, checked: false, text: '더 아래' },
      ]),
    ).toBe('1. 하위\n    - 더 아래');
  });

  it('keeps inline code whitespace inside the code span without duplicating it', () => {
    expect(copy([{ id: 'paragraph1', type: 'paragraph', text: '`  x  `' }])).toBe('`  x  `');
  });

  it('keeps the first selected nested item a list item when a shallower item follows', () => {
    expect(
      copy([
        { id: 'listitem01', type: 'listItem', style: 'bullet', depth: 1, checked: false, text: '하위' },
        { id: 'listitem02', type: 'listItem', style: 'bullet', depth: 0, checked: false, text: '다음 상위' },
      ]),
    ).toBe('- 하위\n- 다음 상위');
  });

  it('uses a code fence longer than any backtick sequence in the selected code', () => {
    expect(
      copy([{ id: 'codeblock1', type: 'code', language: 'markdown', code: '```ts\nconst x = 1;\n```' }]),
    ).toBe('````markdown\n```ts\nconst x = 1;\n```\n````');
    expect(copy([{ id: 'codeblock2', type: 'code', language: 'text', code: '  x\n\n' }])).toBe(
      '```text\n  x\n\n```',
    );
  });

  it('adds table delimiters and alignment while escaping pipes and preserving line breaks', () => {
    expect(
      copy([
        {
          id: 'table00001',
          type: 'table',
          columns: [
            { id: 'col1', align: 'left' },
            { id: 'col2', align: 'center' },
            { id: 'col3', align: 'right' },
          ],
          rows: [
            { id: 'row1', cells: ['**이름**', '종류', '설명'] },
            { id: 'row2', cells: ['a | b', '`x|y`', '위<br>아래'] },
          ],
        },
      ]),
    ).toBe('| **이름** | 종류 | 설명 |\n| :--- | :---: | ---: |\n| a \\| b | `x\\|y` | 위<br>아래 |');
  });

  it('copies images with a usable asset URL, escaped alt text and caption', () => {
    expect(
      copy([
        {
          id: 'image00001',
          type: 'image',
          assetId: 'asset1',
          alt: '[그림]',
          caption: '설명 "인용"',
        },
      ]),
    ).toBe('![\\[그림\\]](<https://docs.example/api/assets/asset1/content> "설명 \\"인용\\"")');
  });

  it('copies a rectangular selection of table cells without unselected cells', () => {
    const doc = docOf([
      {
        id: 'table00001',
        type: 'table',
        columns: [
          { id: 'col1', align: 'none' },
          { id: 'col2', align: 'none' },
          { id: 'col3', align: 'none' },
        ],
        rows: [
          { id: 'row1', cells: ['**A**', 'B', 'skip'] },
          { id: 'row2', cells: ['C', '*D*', 'skip'] },
        ],
      },
    ]);
    const table = doc.child(0);
    const secondRow = table.child(0).nodeSize + 2;
    const selection = CellSelection.create(doc, 2, secondRow + table.child(1).child(0).nodeSize);
    expect(clipboardMarkdown(selection.content(), assetUrl)).toBe(
      '| **A** | B |\n| --- | --- |\n| C | *D* |',
    );
  });

  it('escapes paragraph text that would otherwise become another block type', () => {
    expect(
      copy([
        { id: 'paragraph1', type: 'paragraph', text: '# 제목 아님' },
        { id: 'paragraph2', type: 'paragraph', text: '1. 목록 아님' },
        { id: 'paragraph3', type: 'paragraph', text: '---' },
        { id: 'paragraph4', type: 'paragraph', text: ' # 제목 아님' },
        { id: 'paragraph5', type: 'paragraph', text: '   1. 목록 아님' },
      ]),
    ).toBe('\\# 제목 아님\n\n1\\. 목록 아님\n\n\\---\n\n \\# 제목 아님\n\n   1\\. 목록 아님');
  });
});
