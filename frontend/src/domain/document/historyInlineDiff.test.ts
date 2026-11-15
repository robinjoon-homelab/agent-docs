import { describe, expect, it } from 'vitest';
import { historyInlineDiff } from './historyInlineDiff';

describe('historyInlineDiff', () => {
  it('keeps surrounding Korean text and shows the replaced word', () => {
    expect(historyInlineDiff('배포는 오늘 합니다', '배포는 내일 합니다')).toEqual([
      { kind: 'same', text: '배포는 ' },
      { kind: 'removed', text: '오늘' },
      { kind: 'added', text: '내일' },
      { kind: 'same', text: ' 합니다' },
    ]);
  });

  it('finds multiple edits and never splits an emoji surrogate pair', () => {
    const changes = historyInlineDiff('😀 a then b', '😃 A then B');
    expect(
      changes
        .filter((change) => change.kind !== 'added')
        .map((change) => change.text)
        .join(''),
    ).toBe('😀 a then b');
    expect(
      changes
        .filter((change) => change.kind !== 'removed')
        .map((change) => change.text)
        .join(''),
    ).toBe('😃 A then B');
    expect(changes).toContainEqual({ kind: 'same', text: ' then ' });
  });

  it('handles empty content, equal text and large replacements', () => {
    expect(historyInlineDiff('', '')).toEqual([]);
    expect(historyInlineDiff('같음', '같음')).toEqual([{ kind: 'same', text: '같음' }]);
    expect(historyInlineDiff('', '추가')).toEqual([{ kind: 'added', text: '추가' }]);
    expect(historyInlineDiff('삭제', '')).toEqual([{ kind: 'removed', text: '삭제' }]);
    expect(historyInlineDiff(`앞${'a'.repeat(501)}뒤`, `앞${'b'.repeat(501)}뒤`)).toEqual([
      { kind: 'same', text: '앞' },
      { kind: 'removed', text: 'a'.repeat(501) },
      { kind: 'added', text: 'b'.repeat(501) },
      { kind: 'same', text: '뒤' },
    ]);
  });
});
