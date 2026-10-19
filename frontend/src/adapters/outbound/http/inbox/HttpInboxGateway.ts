import type { InboxGateway } from '../../../../application/inbox/ports/InboxGateway';
import type { InboxItem } from '../../../../domain/inbox/inbox';
import type { HttpClient } from '../shared/httpClient';
import { query, segment } from '../shared/httpClient';
import { list, num, obj, oneOf, optStr, str, strOr } from '../shared/json';
import { toActor, toSpaceName } from '../shared/mappers';

export function toInboxItem(value: unknown): InboxItem {
  const source = obj(value, 'inboxItem');
  return {
    id: str(source, 'id'),
    kind: oneOf(source, 'kind', ['mention', 'reply', 'assigned_comment'] as const),
    space: toSpaceName(source.space),
    documentId: str(source, 'documentId'),
    documentTitle: strOr(source, 'documentTitle', ''),
    threadId: str(source, 'threadId'),
    commentId: strOr(source, 'commentId', ''),
    actor: toActor(source.actor),
    excerpt: strOr(source, 'excerpt', ''),
    createdAt: str(source, 'createdAt'),
    readAt: optStr(source, 'readAt'),
  };
}

export class HttpInboxGateway implements InboxGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async list(unreadOnly: boolean): Promise<InboxItem[]> {
    return list(await this.http.get(`/api/inbox${query({ unread: unreadOnly })}`), toInboxItem);
  }

  async unreadCount(): Promise<number> {
    return num(obj(await this.http.get('/api/inbox/count')), 'unread');
  }

  async markRead(itemId: string): Promise<void> {
    await this.http.post(`/api/inbox/${segment(itemId)}/read`);
  }

  async markAllRead(): Promise<void> {
    await this.http.post('/api/inbox/read-all');
  }
}
