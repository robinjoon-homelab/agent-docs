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
