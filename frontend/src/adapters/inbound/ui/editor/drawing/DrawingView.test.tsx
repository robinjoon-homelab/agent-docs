// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import type { DrawingOptions } from './DrawingView';
import { DrawingPicture } from './DrawingView';

afterEach(cleanup);

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
