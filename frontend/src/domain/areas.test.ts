import { describe, expect, it } from 'vitest';
import { human } from '../test-support/fixtures';
import { activityHref } from './activity/activity';
import type { Connection } from './connection/connection';
import { grantedSpaceNames, validateApproval, validateScriptToken } from './connection/connection';
import { weeklyTotals } from './metrics/usage';
import { highlightSnippet } from './search/search';
import { loginHref } from './session/me';
import { normalizeSpaceDraft, suggestSlug, validateSpaceDraft } from './space/space';

describe('space drafts', () => {
  it('suggests a slug from a latin name within 50 chars and without a trailing hyphen', () => {
    expect(suggestSlug('  Agent Docs: Backend!  ')).toBe('agent-docs-backend');
    expect(suggestSlug(`${'a'.repeat(49)} b`)).toBe('a'.repeat(49));
  });

  it('accepts slugs up to 50 chars and local paths starting with /', () => {
    const draft = { slug: 'a'.repeat(50), name: 'n', description: '', links: [] };
    expect(validateSpaceDraft(draft)).toEqual([]);
    expect(validateSpaceDraft({ ...draft, slug: 'a'.repeat(51) })).toEqual(['slug_invalid']);
    const local = (value: string) => ({ ...draft, links: [{ kind: 'local_path' as const, value }] });
    expect(validateSpaceDraft(local('/Users/me/code/agent-docs'))).toEqual([]);
    expect(validateSpaceDraft(local('~/code/agent-docs'))).toEqual(['link_invalid']);
    expect(validateSpaceDraft(local('code/agent-docs'))).toEqual(['link_invalid']);
    expect(normalizeSpaceDraft(local(' /srv/docs/ ')).links).toEqual([
      { kind: 'local_path', value: '/srv/docs' },
    ]);
  });

  it('reports every invalid field', () => {
    expect(
      validateSpaceDraft({
        slug: '-bad',
        name: ' ',
        description: '',
        links: [{ kind: 'github_repository', value: 'not a repo' }],
      }),
    ).toEqual(['slug_invalid', 'name_required', 'link_invalid']);
    expect(
      validateSpaceDraft({
        slug: 'agent-docs',
        name: '에이전트 문서',
        description: '',
        links: [{ kind: 'github_repository', value: 'robinjoon/agent-docs' }],
      }),
    ).toEqual([]);
  });
});

describe('connection grants', () => {
  it('requires at least one space and a sane expiry', () => {
    expect(validateApproval({ spaceIds: [], scope: 'read', expiresInDays: 0 })).toEqual([
      'no_spaces',
      'expiry_invalid',
    ]);
    expect(validateScriptToken({ name: ' ', spaceIds: ['s'], scope: 'edit', expiresInDays: 90 })).toEqual([
      'name_required',
    ]);
  });
});

describe('grantedSpaceNames', () => {
  it('joins granted space ids with the space list and marks ids it cannot resolve', () => {
    const connection: Connection = {
      id: 'c1',
      kind: 'script',
      name: 'ci',
      clientId: null,
      clientHost: null,
      spaceIds: ['s2', 'gone', 's1'],
      scope: 'read',
      createdAt: '2026-10-01T00:00:00Z',
      lastUsedAt: null,
      expiresAt: '2027-01-01T00:00:00Z',
      revokedAt: null,
    };
    const spaces = [
      { id: 's1', name: 'Team' },
      { id: 's2', name: '개인' },
    ];
    expect(grantedSpaceNames(connection, spaces)).toEqual([
      '개인',
      '삭제되었거나 볼 수 없는 스페이스',
      'Team',
    ]);
  });
});

describe('activityHref', () => {
  const base = {
    id: 'a',
    kind: 'comment',
    actor: human(),
    space: { slug: 'team', name: 'Team' },
    documentId: 'doc-1',
    documentTitle: 'Doc',
    threadId: null,
    revision: null,
    summary: '',
    at: '2026-10-18T00:00:00Z',
  };

  it('points to the most specific place', () => {
    expect(activityHref({ ...base, threadId: 'th-1', revision: 3 })).toBe('/s/team/d/doc-1#thread-th-1');
    expect(activityHref({ ...base, revision: 3 })).toBe('/s/team/d/doc-1/history?rev=3');
    expect(activityHref(base)).toBe('/s/team/d/doc-1');
    expect(activityHref({ ...base, space: null })).toBeNull();
  });
});

describe('highlightSnippet', () => {
  it('marks case-insensitive matches of each term', () => {
    expect(highlightSnippet('Deploy the API (v2)', 'api (v2')).toEqual([
      { text: 'Deploy the ', match: false },
      { text: 'API', match: true },
      { text: ' ', match: false },
      { text: '(v2', match: true },
      { text: ')', match: false },
    ]);
  });
});

describe('loginHref', () => {
  it('keeps same-origin next paths only', () => {
    expect(loginHref('/oauth/authorize?request=1')).toBe('/login?next=%2Foauth%2Fauthorize%3Frequest%3D1');
    expect(loginHref('//evil.example')).toBe('/login');
  });
});

describe('weeklyTotals', () => {
  it('sums spaces per week in date order', () => {
    expect(
      weeklyTotals({
        agentResponseMinutesMedian: null,
        revisitedDocuments: 0,
        weeks: [
          { weekStart: '2026-10-12', spaces: [{ slug: 'a', name: 'A', documents: 2, comments: 3 }] },
          {
            weekStart: '2026-10-05',
            spaces: [
              { slug: 'a', name: 'A', documents: 1, comments: 0 },
              { slug: 'b', name: 'B', documents: 4, comments: 1 },
            ],
          },
        ],
      }),
    ).toEqual([
      { weekStart: '2026-10-05', documents: 5, comments: 1 },
      { weekStart: '2026-10-12', documents: 2, comments: 3 },
    ]);
  });
});
