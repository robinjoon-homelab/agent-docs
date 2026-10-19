import { getSchema } from '@tiptap/core';
import type { JSONContent } from '@tiptap/core';
import { Fragment, Node as PMNode, Slice } from '@tiptap/pm/model';
import { describe, expect, it } from 'vitest';
import { schemaExtensions } from '../schema';
import { unmergePastedTables } from './tableSpans';

const schema = getSchema(schemaExtensions);

function cell(text: string, colspan = 1, rowspan = 1): JSONContent {
  return {
    type: 'tableCell',
    attrs: { colspan, rowspan },
    content: text === '' ? [] : [{ type: 'text', text }],
  };
}

function table(...rows: JSONContent[][]): PMNode {
  return PMNode.fromJSON(schema, {
    type: 'table',
    content: rows.map((content) => ({ type: 'tableRow', content })),
  });
}

function grid(node: PMNode | null | undefined): string[][] {
  const rows: string[][] = [];
  node?.forEach((row) => {
    const cells: string[] = [];
    row.forEach((c) => cells.push(c.attrs.colspan === 1 && c.attrs.rowspan === 1 ? c.textContent : 'merged'));
    rows.push(cells);
  });
  return rows;
}

describe('unmergePastedTables', () => {
  it('lays spanning cells out as a plain grid so later cells keep their columns', () => {
    const pasted = table(
      [cell('Name', 2), cell('Total')],
      [cell('Q1', 1, 2), cell('Jan'), cell('10')],
      [cell('Feb'), cell('20')],
    );

    const result = unmergePastedTables(new Slice(Fragment.from(pasted), 0, 0));

    expect(grid(result.content.firstChild)).toEqual([
      ['Name', '', 'Total'],
      ['Q1', 'Jan', '10'],
      ['', 'Feb', '20'],
    ]);
  });
});
