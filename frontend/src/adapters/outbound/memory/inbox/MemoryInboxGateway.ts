import type { InboxGateway } from '../../../../application/inbox/ports/InboxGateway';
import type { InboxItem } from '../../../../domain/inbox/inbox';
import { markedRead } from '../../../../domain/inbox/inbox';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemoryInboxGateway implements InboxGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(unreadOnly: boolean): Promise<InboxItem[]> {
    return respond(() => this.store.inbox.filter((item) => !unreadOnly || item.readAt === null));
  }

  unreadCount(): Promise<number> {
    return respond(() => this.store.inbox.filter((item) => item.readAt === null).length);
  }

  markRead(itemId: string): Promise<void> {
    return respond(() => {
      this.store.inbox = this.store.inbox.map((item) =>
        item.id === itemId ? markedRead(item, this.store.now()) : item,
      );
    });
  }

  markAllRead(): Promise<void> {
    return respond(() => {
      this.store.inbox = this.store.inbox.map((item) => markedRead(item, this.store.now()));
    });
  }
}
