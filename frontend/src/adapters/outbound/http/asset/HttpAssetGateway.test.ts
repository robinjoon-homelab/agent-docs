import { expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpAssetGateway } from './HttpAssetGateway';

it('resolves immutable asset references in the document context and preserves standalone URLs', async () => {
  const gateway = new HttpAssetGateway(
    new HttpClient({
      cookies: () => '',
      fetch: (input) => {
        const source =
          input === '/api/documents/moved/assets/original/source'
            ? '<p>destination binding</p>'
            : '<p>standalone</p>';
        return Promise.resolve(
          new Response(JSON.stringify({ assetId: 'original', source }), {
            headers: { 'content-type': 'application/json' },
          }),
        );
      },
    }),
  );
  expect(gateway.contentUrl('original', 'moved')).toBe('/api/documents/moved/assets/original/content');
  expect(gateway.contentUrl('original')).toBe('/api/assets/original/content');
  expect(await gateway.htmlSource('original', 'moved')).toBe('<p>destination binding</p>');
  expect(await gateway.htmlSource('original')).toBe('<p>standalone</p>');
});

it('reads a drawing scene through the document that shows it and returns the stored JSON', async () => {
  const scene = '{"type":"excalidraw","elements":[]}';
  const gateway = new HttpAssetGateway(
    new HttpClient({
      cookies: () => '',
      fetch: (input) =>
        Promise.resolve(
          input === '/api/documents/doc-1/assets/scene-1/source'
            ? new Response(
                JSON.stringify({
                  assetId: 'scene-1',
                  mediaType: 'application/vnd.excalidraw+json',
                  source: scene,
                  texts: [],
                }),
                {
                  headers: { 'content-type': 'application/json' },
                },
              )
            : new Response('{}', { status: 404, headers: { 'content-type': 'application/json' } }),
        ),
    }),
  );
  expect(await gateway.sceneSource('scene-1', 'doc-1')).toBe(scene);
});
