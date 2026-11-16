import { describe, expect, it } from 'vitest';
import { HttpPortabilityGateway } from './HttpPortabilityGateway';
import { HttpClient } from '../shared/httpClient';
import { ExportIncompleteError } from '../../../../application/shared/ports/errors';

describe('portability HTTP gateway', () => {
  it('reports the first failed attachment without returning partial ZIP bytes', async () => {
    const http = new HttpClient({ cookies: () => '', fetch: () => Promise.resolve(new Response()) });
    const failedAsset = { id: 'first-asset', sha256: 'a'.repeat(64) };
    const gateway = new HttpPortabilityGateway(http, () =>
      Promise.resolve(
        new Response(
          JSON.stringify({
            code: 'export_incomplete',
            detail: 'export could not include all attachment originals',
            fix: 'restore a verified original; retain metadata and history',
            failedAsset,
            scanComplete: false,
          }),
          { status: 500, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    );
    const failure: unknown = await gateway.exportZip('docs').catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(ExportIncompleteError);
    expect(failure).toMatchObject({
      code: 'export_incomplete',
      status: 500,
      message: 'export could not include all attachment originals',
      fix: 'restore a verified original; retain metadata and history',
      failedAsset,
      scanComplete: false,
    });
  });

  it('keeps an incomplete problem even when attachment context is malformed', async () => {
    const http = new HttpClient({ cookies: () => '', fetch: () => Promise.resolve(new Response()) });
    const gateway = new HttpPortabilityGateway(http, () =>
      Promise.resolve(
        new Response(JSON.stringify({ code: 'export_incomplete', failedAsset: null }), {
          status: 500,
          headers: { 'content-type': 'application/problem+json' },
        }),
      ),
    );
    await expect(gateway.exportZip(null)).rejects.toMatchObject({ code: 'export_incomplete', status: 500 });
  });

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
