import { threadHref } from '../document/document';
import type { Actor } from '../shared/actor';

export type InboxKind = 'mention' | 'reply' | 'assigned_comment';

export type InboxItem = {
  readonly id: string;
  readonly kind: InboxKind;
  readonly space: { readonly slug: string; readonly name: string };
  readonly documentId: string;
  readonly documentTitle: string;
  readonly threadId: string;
  readonly commentId: string;
  readonly actor: Actor;
  readonly excerpt: string;
  readonly createdAt: string;
  readonly readAt: string | null;
};

export const INBOX_KIND_LABEL: Record<InboxKind, string> = {
  mention: '멘션',
  reply: '내 스레드에 답글',
  assigned_comment: '담당 문서에 새 댓글',
};

export function isUnread(item: InboxItem): boolean {
  return item.readAt === null;
}

/** Opens the document scrolled to the thread anchor. */
export function inboxItemHref(item: InboxItem): string {
  return threadHref(item.space.slug, item.documentId, item.threadId);
}

export function markedRead(item: InboxItem, at: string): InboxItem {
  return item.readAt === null ? { ...item, readAt: at } : item;
}
