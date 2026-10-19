import type { InboxItem } from '../../../domain/inbox/inbox';

export interface InboxGateway {
  list(unreadOnly: boolean): Promise<InboxItem[]>;
  unreadCount(): Promise<number>;
  markRead(itemId: string): Promise<void>;
  markAllRead(): Promise<void>;
}
