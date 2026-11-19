// @vitest-environment jsdom
import { EditorContent, useEditor } from '@tiptap/react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { blocksToDoc } from '../convert';
import { DrawingNode, schemaExtensions } from '../schema';
import type { DrawingOptions } from './DrawingView';
import { DrawingPicture, DrawingWithView } from './DrawingView';

afterEach(cleanup);

function DrawingBlockView({ alt, editable }: { alt: string; editable: boolean }) {
  const editor = useEditor({
    extensions: [
      ...schemaExtensions.filter((extension) => extension !== DrawingNode),
      DrawingWithView.configure(options(false, 'blob:local-svg')),
    ],
    content: blocksToDoc(
      [{ id: 'd000000001', type: 'drawing', svgAssetId: 'svg-1', sceneAssetId: 'scene-1', alt }],
      () => 'n000000001',
    ),
    editable,
  });
  return <EditorContent editor={editor} />;
}

function options(saved: boolean, local: string | null): DrawingOptions {
  return {
    contentUrl: (assetId) => `/api/documents/doc-1/assets/${assetId}/content`,
    savedReferences: { subscribe: () => () => undefined, contains: () => saved },
    localPreview: () => local,
    openDrawing: () => undefined,
  };
}

it('shows a saved drawing through its document', () => {
  render(<DrawingPicture svgAssetId="svg-1" alt="흐름" options={options(true, null)} />);
  expect(screen.getByRole('img', { name: '흐름' }).getAttribute('src')).toBe(
    '/api/documents/doc-1/assets/svg-1/content',
  );
});

it('shows an unsaved drawing from this tab without asking the document for it', () => {
  render(<DrawingPicture svgAssetId="svg-1" alt="흐름" options={options(false, 'blob:local-svg')} />);
  expect(screen.getByRole('img', { name: '흐름' }).getAttribute('src')).toBe('blob:local-svg');
});

it('waits for the save when neither the document nor this tab has the picture', () => {
  render(<DrawingPicture svgAssetId="svg-1" alt="흐름" options={options(false, null)} />);
  expect(screen.queryByRole('img')).toBeNull();
  expect(screen.getByRole('status').textContent).toBe('문서 저장 후 그림을 불러옵니다.');
});

it('renders existing drawing alt read-only for readers while keeping missing-alt warnings editor-only', async () => {
  render(<DrawingBlockView alt="시스템 구조" editable={false} />);
  await screen.findByText('시스템 구조');
  expect(screen.queryByRole('textbox', { name: '대체 텍스트' })).toBeNull();
  expect(screen.queryByText('대체 텍스트 없음')).toBeNull();
  cleanup();

  render(<DrawingBlockView alt="" editable={false} />);
  await waitFor(() => expect(document.querySelector('img.drawing-picture')).not.toBeNull());
  expect(screen.queryByText('대체 텍스트 없음')).toBeNull();
  cleanup();

  render(<DrawingBlockView alt="" editable />);
  await screen.findByText('대체 텍스트 없음');
});
