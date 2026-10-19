import { isSafeNext } from '../../../../domain/session/me';

const KEY = 'agent-docs:next';

/**
 * GitHub login is a full-page round trip, so the page to return to is kept in sessionStorage
 * instead of relying on the server to carry it.
 */
export function rememberNext(next: string | null): void {
  try {
    if (next !== null && isSafeNext(next)) sessionStorage.setItem(KEY, next);
    else sessionStorage.removeItem(KEY);
  } catch {
    // Storage can be unavailable (private mode); login still works, it just lands on home.
  }
}

export function takeNext(): string | null {
  try {
    const next = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return next !== null && isSafeNext(next) ? next : null;
  } catch {
    return null;
  }
}
