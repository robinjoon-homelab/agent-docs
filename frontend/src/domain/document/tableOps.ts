import type { ColumnAlign, TableColumn } from './block';

/**
 * Row and column edits on a table, generic over the cell type so the editor can work on inline ASTs
 * and the stored model on markdown. Row ids live on rows and column ids on the column list, so every
 * operation keeps both in step. rows[0] is the header row.
 */
export type Grid<C> = {
  readonly columns: readonly TableColumn[];
  readonly rows: readonly { readonly id: string; readonly cells: readonly C[] }[];
};

function clampIndex(index: number, length: number): number {
  return Math.min(Math.max(index, 0), length);
}

function move<T>(items: readonly T[], from: number, to: number): T[] {
  const result = [...items];
  const [item] = result.splice(from, 1);
  if (item === undefined) return [...items];
  result.splice(clampIndex(to, result.length), 0, item);
  return result;
}

export function insertRow<C>(grid: Grid<C>, index: number, rowId: string, empty: C): Grid<C> {
  const rows = [...grid.rows];
  rows.splice(clampIndex(index, rows.length), 0, { id: rowId, cells: grid.columns.map(() => empty) });
  return { ...grid, rows };
}

/** A table keeps at least one row (the header). */
export function deleteRow<C>(grid: Grid<C>, index: number): Grid<C> {
  if (grid.rows.length <= 1) return grid;
  return { ...grid, rows: grid.rows.filter((_, rowIndex) => rowIndex !== index) };
}

export function moveRow<C>(grid: Grid<C>, from: number, to: number): Grid<C> {
  if (to < 0 || to >= grid.rows.length) return grid;
  return { ...grid, rows: move(grid.rows, from, to) };
}

export function insertColumn<C>(grid: Grid<C>, index: number, columnId: string, empty: C): Grid<C> {
  const at = clampIndex(index, grid.columns.length);
  const columns = [...grid.columns];
  columns.splice(at, 0, { id: columnId, align: 'none' });
  const rows = grid.rows.map((row) => {
    const cells = [...row.cells];
    cells.splice(at, 0, empty);
    return { ...row, cells };
  });
  return { columns, rows };
}

/** A table keeps at least one column. */
export function deleteColumn<C>(grid: Grid<C>, index: number): Grid<C> {
  if (grid.columns.length <= 1) return grid;
  return {
    columns: grid.columns.filter((_, columnIndex) => columnIndex !== index),
    rows: grid.rows.map((row) => ({
      ...row,
      cells: row.cells.filter((_, columnIndex) => columnIndex !== index),
    })),
  };
}

export function moveColumn<C>(grid: Grid<C>, from: number, to: number): Grid<C> {
  if (to < 0 || to >= grid.columns.length) return grid;
  return {
    columns: move(grid.columns, from, to),
    rows: grid.rows.map((row) => ({ ...row, cells: move(row.cells, from, to) })),
  };
}

export function setColumnAlign<C>(grid: Grid<C>, index: number, align: ColumnAlign): Grid<C> {
  return {
    ...grid,
    columns: grid.columns.map((column, columnIndex) =>
      columnIndex === index ? { ...column, align } : column,
    ),
  };
}

/** A new table: a header row plus `bodyRows` rows, all cells empty. */
export function emptyGrid<C>(columnCount: number, bodyRows: number, newId: () => string, empty: C): Grid<C> {
  const columns: TableColumn[] = Array.from({ length: columnCount }, () => ({ id: newId(), align: 'none' }));
  const rows = Array.from({ length: bodyRows + 1 }, () => ({ id: newId(), cells: columns.map(() => empty) }));
  return { columns, rows };
}
