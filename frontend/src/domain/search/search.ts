import { documentHref, threadHref } from '../document/document';

export type SearchScope = { readonly kind: 'all' } | { readonly kind: 'space'; readonly slug: string };

export type HitKind = 'title' | 'block' | 'comment';

export type SearchHit = {
  readonly kind: HitKind;
  readonly space: { readonly slug: string; readonly name: string };
  readonly documentId: string;
  readonly path: string;
  readonly title: string;
  readonly blockId: string | null;
  readonly threadId: string | null;
  readonly snippet: string;
};

export type TitleHit = {
  readonly space: { readonly slug: string; readonly name: string };
  readonly documentId: string;
  readonly path: string;
  readonly title: string;
};

export const HIT_KIND_LABEL: Record<HitKind, string> = { title: '제목', block: '본문', comment: '댓글' };

export const MIN_QUERY_LENGTH = 1;

export function normalizeQuery(query: string): string {
  return query.trim().replace(/\s+/g, ' ');
}

export function hitHref(hit: SearchHit): string {
  if (hit.threadId !== null) return threadHref(hit.space.slug, hit.documentId, hit.threadId);
  const base = documentHref(hit.space.slug, hit.documentId);
  return hit.blockId === null ? base : `${base}#block-${encodeURIComponent(hit.blockId)}`;
}

export type SnippetPart = { readonly text: string; readonly match: boolean };

/** Splits a snippet around case-insensitive occurrences of the query terms for highlighting. */
export function highlightSnippet(snippet: string, query: string): SnippetPart[] {
  const terms = normalizeQuery(query)
    .split(' ')
    .filter((term) => term !== '')
    .map((term) => term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  if (terms.length === 0) return [{ text: snippet, match: false }];
  const pattern = new RegExp(`(${terms.join('|')})`, 'gi');
  return snippet
    .split(pattern)
    .filter((part) => part !== '')
    .map((part) => ({ text: part, match: terms.some((term) => new RegExp(`^${term}$`, 'i').test(part)) }));
}
