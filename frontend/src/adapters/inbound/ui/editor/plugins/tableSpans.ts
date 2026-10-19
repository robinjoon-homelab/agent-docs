import type { Node as PMNode } from '@tiptap/pm/model';
import { Fragment, Slice } from '@tiptap/pm/model';

type Covered = Map<number, Set<number>>;

/** One row as plain cells: each cell takes one column, and columns a cell above still spans are left empty. */
function gridRow(row: PMNode, rowIndex: number, covered: Covered): PMNode[] {
  const taken = covered.get(rowIndex) ?? new Set<number>();
  const cells: PMNode[] = [];
  const empty = (template: PMNode): PMNode =>
    template.type.create({ ...template.attrs, colspan: 1, rowspan: 1, colwidth: null });
  const skipTaken = (template: PMNode): void => {
    while (taken.has(cells.length)) cells.push(empty(template));
  };
  row.forEach((cell) => {
    skipTaken(cell);
    const start = cells.length;
    const colspan = Math.max(1, Number(cell.attrs.colspan) || 1);
    const rowspan = Math.max(1, Number(cell.attrs.rowspan) || 1);
    cells.push(
      cell.type.create({ ...cell.attrs, colspan: 1, rowspan: 1, colwidth: null }, cell.content, cell.marks),
    );
    for (let extra = 1; extra < colspan; extra++) cells.push(empty(cell));
    for (let below = 1; below < rowspan; below++) {
      const columns = covered.get(rowIndex + below) ?? new Set<number>();
      for (let column = start; column < start + colspan; column++) columns.add(column);
      covered.set(rowIndex + below, columns);
    }
  });
  const template = row.firstChild;
  if (template !== null) skipTaken(template);
  return cells;
}

function unmerged(table: PMNode): PMNode {
  const covered: Covered = new Map();
  const rows: PMNode[] = [];
  table.forEach((row, _offset, index) =>
    rows.push(row.type.create(row.attrs, gridRow(row, index, covered), row.marks)),
  );
  return table.type.create(table.attrs, rows, table.marks);
}

/**
 * Tables here never merge cells, so a pasted table with spanning cells becomes a plain grid: the content stays in the
 * first cell it covered and the rest become empty, which keeps every later cell in its own column.
 */
export function unmergePastedTables(slice: Slice): Slice {
  const children: PMNode[] = [];
  slice.content.forEach((child) => children.push(child.type.name === 'table' ? unmerged(child) : child));
  return new Slice(Fragment.fromArray(children), slice.openStart, slice.openEnd);
}
