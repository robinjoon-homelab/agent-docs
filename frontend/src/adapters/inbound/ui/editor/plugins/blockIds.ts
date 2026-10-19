import type { Node as PMNode } from '@tiptap/pm/model';
import { Fragment, Slice } from '@tiptap/pm/model';
import type { Transaction } from '@tiptap/pm/state';
import { Plugin, PluginKey } from '@tiptap/pm/state';

function usable(id: unknown, seen: ReadonlySet<string>): id is string {
  return typeof id === 'string' && id !== '' && !seen.has(id);
}

function fixTable(
  tr: Transaction,
  table: PMNode,
  tablePos: number,
  seen: Set<string>,
  newId: () => string,
): boolean {
  let changed = false;
  table.forEach((row, offset) => {
    if (usable(row.attrs.id, seen)) {
      seen.add(row.attrs.id);
      return;
    }
    const id = newId();
    seen.add(id);
    tr.setNodeAttribute(tablePos + 1 + offset, 'id', id);
    changed = true;
  });
  const width = table.firstChild?.childCount ?? 0;
  const stored = (table.attrs.columns ?? []) as readonly { id?: unknown; align?: unknown }[];
  const columns = Array.from({ length: width }, (_, index) => {
    const column = stored[index];
    const id = usable(column?.id, seen) ? column.id : newId();
    seen.add(id);
    return { id, align: typeof column?.align === 'string' ? column.align : 'none' };
  });
  if (JSON.stringify(columns) !== JSON.stringify(stored)) {
    tr.setNodeAttribute(tablePos, 'columns', columns);
    changed = true;
  }
  return changed;
}

/**
 * Keeps every top-level block, table row and table column identified: new nodes get a fresh id and
 * duplicates (from split, paste or copy) are renamed, keeping the first occurrence's stored id.
 */
export function blockIdPlugin(newId: () => string): Plugin {
  return new Plugin({
    key: new PluginKey('blockIds'),
    appendTransaction(transactions, _old, state) {
      if (!transactions.some((transaction) => transaction.docChanged)) return null;
      const tr = state.tr;
      const seen = new Set<string>();
      let changed = false;
      let offset = 0;
      for (let index = 0; index < state.doc.childCount; index++) {
        const node = state.doc.child(index);
        if (usable(node.attrs.id, seen)) seen.add(node.attrs.id);
        else {
          const id = newId();
          seen.add(id);
          tr.setNodeAttribute(offset, 'id', id);
          changed = true;
        }
        if (node.type.name === 'table') changed = fixTable(tr, node, offset, seen, newId) || changed;
        offset += node.nodeSize;
      }
      return changed ? tr.setMeta('addToHistory', false) : null;
    },
  });
}

function existingIds(doc: PMNode): Set<string> {
  const ids = new Set<string>();
  doc.descendants((node) => {
    if (typeof node.attrs.id === 'string') ids.add(node.attrs.id);
    for (const column of (node.attrs.columns ?? []) as readonly { id?: unknown }[]) {
      if (typeof column.id === 'string') ids.add(column.id);
    }
    return node.type.name === 'table' || node.type.name === 'doc';
  });
  return ids;
}

function withoutTakenIds(node: PMNode, taken: ReadonlySet<string>): PMNode {
  if (node.isText) return node;
  let attrs = node.attrs;
  if (typeof attrs.id === 'string' && taken.has(attrs.id)) attrs = { ...attrs, id: null };
  const columns = (attrs.columns ?? []) as readonly { id?: unknown }[];
  if (columns.some((column) => typeof column.id === 'string' && taken.has(column.id)))
    attrs = { ...attrs, columns: [] };
  const content = node.type.name === 'table' ? mapFragment(node.content, taken) : node.content;
  return attrs === node.attrs && content === node.content
    ? node
    : node.type.create(attrs, content, node.marks);
}

function mapFragment(fragment: Fragment, taken: ReadonlySet<string>): Fragment {
  const children: PMNode[] = [];
  fragment.forEach((child) => children.push(withoutTakenIds(child, taken)));
  return Fragment.fromArray(children);
}

/**
 * Pasted blocks, rows and columns lose ids that the document already uses, so a pasted copy never
 * takes the original's id (and its comments). Cut-and-paste keeps ids: the original is gone first.
 */
export function clearDuplicateIds(slice: Slice, doc: PMNode): Slice {
  return new Slice(mapFragment(slice.content, existingIds(doc)), slice.openStart, slice.openEnd);
}
