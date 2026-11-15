import { describe, expect, it } from 'vitest';
import { linkedRepositories } from './space';

describe('linked repositories', () => {
  it('keeps repository links only, so an organization never stands for a guessed repository', () => {
    const links = [
      { kind: 'github_organization' as const, value: 'homelab' },
      { kind: 'github_repository' as const, value: 'homelab/agent-docs' },
      { kind: 'url' as const, value: 'https://example.com' },
      { kind: 'github_repository' as const, value: 'homelab/infra' },
    ];

    expect(linkedRepositories({ links })).toEqual(['homelab/agent-docs', 'homelab/infra']);
  });
});
