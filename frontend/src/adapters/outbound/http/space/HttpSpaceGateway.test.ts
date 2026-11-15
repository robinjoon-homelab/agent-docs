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
