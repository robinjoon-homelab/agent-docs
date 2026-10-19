import { describe, expect, it } from 'vitest';
import type { InboxItem } from '../../domain/inbox/inbox';
import { agent } from '../../test-support/fixtures';
import { createInboxUseCases } from './inboxUseCases';
import type { InboxGateway } from './ports/InboxGateway';

function item(overrides: Partial<InboxItem>): InboxItem {
  return {
    id: 'i1',
    kind: 'reply',
    space: { slug: 'team', name: 'Team' },
    documentId: 'doc-1',
    documentTitle: 'Doc',
    threadId: 'th-1',
    commentId: 'cm-1',
    actor: agent(),
    excerpt: '수정했습니다',
    createdAt: '2026-10-18T09:00:00Z',
    readAt: null,
    ...overrides,
  };
}

/** Inbox server whose read state is the observable result. */
function fakeInbox(items: InboxItem[]): InboxGateway & { items: InboxItem[] } {
  const server = {
    items,
    list: (unreadOnly: boolean) =>
      Promise.resolve(server.items.filter((i) => !unreadOnly || i.readAt === null)),
    unreadCount: () => Promise.resolve(server.items.filter((i) => i.readAt === null).length),
    markRead: (id: string) => {
      server.items = server.items.map((i) => (i.id === id ? { ...i, readAt: '2026-10-18T10:00:00Z' } : i));
      return Promise.resolve();
    },
    markAllRead: () => Promise.resolve(),
  };
  return server;
}

describe('inbox use cases', () => {
  it('opening an item marks it read and returns the thread anchor link', async () => {
    const server = fakeInbox([item({})]);
    const href = await createInboxUseCases(server).open(item({}));
    expect(href).toBe('/s/team/d/doc-1#thread-th-1');
    expect(await server.unreadCount()).toBe(0);
  });

  it('lists newest first', async () => {
    const server = fakeInbox([item({ id: 'old', createdAt: '2026-10-01T00:00:00Z' }), item({ id: 'new' })]);
    expect((await createInboxUseCases(server).list(false)).map((i) => i.id)).toEqual(['new', 'old']);
  });
});
