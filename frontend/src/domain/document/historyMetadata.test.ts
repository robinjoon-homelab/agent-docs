import { describe, expect, it } from 'vitest';
import { codeLinks } from './historyMetadata';

describe('revision code links', () => {
  it('builds commit and pull request URLs from values', () => {
    expect(codeLinks({ repository: 'owner/repo', commit: 'a'.repeat(40), pullRequest: 9 })).toEqual([
      { label: 'owner/repo@aaaaaaa', href: `https://github.com/owner/repo/commit/${'a'.repeat(40)}` },
      { label: 'owner/repo#9', href: 'https://github.com/owner/repo/pull/9' },
    ]);
  });
  it('does not turn an untrusted repository or sha into an external URL', () => {
    expect(codeLinks({ repository: 'https://evil.test/a', commit: 'a'.repeat(40), pullRequest: 1 })).toEqual(
      [],
    );
    expect(codeLinks({ repository: 'owner/repo', commit: '../evil', pullRequest: -1 })).toEqual([]);
  });
});
