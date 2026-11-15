// @vitest-environment jsdom
import { Editor } from '@tiptap/core';
import { TextSelection } from '@tiptap/pm/state';
import { afterEach, describe, expect, it } from 'vitest';
import type { Block } from '../../../../domain/document/block';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { BlockBehavior } from './behavior';
import { blocksToDoc, docToDrafts } from './convert';
import { pasteStructuredText } from './paste';
import { schemaExtensions } from './schema';

let editor: Editor | null = null;
function open(blocks: Block[]) {
  let id = 0;
  const newId = () => `n${String(id++).padStart(9, '0')}`;
  editor = new Editor({
    element: document.createElement('div'),
    extensions: [...schemaExtensions, BlockBehavior.configure({ newId })],
    content: blocksToDoc(blocks, newId),
  });
  return editor;
}
function paste(current: Editor, text: string, html = '') {
  const data = {
    getData: (type: string) => (type === 'text/plain' ? text : type === 'text/html' ? html : ''),
  };
  const event = new Event('paste');
  Object.defineProperty(event, 'clipboardData', { value: data });
  return pasteStructuredText(current.view, event as ClipboardEvent);
}
function saved(current: Editor) {
  return toSavableBlocks(
    docToDrafts(current.getJSON(), () => 'missing'),
    [],
  );
}
afterEach(() => {
  editor?.destroy();
  editor = null;
});

describe('paste in the editor', () => {
  it('retains Korean composition and the surrounding block during inline paste', () => {
    const current = open([{ id: 'para000001', type: 'paragraph', text: '한글' }]);
    Object.defineProperty(current.view, 'composing', { value: true, configurable: true });
    expect(paste(current, '**입력**')).toBe(false);
    expect(saved(current)[0]).toMatchObject({ id: 'para000001', text: '한글' });
    Object.defineProperty(current.view, 'composing', { value: false, configurable: true });
    current.view.dispatch(current.state.tr.setSelection(TextSelection.create(current.state.doc, 3)));
    expect(paste(current, '**추가**')).toBe(true);
    expect(saved(current)[0]).toMatchObject({ id: 'para000001', text: '한글**추가**' });
  });

  it('overlays and expands a table while retaining existing block and cell identities', () => {
    const current = open([
      {
        id: 'table00001',
        type: 'table',
        columns: [{ id: 'col0000001', align: 'right' }],
        rows: [{ id: 'row0000001', cells: ['전'] }],
      },
    ]);
    current.view.dispatch(current.state.tr.setSelection(TextSelection.create(current.state.doc, 3)));
    expect(paste(current, '새\t열\n다음\t행')).toBe(true);
    expect(saved(current)[0]).toMatchObject({
      id: 'table00001',
      columns: [{ id: 'col0000001', align: 'right' }, { align: 'none' }],
      rows: [{ id: 'row0000001', cells: ['새', '열'] }, { cells: ['다음', '행'] }],
    });
  });

  it('leaves rich HTML and code blocks to native paste', () => {
    const current = open([{ id: 'code000001', type: 'code', language: '', code: '한글' }]);
    expect(paste(current, '# 제목')).toBe(false);
    expect(saved(current)[0]).toMatchObject({ id: 'code000001', code: '한글' });
    const paragraph = open([{ id: 'para000001', type: 'paragraph', text: '한글' }]);
    expect(paste(paragraph, '**서식**', '<b>서식</b>')).toBe(false);
    expect(saved(paragraph)[0]).toMatchObject({ id: 'para000001', text: '한글' });
    current.destroy();
  });
});
