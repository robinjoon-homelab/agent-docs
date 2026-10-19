import { describe, expect, it } from 'vitest';
import { weekBars } from './usage';

describe('weekBars', () => {
  it('scales every bar against the busiest value of any week', () => {
    expect(
      weekBars([
        { weekStart: '2026-10-05', documents: 2, comments: 8 },
        { weekStart: '2026-10-12', documents: 4, comments: 0 },
      ]),
    ).toEqual([
      { weekStart: '2026-10-05', documents: 2, comments: 8, documentsRatio: 0.25, commentsRatio: 1 },
      { weekStart: '2026-10-12', documents: 4, comments: 0, documentsRatio: 0.5, commentsRatio: 0 },
    ]);
  });

  it('gives empty bars when nothing happened', () => {
    expect(weekBars([{ weekStart: '2026-10-05', documents: 0, comments: 0 }])).toEqual([
      { weekStart: '2026-10-05', documents: 0, comments: 0, documentsRatio: 0, commentsRatio: 0 },
    ]);
  });
});
