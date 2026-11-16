import { HttpClient } from '../shared/httpClient';
import { HttpSpaceGateway } from './HttpSpaceGateway';
import { describe, expect, it } from 'vitest';
import { toSpace } from './HttpSpaceGateway';

const base = {
  id: 'sp',
  slug: 'agent-docs',
  name: 'Agent Docs',
  description: '',
  links: [{ kind: 'github_repository', value: 'homelab/agent-docs' }],
  createdAt: '2026-10-18T00:00:00Z',
  archivedAt: null,
};

describe('space JSON', () => {
  it('reads the offered agent guide and the default repository', () => {
    const space = toSpace({
      ...base,
      defaultRepository: 'homelab/agent-docs',
      agentGuide: { documentId: 'doc-guide', title: '작성 규칙', path: 'guide' },
      agentGuideIssue: null,
    });

    expect(space.agentGuide).toEqual({ documentId: 'doc-guide', title: '작성 규칙', path: 'guide' });
    expect(space.agentGuideIssue).toBeNull();
    expect(space.defaultRepository).toBe('homelab/agent-docs');
  });

  it('reads a withheld guide as its issue, and older answers without these fields as none', () => {
    expect(toSpace({ ...base, agentGuide: null, agentGuideIssue: 'unavailable' })).toMatchObject({
      agentGuide: null,
      agentGuideIssue: 'unavailable',
    });
    expect(toSpace(base)).toMatchObject({ agentGuide: null, agentGuideIssue: null, defaultRepository: null });
  });
});

it('reads the space grant and excludes read-only permission from setting requests', async () => {
  const space = {
    id: 'uuid',
    slug: 'team',
    name: 'Team',
    description: '',
    links: [],
    createdAt: '2026-11-16T00:00:00Z',
    permission: 'comment',
  };
  const gateway = new HttpSpaceGateway(
    new HttpClient({
      cookies: () => '',
      fetch: (_url, init) => {
        if (init?.method === 'PATCH') {
          if (typeof init.body !== 'string') return Promise.reject(new Error('JSON body required'));
          const payload: unknown = JSON.parse(init.body);
          expect(payload).not.toHaveProperty('permission');
        }
        return Promise.resolve(
          new Response(JSON.stringify(space), { headers: { 'Content-Type': 'application/json' } }),
        );
      },
    }),
  );
  expect((await gateway.get('team')).permission).toBe('comment');
  expect(
    (await gateway.update('team', { name: 'Team', permission: 'owner' } as { name: string })).permission,
  ).toBe('comment');
});
