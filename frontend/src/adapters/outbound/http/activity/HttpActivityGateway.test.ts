import { describe, expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpActivityGateway } from './HttpActivityGateway';

function gatewayReturning(body: unknown): HttpActivityGateway {
  const fetch = (): Promise<Response> =>
    Promise.resolve(
      new Response(JSON.stringify(body), { status: 200, headers: { 'content-type': 'application/json' } }),
    );
  return new HttpActivityGateway(new HttpClient({ fetch, cookies: () => '' }));
}

describe('HttpActivityGateway', () => {
  it('fetches a filtered page and accepts system activity without a login', async () => {
    const since = '2026-10-17T09:00:00Z';
    const fetch = (input: RequestInfo | URL): Promise<Response> => {
      const url = new URL(input instanceof Request ? input.url : input, 'http://localhost');
      const match =
        url.pathname === '/api/activity' &&
        url.searchParams.get('space') === 'agent-docs' &&
        url.searchParams.get('limit') === '20' &&
        url.searchParams.get('kind') === 'document.purged' &&
        url.searchParams.get('actorKind') === 'system' &&
        url.searchParams.get('since') === since;
      return Promise.resolve(
        Response.json(
          match
            ? [
                {
                  id: 42,
                  kind: 'document.purged',
                  actor: { kind: 'system', login: null, agent: null },
                  at: '2026-10-18T09:00:00Z',
                },
              ]
            : [],
        ),
      );
    };
    const gateway = new HttpActivityGateway(new HttpClient({ fetch, cookies: () => '' }));

    expect(
      await gateway.list('agent-docs', 20, { kind: 'document.purged', actorKind: 'system', since }),
    ).toMatchObject([{ id: '42', kind: 'document.purged', actor: { kind: 'system' } }]);
  });

  it('reads the activity log as the API documents it, with a numeric log id', async () => {
    const activity = await gatewayReturning([
      {
        id: 41,
        kind: 'document.revised',
        actor: {
          kind: 'agent',
          login: 'robinjoon',
          agent: { connectionId: '22222222-2222-4222-8222-222222222222', clientName: 'Claude Code' },
        },
        space: { id: '33333333-3333-4333-8333-333333333333', slug: 'agent-docs', name: 'Agent Docs' },
        documentId: '55555555-5555-4555-8555-555555555555',
        documentTitle: '재시도 설계',
        threadId: null,
        revision: 4,
        summary: '재시도 설계: 블록 1개 수정',
        at: '2026-10-18T09:00:00Z',
      },
    ]).list('agent-docs', 20);
    expect(activity).toEqual([
      {
        id: '41',
        kind: 'document.revised',
        actor: {
          kind: 'agent',
          login: 'robinjoon',
          agent: { connectionId: '22222222-2222-4222-8222-222222222222', clientName: 'Claude Code' },
        },
        space: { slug: 'agent-docs', name: 'Agent Docs' },
        documentId: '55555555-5555-4555-8555-555555555555',
        documentTitle: '재시도 설계',
        threadId: null,
        revision: 4,
        summary: '재시도 설계: 블록 1개 수정',
        at: '2026-10-18T09:00:00Z',
      },
    ]);
  });
});
