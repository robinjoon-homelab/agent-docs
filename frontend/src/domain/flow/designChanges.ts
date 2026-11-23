import type { DesignJson, DesignObject } from '../document/designBlock';
import { asObject, asObjects, asText, editDesignValue, mergeDesignFields } from './designValue';

type Edit = (value: DesignJson | undefined) => DesignJson | undefined;
function itemIndex(rows: readonly DesignObject[], key: string, value: DesignJson | undefined) {
  return rows.findIndex((r) => r[key] === value);
}
function placed(
  rows: readonly DesignObject[],
  value: DesignObject,
  where: DesignObject,
  key: string,
): readonly DesignObject[] {
  let i = where.edge === 'first' ? 0 : rows.length;
  if (where.before !== undefined || where.after !== undefined) {
    i = itemIndex(rows, key, where.before ?? where.after);
    if (i < 0) return rows;
    if (where.after !== undefined) i++;
  }
  return [...rows.slice(0, i), value, ...rows.slice(i)];
}
function editList(
  list: readonly DesignObject[],
  ch: DesignObject,
  within: readonly DesignObject[],
): readonly DesignObject[] {
  const [hop, ...rest] = within;
  if (hop) {
    const field = asText(hop.field);
    if (!field) return list;
    return list.map((r) =>
      r.itemKey === hop.itemKey
        ? editDesignValue(r, ['fields', field], (old) => editList(asObjects(old), ch, rest))
        : r,
    );
  }
  const item = list.find((r) => r.itemKey === ch.itemKey);
  if (ch.op === 'itemInsert') return placed(list, asObject(ch.item), asObject(ch.at), 'itemKey');
  if (!item) return list;
  if (ch.op === 'itemRemove') return list.filter((r) => r !== item);
  if (ch.op === 'itemMove') {
    const rest = list.filter((r) => r !== item),
      moved = placed(rest, item, asObject(ch.at), 'itemKey');
    return rest === moved ? list : moved;
  }
  const field = asText(ch.field);
  return !field
    ? list
    : list.map((r) => (r === item ? editDesignValue(item, ['fields', field], () => ch.value) : r));
}
function partPath(n: DesignObject, collection: string, first: boolean): string[] {
  if (collection === 'textFills') return first ? ['style', 'text', 'fills'] : ['style', 'fills'];
  if (!first) return [collection];
  if (['fills', 'strokes', 'effects'].includes(collection)) return ['style', collection];
  if (['runs', 'paragraphs'].includes(collection)) return ['text', collection];
  if (['vertices', 'segments', 'regions'].includes(collection) && n.network) return ['network', collection];
  return [collection];
}
function editMember(n: DesignObject, steps: readonly DesignObject[], edit: Edit, first = true): DesignObject {
  const [s, ...rest] = steps;
  if (!s) return asObject(edit(n));
  const path = partPath(n, asText(s.collection) ?? '', first);
  return editDesignValue(n, path, (part) => {
    if (Array.isArray(part)) {
      return asObjects(part).map((member) =>
        member.id === s.id ? editMember(member, rest, edit, false) : member,
      );
    }
    const member = asObject(part);
    return member.id === s.id ? editMember(member, rest, edit, false) : part;
  });
}
function modifyDetail(n: DesignObject, ch: DesignObject): DesignObject {
  const steps = asObjects(ch.parts);
  if (ch.op === 'detailSet' || ch.op === 'detailMask') {
    const field = asText(ch.field)?.split('.') ?? [];
    return editMember(n, steps, (v) =>
      editDesignValue(asObject(v), field, () => (ch.op === 'detailSet' ? ch.value : undefined)),
    );
  }
  const insert = ch.op === 'detailInsert',
    last = steps.at(-1);
  const parents = insert ? asObjects(ch.parents) : steps.slice(0, -1);
  const collection = asText(insert ? ch.collection : last?.collection);
  if (!collection) return n;
  return editMember(n, parents, (value) =>
    editDesignValue(asObject(value), partPath(asObject(value), collection, !parents.length), (current) => {
      const list = asObjects(current);
      if (insert) return placed(list, asObject(ch.item), asObject(ch.at), 'id');
      const member = list.find((p) => p.id === last?.id);
      if (!member) return current;
      const rest = list.filter((p) => p !== member);
      if (ch.op === 'detailRemove') return rest;
      const moved = placed(rest, member, asObject(ch.at), 'id');
      return moved === rest ? list : moved;
    }),
  );
}
export function applyNodeChange(
  node: DesignObject,
  ch: DesignObject,
  resolve: (value: DesignJson) => DesignJson,
): DesignObject {
  if (ch.op === 'set' || ch.op === 'variant') return mergeDesignFields(node, asObject(ch.fields));
  if (ch.op === 'hide') return { ...node, visible: false };
  if (ch.op === 'leaf' || ch.op === 'mask')
    return editDesignValue(node, asText(ch.path)?.split('.') ?? [], () =>
      ch.op === 'mask' ? undefined : ch.value,
    );
  if (ch.op === 'run')
    return editMember(node, [{ collection: 'runs', id: ch.run ?? '' }], (r) =>
      mergeDesignFields(asObject(r), asObject(ch.fields)),
    );
  const prop = asText(ch.prop);
  if (ch.op === 'input' && prop) return editDesignValue(node, ['inputs', prop], () => ch.value);
  if (typeof ch.op === 'string' && ch.op.startsWith('item') && prop)
    return editDesignValue(node, ['inputs', prop], (old) =>
      editList(asObjects(resolve(old ?? [])), ch, asObjects(ch.within)),
    );
  if (typeof ch.op === 'string' && ch.op.startsWith('detail')) return modifyDetail(node, ch);
  return node;
}
