// @vitest-environment jsdom
import { EditorContent, useEditor } from '@tiptap/react';
import type { Editor } from '@tiptap/react';
import { TextSelection } from '@tiptap/pm/state';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { Block } from '../../../../domain/document/block';
import { blocksToDoc } from './convert';
import { CodeBlockWithView, ImageWithView } from './nodeViews';
import { CodeBlockNode, ImageNode, schemaExtensions } from './schema';

afterEach(cleanup);

let current: Editor | null = null;

function Diagram({ code, editable }: { code: string; editable: boolean }) {
  const blocks: Block[] = [
    { id: 'p000000001', type: 'paragraph', text: 'before' },
    { id: 'k000000001', type: 'code', language: 'mermaid', code },
  ];
  const editor = useEditor({
    extensions: [...schemaExtensions.filter((extension) => extension !== CodeBlockNode), CodeBlockWithView],
    content: blocksToDoc(blocks, () => 'n000000001'),
    editable,
    onCreate: ({ editor: created }) => {
      current = created;
    },
  });
  return <EditorContent editor={editor} />;
}

function codeShown(): boolean {
  const pre = document.querySelector('.code-block pre');
  return pre !== null && !pre.classList.contains('code-hidden');
}

function previewShown(): boolean {
  return document.querySelector('.code-block-preview') !== null;
}

function pressed(label: string): boolean {
  return screen.getByRole('button', { name: label }).getAttribute('aria-pressed') === 'true';
}

describe('mermaid code block views', () => {
  it('opens a written diagram on its preview alone', async () => {
    render(<Diagram code={'graph TD\n  A-->B'} editable />);
    await screen.findByRole('group', { name: '다이어그램 보기' });

    expect(pressed('미리보기')).toBe(true);
    expect(codeShown()).toBe(false);
    expect(previewShown()).toBe(true);
  });

  it('opens an empty diagram with its code and preview together, ready to type', async () => {
    render(<Diagram code="" editable />);
    await screen.findByRole('group', { name: '다이어그램 보기' });

    expect(pressed('함께 보기')).toBe(true);
    expect(codeShown()).toBe(true);
    expect(previewShown()).toBe(true);
  });

  it('shows only the code when asked, in a read-only document too', async () => {
    render(<Diagram code={'graph TD\n  A-->B'} editable={false} />);
    fireEvent.click(await screen.findByRole('button', { name: '코드' }));

    expect(pressed('코드')).toBe(true);
    expect(codeShown()).toBe(true);
    expect(previewShown()).toBe(false);
  });

  it('reveals hidden code when the caret moves into it, so nobody types into text they cannot see', async () => {
    render(<Diagram code={'graph TD\n  A-->B'} editable />);
    await screen.findByRole('group', { name: '다이어그램 보기' });
    const editor = current;
    if (editor === null) throw new Error('editor not ready');

    act(() => {
      const inside = editor.state.doc.child(0).nodeSize + 2;
      editor.view.dispatch(editor.state.tr.setSelection(TextSelection.create(editor.state.doc, inside)));
    });

    expect(pressed('함께 보기')).toBe(true);
    expect(codeShown()).toBe(true);
  });
});

function Picture({ caption }: { caption: string }) {
  const blocks: Block[] = [{ id: 'i000000001', type: 'image', assetId: 'asset', alt: '', caption }];
  const editor = useEditor({
    extensions: [...schemaExtensions.filter((extension) => extension !== ImageNode), ImageWithView],
    content: blocksToDoc(blocks, () => 'n000000001'),
    onCreate: ({ editor: created }) => {
      current = created;
    },
  });
  return <EditorContent editor={editor} />;
}

function captionField(): Promise<HTMLInputElement> {
  return screen.findByRole<HTMLInputElement>('textbox', { name: '캡션' });
}

function documentCaption(): unknown {
  return current?.state.doc.firstChild?.attrs.caption;
}

describe('image fields', () => {
  it('keeps the text being typed in the field at once, or the browser drops the Korean letter being composed', async () => {
    render(<Picture caption="" />);
    const field = await captionField();

    fireEvent.change(field, { target: { value: 'ㅎ' } });

    expect(field.value).toBe('ㅎ');
  });

  it('writes what is typed into the document', async () => {
    render(<Picture caption="" />);
    const field = await captionField();

    await act(async () => {
      fireEvent.change(field, { target: { value: '한글' } });
      await Promise.resolve();
    });

    expect(field.value).toBe('한글');
    expect(documentCaption()).toBe('한글');
  });

  it('shows a caption the document got some other way, such as an undo', async () => {
    render(<Picture caption="처음" />);
    const field = await captionField();

    await act(async () => {
      current?.commands.command(({ tr, state }) => {
        tr.setNodeMarkup(0, undefined, { ...state.doc.firstChild?.attrs, caption: '바뀜' });
        return true;
      });
      await Promise.resolve();
    });

    expect(field.value).toBe('바뀜');
  });
});
