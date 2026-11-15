import { describe, expect, it } from 'vitest';
import { HttpPortabilityGateway } from './HttpPortabilityGateway';
import { HttpClient } from '../shared/httpClient';

describe('portability HTTP gateway', () => {
  it('returns per-file outcomes from a multipart import', async () => {
    const http = new HttpClient({
      cookies: () => '',
      fetch: () =>
        Promise.resolve(
          new Response(
            JSON.stringify({
              files: [
                {
                  path: 'a.md',
                  status: 'failed',
                  documentId: null,
                  code: 'missing_image',
                  relatedPath: null,
                },
              ],
            }),
            { headers: { 'content-type': 'application/json' } },
          ),
        ),
    });
    const gateway = new HttpPortabilityGateway(http, () => Promise.resolve(new Response()));
    const result = await gateway.importFolder('docs', 'Markdown', [
      { path: 'a.md', type: 'text/markdown', bytes: new TextEncoder().encode('hello').buffer },
    ]);
    expect(result.files).toEqual([
      { path: 'a.md', status: 'failed', documentId: null, code: 'missing_image', relatedPath: null },
    ]);
  });
  it('preserves exported ZIP bytes and rejects forbidden exports', async () => {
    const http = new HttpClient({ cookies: () => '', fetch: () => Promise.resolve(new Response()) });
    const gateway = new HttpPortabilityGateway(http, () =>
      Promise.resolve(new Response(new Uint8Array([80, 75]))),
    );
    expect(new Uint8Array(await gateway.exportZip(null))).toEqual(new Uint8Array([80, 75]));
    const forbidden = new HttpPortabilityGateway(http, () =>
      Promise.resolve(
        new Response('{}', { status: 403, headers: { 'content-type': 'application/problem+json' } }),
      ),
    );
    await expect(forbidden.exportZip('docs')).rejects.toMatchObject({ status: 403 });
  });
});
