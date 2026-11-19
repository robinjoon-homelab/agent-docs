import { describe, expect, it } from 'vitest';
import { document, paragraph, stored } from '../../../../test-support/fixtures';
import { MemoryStore } from '../shared/store';
import { MemorySearchGateway } from './MemorySearchGateway';

describe('MemorySearchGateway', () => {
  it('includes authorized archived spaces in global search and Cmd+K titles while excluding inaccessible ones', async () => {
    const store = new MemoryStore({
      userId: 'u-member',
      login: 'bob',
      name: 'Bob',
      avatarUrl: '',
      owner: false,
    });
    store.spaces = [
      {
        id: 'sp-allowed',
        slug: 'archived-allowed',
        name: '보관 허용',
        description: '',
        links: [],
        createdAt: '2026-01-01T00:00:00Z',
        archivedAt: '2026-02-01T00:00:00Z',
        lastActivityAt: null,
        documentCount: 1,
        permission: 'read',
      },
      {
        id: 'sp-hidden',
        slug: 'archived-hidden',
        name: '보관 비공개',
        description: '',
        links: [],
        createdAt: '2026-01-01T00:00:00Z',
        archivedAt: '2026-02-01T00:00:00Z',
        lastActivityAt: null,
        documentCount: 1,
      },
    ];
    store.documents.set(
      'doc-allowed',
      document({
        id: 'doc-allowed',
        space: { id: 'sp-allowed', slug: 'archived-allowed', name: '보관 허용' },
        title: '보관 안내서',
        path: 'guides/archived',
        blocks: [stored(paragraph('p1', '보관된 스페이스 본문 키워드'))],
      }),
    );
    store.documents.set(
      'doc-hidden',
      document({
        id: 'doc-hidden',
        space: { id: 'sp-hidden', slug: 'archived-hidden', name: '보관 비공개' },
        title: '보관 안내서 비밀',
        path: 'secret',
        blocks: [stored(paragraph('p2', '보관된 스페이스 본문 키워드'))],
      }),
    );

    const gateway = new MemorySearchGateway(store);
    const hits = await gateway.search('키워드');
    const titles = await gateway.titles('보관 안내서');

    expect(hits.map((hit) => hit.documentId)).toEqual(['doc-allowed']);
    expect(titles.map((hit) => hit.documentId)).toEqual(['doc-allowed']);
  });
});
