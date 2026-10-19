import { describe, expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpConnectionGateway, HttpOAuthRequestGateway } from './HttpConnectionGateway';

function client(body: unknown, status = 200): HttpClient {
  const fetch = (): Promise<Response> =>
    Promise.resolve(
      new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } }),
    );
  return new HttpClient({ fetch, cookies: () => '' });
}

describe('connection gateways', () => {
  it('maps an issued script token', async () => {
    const issued = await new HttpConnectionGateway(
      client(
        {
          connection: {
            id: 'c1',
            kind: 'script',
            name: 'ci',
            clientId: null,
            clientHost: null,
            spaceIds: ['s1'],
            scope: 'comment',
            createdAt: '2026-10-18T00:00:00Z',
            lastUsedAt: null,
            expiresAt: '2027-01-16T00:00:00Z',
            revokedAt: null,
          },
          token: 'adt_secret',
        },
        201,
      ),
    ).issueScriptToken({ name: 'ci', spaceIds: ['s1'], scope: 'comment', expiresInDays: 90 });
    expect(issued.token).toBe('adt_secret');
    expect(issued.connection).toMatchObject({ kind: 'script', scope: 'comment', spaceIds: ['s1'] });
  });

  it('maps an OAuth request and the approval redirect', async () => {
    const request = await new HttpOAuthRequestGateway(
      client({
        id: 'r1',
        client: {
          id: 'https://claude.ai/oauth/metadata',
          name: 'Claude',
          host: 'claude.ai',
          kind: 'metadata_document',
        },
        redirectHost: 'claude.ai',
        requestedScope: null,
        expiresAt: '2026-10-18T00:10:00Z',
      }),
    ).get('r1');
    expect(request).toMatchObject({
      client: { host: 'claude.ai', kind: 'metadata_document' },
      requestedScope: null,
    });
    const registered = await new HttpOAuthRequestGateway(
      client({
        id: 'r2',
        client: { id: 'adc_123', name: 'My script', host: null, kind: 'registered' },
        redirectHost: '127.0.0.1',
        requestedScope: 'read',
        expiresAt: '2026-10-18T00:10:00Z',
      }),
    ).get('r2');
    expect(registered.client).toEqual({ id: 'adc_123', name: 'My script', host: null, kind: 'registered' });
    const redirect = await new HttpOAuthRequestGateway(
      client({ redirectTo: 'https://claude.ai/cb?code=1' }),
    ).deny('r1');
    expect(redirect).toBe('https://claude.ai/cb?code=1');
  });
});
