import { describe, expect, it } from 'vitest';
import { thread } from '../../../../../test-support/fixtures';
import { threadMarksInput } from './threadMarks';

describe('thread highlights', () => {
  const open = thread({ id: 'open', resolution: { state: 'attached', start: 1, end: 4 } });
  const answered = thread({ id: 'answered', status: 'answered' });
  const resolved = thread({ id: 'resolved', status: 'resolved' });

  it('highlights open and answered threads using their current ranges, excluding resolved ones', () => {
    expect(threadMarksInput([open, answered, resolved], null)).toEqual({
      marks: [
        { threadId: 'open', anchor: open.anchor, range: { start: 1, end: 4 } },
        { threadId: 'answered', anchor: answered.anchor, range: { start: 0, end: 5 } },
      ],
      activeId: null,
    });
  });

  it('keeps a selected resolved thread highlighted until another thread is selected', () => {
    const threads = [open, resolved];
    expect(threadMarksInput(threads, 'resolved')).toEqual({
      marks: [
        { threadId: 'open', anchor: open.anchor, range: { start: 1, end: 4 } },
        { threadId: 'resolved', anchor: resolved.anchor, range: { start: 0, end: 5 } },
      ],
      activeId: 'resolved',
    });
    expect(threadMarksInput(threads, 'open').marks.map((mark) => mark.threadId)).toEqual(['open']);
  });
});
