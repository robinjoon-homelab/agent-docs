import { describe, expect, it } from 'vitest';
import { human, thread } from '../../test-support/fixtures';
import { canEdit, canComment, canResolveThread, effectivePermission, permissionOf } from './permission';

describe('space permission', () => {
  it('keeps A edit, B comment and unknown spaces separate', () => {
    const grants = new Map([
      ['A', 'edit' as const],
      ['B', 'comment' as const],
    ]);
    expect(['A', 'B', 'unknown'].map((space) => canEdit(grants.get(space)))).toEqual([true, false, false]);
    expect(['A', 'B', 'unknown'].map((space) => canComment(grants.get(space)))).toEqual([true, true, false]);
    expect(effectivePermission(permissionOf('future-role'), true)).toBe('read');
    expect(effectivePermission(undefined, true)).toBe('owner');
    expect(effectivePermission(undefined, false)).toBe('read');
    expect(canEdit(null)).toBe(false);
  });
  it('allows commenters to resolve only their own human threads and editors to decide suggestions', () => {
    const own = thread({ author: human('me') });
    expect(canResolveThread('comment', own, 'me')).toBe(true);
    expect(canResolveThread('comment', own, 'other')).toBe(false);
    expect(canResolveThread('comment', { ...own, author: human('old-me'), own: true }, 'new-me')).toBe(true);
    expect(canResolveThread('comment', { ...own, own: false }, 'me')).toBe(false);
    expect(canResolveThread('edit', own, 'other')).toBe(true);
    expect(canResolveThread('read', own, 'me')).toBe(false);
    expect(canResolveThread(null, own, 'me')).toBe(false);
    expect(canResolveThread('owner', { ...own, status: 'resolved' }, 'me')).toBe(false);
    expect(canEdit('comment')).toBe(false);
  });
});
