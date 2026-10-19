import type { InboxItem } from '../../domain/inbox/inbox';
import { inboxItemHref, isUnread } from '../../domain/inbox/inbox';
import type { InboxGateway } from './ports/InboxGateway';

export interface InboxUseCases {
  list(unreadOnly: boolean): Promise<InboxItem[]>;
  unreadCount(): Promise<number>;
  /** Marks the item read (if needed) and returns where to go: the document scrolled to the thread. */
  open(item: InboxItem): Promise<string>;
  markAllRead(): Promise<void>;
}

export function createInboxUseCases(gateway: InboxGateway): InboxUseCases {
  return {
    async list(unreadOnly) {
      const items = await gateway.list(unreadOnly);
      return [...items].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    },
    unreadCount: () => gateway.unreadCount(),
    async open(item) {
      if (isUnread(item)) await gateway.markRead(item.id);
      return inboxItemHref(item);
    },
    markAllRead: () => gateway.markAllRead(),
  };
}
