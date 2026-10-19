export type Me = {
  readonly userId: string;
  readonly login: string;
  readonly name: string;
  readonly avatarUrl: string;
  readonly owner: boolean;
};

export const LOGIN_PATH = '/login';
export const GITHUB_AUTHORIZATION_PATH = '/oauth2/authorization/github';

/** Login URL that brings the user back to `next` (same-origin paths only, to avoid open redirects). */
export function loginHref(next: string | null): string {
  if (next === null || !isSafeNext(next)) return LOGIN_PATH;
  return `${LOGIN_PATH}?next=${encodeURIComponent(next)}`;
}

export function isSafeNext(next: string): boolean {
  return next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/\\');
}

export type LoginError = 'not_allowed' | 'unknown';

export function loginErrorOf(code: string | null): LoginError | null {
  if (code === null) return null;
  return code === 'not_allowed' ? 'not_allowed' : 'unknown';
}
