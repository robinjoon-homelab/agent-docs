import { describe, expect, it } from 'vitest';
import { filterMentionLogins, mentionAtCursor } from './mentions';

describe('mention completion', () => {
  it('matches login prefixes without case sensitivity and keeps the first eight candidates', () => {
    const logins = ['Robin', 'rob', 'other', ...Array.from({ length: 9 }, (_, index) => `ro-${index}`)];
    expect(filterMentionLogins(logins, 'RO')).toEqual([
      'Robin',
      'rob',
      'ro-0',
      'ro-1',
      'ro-2',
      'ro-3',
      'ro-4',
      'ro-5',
    ]);
    expect(filterMentionLogins(logins, 'missing')).toEqual([]);
    expect(filterMentionLogins(logins, '')).toEqual(logins.slice(0, 8));
  });

  it('finds the mention at the cursor and includes the rest of its login in the replacement', () => {
    expect(mentionAtCursor('Hello @robin, thanks', 9, 9)).toEqual({ from: 6, to: 12, query: 'ro' });
    expect(mentionAtCursor('Hi\n@', 4, 4)).toEqual({ from: 3, to: 4, query: '' });
    expect(mentionAtCursor('@ro-bin', 7, 7)).toEqual({ from: 0, to: 7, query: 'ro-bin' });
  });

  it('does not complete email addresses, selected text, or text after a finished mention', () => {
    expect(mentionAtCursor('me@robin', 8, 8)).toBeNull();
    expect(mentionAtCursor('@robin', 1, 6)).toBeNull();
    expect(mentionAtCursor('@robin ', 7, 7)).toBeNull();
  });
});
