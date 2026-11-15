import { describe, expect, it } from 'vitest';
import { toSavableBlocks } from '../../../../domain/document/draft';
import { docToDrafts } from './convert';
import { delimitedRows, pastedTextNodes } from './pasteText';

function blocks(text: string) {
  let id = 0;
  return toSavableBlocks(
    docToDrafts({ type: 'doc', content: pastedTextNodes(text) ?? [] }, () => `id${id++}`),
    [],
  );
}

describe('structured text paste', () => {
  it('reads quoted delimiters, doubled quotes and embedded newlines', () => {
    expect(delimitedRows('이름,설명\n"가,나","두 ""말""\n다음"\n', ',')).toEqual([
      ['이름', '설명'],
      ['가,나', '두 "말"\n다음'],
    ]);
    expect(delimitedRows('"닫지 않음,x', ',')).toBeNull();
    expect(delimitedRows('"닫음"오류,x', ',')).toBeNull();
    expect(pastedTextNodes('이름\n한글', true)?.[0]).toMatchObject({
      type: 'table',
      content: [{ type: 'tableRow' }, { type: 'tableRow' }],
    });
  });

  it('converts spreadsheet cells as literal text rather than markdown', () => {
    expect(blocks('이름\t값\r\n한글\t**원문**')[0]).toMatchObject({
      type: 'table',
      rows: [{ cells: ['이름', '값'] }, { cells: ['한글', '\\*\\*원문\\*\\*'] }],
    });
    expect(pastedTextNodes('평범한 한글 문장')).toBeNull();
    expect(pastedTextNodes('문장, 쉼표 하나')).toBeNull();
  });

  it('converts GFM headings, tasks, code and aligned tables with escaped pipes', () => {
    const result = blocks(
      '# 제목\n\n- [x] **완료**\n\n```ts\nconst x = 1;\n```\n\n| 이름 | 값 |\n| :--- | ---: |\n| 가\\|나 | **둘** |',
    );
    expect(result).toMatchObject([
      { type: 'heading', level: 1, text: '제목' },
      { type: 'listItem', style: 'task', checked: true, text: '**완료**' },
      { type: 'code', language: 'ts', code: 'const x = 1;' },
      {
        type: 'table',
        columns: [{ align: 'left' }, { align: 'right' }],
        rows: [{ cells: ['이름', '값'] }, { cells: ['가|나', '**둘**'] }],
      },
    ]);
  });
});
