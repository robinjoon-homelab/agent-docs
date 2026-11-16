import { describe, expect, it } from 'vitest';
import { ApiError } from '../../../../application/shared/ports/errors';
import type { FetchLike } from './httpClient';
import { HttpClient } from './httpClient';

function json(status: number, body: unknown, type = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': type } });
}

/** Behaves like Spring Security's CookieCsrfTokenRepository: mutating requests need the cookie value. */
const csrfServer: FetchLike = (_url, init) => {
  const method = init?.method ?? 'GET';
  const token = new Headers(init?.headers).get('X-XSRF-TOKEN');
  if (method !== 'GET' && token !== 'tok/en=') {
    return Promise.resolve(
      json(403, { code: 'forbidden', title: 'Forbidden', detail: 'CSRF 토큰이 없습니다.' }),
    );
  }
  return Promise.resolve(json(200, { method, body: init?.body ?? null }));
};

describe('HttpClient', () => {
  it('preserves an incomplete export problem and its first attachment context', async () => {
    const body = {
      code: 'export_incomplete',
      detail: 'export could not include all attachment originals',
      fix: 'restore a verified original; retain metadata and history',
      failedAsset: { id: 'first-asset', sha256: 'a'.repeat(64) },
      scanComplete: false,
    };
    const client = new HttpClient({
      fetch: () => Promise.resolve(json(500, body, 'application/problem+json')),
      cookies: () => '',
    });
    await expect(client.get('/api/spaces/docs/export.zip')).rejects.toMatchObject({
      code: 'export_incomplete',
      status: 500,
      message: body.detail,
      fix: body.fix,
      body,
    });
  });

  it('sends the XSRF cookie value on mutating requests', async () => {
    const withCookie = new HttpClient({ fetch: csrfServer, cookies: () => 'a=1; XSRF-TOKEN=tok%2Fen%3D' });
    expect(await withCookie.post('/api/spaces', { name: 'x' })).toEqual({
      method: 'POST',
      body: '{"name":"x"}',
    });
    const withoutCookie = new HttpClient({ fetch: csrfServer, cookies: () => '' });
    await expect(withoutCookie.post('/api/spaces', {})).rejects.toMatchObject({
      code: 'forbidden',
      status: 403,
    });
  });

  it('keeps a bodyless finalize request alive with its CSRF and session credentials', async () => {
    const fetch: FetchLike = (_url, init) => {
      const valid =
        init?.method === 'POST' &&
        init.keepalive === true &&
        init.body === undefined &&
        init.credentials === 'same-origin' &&
        init.redirect === 'manual' &&
        new Headers(init.headers).get('X-XSRF-TOKEN') === 'tok/en=';
      return Promise.resolve(json(valid ? 200 : 403, valid ? { revision: 4 } : { code: 'forbidden' }));
    };
    const client = new HttpClient({ fetch, cookies: () => 'XSRF-TOKEN=tok%2Fen%3D' });
    expect(await client.postKeepalive('/api/documents/d1/finalize')).toEqual({ revision: 4 });
  });

  it('maps problem JSON to ApiError with detail and fix', async () => {
    const client = new HttpClient({
      fetch: () =>
        Promise.resolve(
          json(
            404,
            {
              type: 'about:blank',
              title: 'Not Found',
              status: 404,
              detail: '문서가 없습니다.',
              code: 'not_found',
              fix: '경로 확인',
            },
            'application/problem+json',
          ),
        ),
      cookies: () => '',
    });
    const error = await client.get('/api/documents/x').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      code: 'not_found',
      status: 404,
      message: '문서가 없습니다.',
      fix: '경로 확인',
    });
  });

  it('falls back to the status when the body is not a problem', async () => {
    const client = new HttpClient({
      fetch: () => Promise.resolve(new Response('', { status: 401 })),
      cookies: () => '',
    });
    await expect(client.get('/api/me')).rejects.toMatchObject({
      code: 'unauthenticated',
      message: '로그인이 필요합니다.',
    });
  });

  it('treats a redirect (Spring sending the browser to login) as an expired session', async () => {
    const client = new HttpClient({
      fetch: () =>
        Promise.resolve(
          new Response(null, { status: 302, headers: { location: '/oauth2/authorization/github' } }),
        ),
      cookies: () => '',
    });
    await expect(client.get('/api/me')).rejects.toMatchObject({ code: 'unauthenticated', status: 401 });
  });

  it('reports transport failures as network errors and empty bodies as undefined', async () => {
    const offline = new HttpClient({
      fetch: () => Promise.reject(new TypeError('Failed to fetch')),
      cookies: () => '',
    });
    await expect(offline.get('/api/me')).rejects.toMatchObject({ code: 'network' });
    const noContent = new HttpClient({
      fetch: () => Promise.resolve(new Response(null, { status: 204 })),
      cookies: () => '',
    });
    expect(await noContent.post('/api/inbox/read-all')).toBeUndefined();
  });
});
