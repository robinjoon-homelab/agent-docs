import { describe, expect, it } from 'vitest';
import { richBlocks, richPlain } from './richText';

describe('화면 흐름 설명 서식', () => {
  it('문단의 줄바꿈은 유지하고 목록·표·제목을 나눈다', () => {
    const source = [
      '[기본] 첫 줄',
      '둘째 줄',
      '',
      '- 하나',
      '- **둘**',
      '1. 첫째',
      '2. 둘째',
      '### 연산',
      '| 연산 | 화면 블록 |',
      '| --- | --- |',
      '| replace | 머리만 \\| 바꾼다 |',
    ].join('\n');
    expect(richBlocks(source)).toEqual([
      { kind: 'paragraph', lines: ['[기본] 첫 줄', '둘째 줄'] },
      { kind: 'list', ordered: false, items: ['하나', '**둘**'] },
      { kind: 'list', ordered: true, items: ['첫째', '둘째'] },
      { kind: 'heading', text: '연산' },
      { kind: 'table', header: ['연산', '화면 블록'], rows: [['replace', '머리만 | 바꾼다']] },
    ]);
  });

  it('구분선 없는 표는 머리 없이 모든 줄을 본문으로 둔다', () => {
    expect(richBlocks('| a | b |\n| c | d |')).toEqual([
      {
        kind: 'table',
        header: [],
        rows: [
          ['a', 'b'],
          ['c', 'd'],
        ],
      },
    ]);
  });

  it('미리보기 글은 문법을 빼고 한 줄로 잇는다', () => {
    expect(richPlain('- **굵게** 항목\n| 칸1 | 칸2 |\n[링크](doc:abc) 끝')).toBe('굵게 항목 칸1 칸2 링크 끝');
  });
});
