export function filterMentionLogins(logins: readonly string[], query: string): string[] {
  return logins.filter((login) => login.toLowerCase().startsWith(query.toLowerCase())).slice(0, 8);
}

export type MentionQuery = { readonly from: number; readonly to: number; readonly query: string };

export function mentionAtCursor(body: string, start: number, end: number): MentionQuery | null {
  if (start !== end) return null;
  const match = /(?:^|\s)@([a-z\d-]*)$/i.exec(body.slice(0, start));
  const query = match?.[1];
  if (query === undefined) return null;
  const suffix = /^[a-z\d-]*/i.exec(body.slice(start))?.[0] ?? '';
  return { from: start - query.length - 1, to: start + suffix.length, query };
}
