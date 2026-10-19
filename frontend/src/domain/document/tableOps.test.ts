import { describe, expect, it } from 'vitest';
import type { Grid } from './tableOps';
import { deleteColumn, deleteRow, emptyGrid, insertColumn, insertRow, moveColumn, moveRow } from './tableOps';

const grid: Grid<string> = {
  columns: [
    { id: 'c1', align: 'none' },
    { id: 'c2', align: 'right' },
  ],
  rows: [
    { id: 'r1', cells: ['A', 'B'] },
    { id: 'r2', cells: ['a', 'b'] },
  ],
};

describe('table operations', () => {
  it('inserts and moves columns together with their cells and ids', () => {
    const inserted = insertColumn(grid, 1, 'c3', '');
    expect(inserted.columns.map((column) => column.id)).toEqual(['c1', 'c3', 'c2']);
    expect(inserted.rows.map((row) => row.cells)).toEqual([
      ['A', '', 'B'],
      ['a', '', 'b'],
    ]);
    const moved = moveColumn(inserted, 2, 0);
    expect(moved.columns.map((column) => column.id)).toEqual(['c2', 'c1', 'c3']);
    expect(moved.rows[1]?.cells).toEqual(['b', 'a', '']);
  });

  it('keeps at least one row and one column', () => {
    const single = deleteRow(deleteRow(grid, 1), 0);
    expect(single.rows.map((row) => row.id)).toEqual(['r1']);
    expect(deleteColumn(deleteColumn(grid, 0), 0).columns.map((column) => column.id)).toEqual(['c2']);
  });

  it('inserts rows with one empty cell per column and ignores moves past the edges', () => {
    const inserted = insertRow(grid, 99, 'r3', '-');
    expect(inserted.rows.at(-1)).toEqual({ id: 'r3', cells: ['-', '-'] });
    expect(moveRow(inserted, 0, -1)).toBe(inserted);
    expect(moveRow(inserted, 2, 1).rows.map((row) => row.id)).toEqual(['r1', 'r3', 'r2']);
  });

  it('builds an empty table with a header row', () => {
    let next = 0;
    const created = emptyGrid(2, 1, () => `id${next++}`, '');
    expect(created).toEqual({
      columns: [
        { id: 'id0', align: 'none' },
        { id: 'id1', align: 'none' },
      ],
      rows: [
        { id: 'id2', cells: ['', ''] },
        { id: 'id3', cells: ['', ''] },
      ],
    });
  });
});
