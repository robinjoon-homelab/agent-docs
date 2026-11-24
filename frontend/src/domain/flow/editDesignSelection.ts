import type { DesignJson, DesignObject, FlowDesignBlock } from '../document/designBlock';
import { isDesignObject } from '../document/designBlock';
import type { DesignAddress } from './designAddress';
import {
  findDesignNode,
  editDesignCollection,
  object,
  ownedChildren,
  records,
  sourceBlock,
  stringValue,
} from './editDesign';
import { bounds, type Rect } from './flow';
import { editDesignValue } from './designValue';

export type DesignClipboard = {
  readonly owner: string;
  readonly nodes: readonly DesignObject[];
  readonly annotations: readonly DesignObject[];
};
export type DesignLayerContainer = DesignAddress & { slot?: string };
export function containerChildren(node: DesignObject, container: DesignLayerContainer) {
  return records(container.slot ? object(node.slots)?.[container.slot] : node.children);
}
export function replaceContainerChildren(
  node: DesignObject,
  container: DesignLayerContainer,
  children: readonly DesignObject[],
): DesignObject {
  if (container.slot) {
    if (node.kind !== 'instance') throw Error('선택한 슬롯의 인스턴스가 변경되었습니다.');
    return { ...node, slots: { ...object(node.slots), [container.slot]: children } };
  }
  if (node.kind !== 'box' && node.kind !== 'group') throw Error('요소를 놓을 상자를 선택하세요.');
  return { ...node, children };
}
export function copyDesignSiblings(
  bs: readonly FlowDesignBlock[],
  parent: DesignLayerContainer,
  ids: readonly string[],
): DesignClipboard {
  const n = findDesignNode(bs, parent),
    owner = sourceBlock(bs, parent.owner, 'screen');
  if (!n || !owner || parent.via?.length) throw Error('기본 화면의 상자를 선택하세요.');
  const selected = containerChildren(n, parent).filter((c) => ids.includes(stringValue(c.id)));
  if (!selected.length || selected.length !== new Set(ids).size)
    throw Error('같은 상자에 속한 요소를 선택하세요.');
  const all = new Set<string>();
  const collect = (v: DesignObject) => {
    all.add(stringValue(v.id));
    ownedChildren(v).forEach(collect);
  };
  selected.forEach(collect);
  const includes = (a: DesignObject) =>
    (a.owner === parent.owner && all.has(stringValue(a.node))) ||
    records(a.via).some((v) => v.owner === parent.owner && all.has(stringValue(v.instance ?? v.repeat)));
  const annotations = bs
    .filter(
      (b) =>
        b.design.kind === 'annotation' &&
        b.design.screen === parent.owner &&
        !b.design.state &&
        includes(object(b.design.target) ?? {}),
    )
    .map((b) => b.design);
  return { owner: parent.owner, nodes: selected, annotations };
}
export function pasteDesignSiblings(
  bs: readonly FlowDesignBlock[],
  parent: DesignLayerContainer,
  clip: DesignClipboard,
  allocate: () => string,
) {
  if (parent.via?.length || !sourceBlock(bs, parent.owner, 'screen'))
    throw Error('기본 화면에서 붙여 넣을 위치를 선택하세요.');
  const result = cloneCanvasClipboard(clip, parent.owner, parent.owner, allocate);
  const next = editDesignCollection(bs, parent, (p) =>
    replaceContainerChildren(p, parent, [...containerChildren(p, parent), ...result.nodes]),
  );
  return { blocks: [...next, ...result.annotations], ids: result.nodes.map((n) => stringValue(n.id)) };
}

export function cloneCanvasClipboard(
  clip: DesignClipboard,
  owner: string,
  screen: string,
  allocate: () => string,
  offset = 16,
  rename = false,
  state?: string,
) {
  const map = new Map<string, string>(),
    groups = new Map<string, string>();
  function assign(n: DesignObject) {
    map.set(stringValue(n.id), allocate());
    ownedChildren(n).forEach(assign);
  }
  clip.nodes.forEach(assign);
  function remapReferences(v: DesignJson): DesignJson {
    if (Array.isArray(v)) return (v as readonly DesignJson[]).map(remapReferences);
    if (!isDesignObject(v)) return v;
    const copy: Record<string, DesignJson> = Object.fromEntries(
      Object.entries(v).map(([k, x]) => [k, remapReferences(x)]),
    );
    if (v.owner === clip.owner) {
      for (const key of ['node', 'instance', 'repeat']) {
        const mapped = map.get(stringValue(v[key]));
        if (mapped) {
          copy.owner = owner;
          copy[key] = mapped;
        }
      }
    } else if (typeof v.repeat === 'string' && typeof v.field === 'string' && !v.owner)
      copy.repeat = map.get(v.repeat) ?? v.repeat;
    return copy;
  }
  function clone(n: DesignObject): DesignObject {
    const embedded = new Set([
      'children',
      'template',
      'fallback',
      'operands',
      'source',
      'slots',
      'overrides',
    ]);
    const data = Object.fromEntries(
      Object.entries(n)
        .filter(([k]) => !embedded.has(k))
        .map(([k, v]) => [k, remapReferences(v)]),
    );
    const copy: Record<string, DesignJson> = { ...data, id: map.get(stringValue(n.id)) ?? allocate() };
    for (const field of ['children', 'template', 'fallback', 'operands'])
      if (Array.isArray(n[field])) copy[field] = records(n[field]).map(clone);
    if (object(n.source)) copy.source = clone(object(n.source) ?? {});
    if (object(n.slots))
      copy.slots = Object.fromEntries(
        Object.entries(object(n.slots) ?? {}).map(([k, items]) => [k, records(items).map(clone)]),
      );
    if (Array.isArray(n.overrides))
      copy.overrides = records(n.overrides).map((op) => {
        const change = object(remapReferences(op)) ?? {};
        const assignment = object(op.assignment);
        if (op.op === 'slot' && Array.isArray(assignment?.children)) {
          return { ...change, assignment: { children: records(assignment.children).map(clone) } };
        }
        return change;
      });
    if (typeof n.group === 'string') {
      const g = groups.get(n.group) ?? allocate();
      groups.set(n.group, g);
      copy.group = g;
    }
    return copy;
  }
  const nodes = clip.nodes.map((n) => {
    let copy = clone(n);
    if (rename)
      copy = {
        ...copy,
        name:
          (stringValue(n.name) ||
            stringValue(n.alias) ||
            stringValue(n.text) ||
            stringValue(n.component) ||
            '요소') + ' 사본',
      };
    const position = object(n.position);
    if (position?.mode === 'absolute')
      for (const axis of ['x', 'y'])
        for (const [edge, v] of Object.entries(object(position[axis]) ?? {})) {
          if ((edge === 'start' || edge === 'center' || edge === 'end') && typeof v === 'number')
            copy = editDesignValue(
              copy,
              ['position', axis, edge],
              () => v + (edge === 'end' ? -offset : offset),
            );
        }
    return copy;
  });
  const annotations = clip.annotations.map((a) => {
    const id = allocate();
    return {
      id,
      type: 'flowDesign' as const,
      design: {
        ...Object.fromEntries(Object.entries(a).filter(([key]) => key !== 'state')),
        id,
        screen,
        ...(state ? { state } : {}),
        target: remapReferences(a.target ?? {}),
      },
    };
  });
  return { nodes, annotations };
}

export type Arrange = 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom' | 'horizontal' | 'vertical';
export function arrangeDesignSiblings(
  bs: readonly FlowDesignBlock[],
  parent: DesignLayerContainer,
  ids: readonly string[],
  operation: Arrange,
  frames: ReadonlyMap<string, Rect>,
) {
  const items = copyDesignSiblings(bs, parent, ids).nodes;
  if (items.length < 2 || items.some((n) => n.locked === true || !frames.has(stringValue(n.id))))
    throw Error('잠기지 않은 자유 배치 요소를 두 개 이상 선택하세요.');
  const dimensions = items.map((n) => ({
    id: stringValue(n.id),
    ...(frames.get(stringValue(n.id)) as Rect),
  }));
  const horizontal = ['left', 'center', 'right', 'horizontal'].includes(operation),
    axis = horizontal ? 'x' : 'y',
    size = horizontal ? 'w' : 'h';
  const sorted = [...dimensions].sort((a, b) => a[axis] - b[axis]),
    all = bounds(dimensions),
    span = all[size],
    start = all[axis];
  const gap = (span - sorted.reduce((sum, one) => sum + one[size], 0)) / (sorted.length - 1);
  let cursor = start;
  const coords = new Map(
    sorted.map((one) => {
      const v =
        operation === 'left' || operation === 'top'
          ? start
          : operation === 'right' || operation === 'bottom'
            ? start + span - one[size]
            : operation === 'center' || operation === 'middle'
              ? start + (span - one[size]) / 2
              : cursor;
      cursor += gap + one[size];
      return [one.id, v];
    }),
  );
  return editDesignCollection(bs, parent, (p) => ({
    ...p,
    children: records(p.children).map((n) => {
      const value = coords.get(stringValue(n.id));
      return value === undefined ? n : editDesignValue(n, ['position', axis, 'start'], () => value);
    }),
  }));
}
export function markDesignSiblings(
  bs: readonly FlowDesignBlock[],
  parent: DesignLayerContainer,
  ids: readonly string[],
  field: 'group' | 'locked',
  value: string | boolean | null,
) {
  copyDesignSiblings(bs, parent, ids);
  return editDesignCollection(bs, parent, (p) =>
    replaceContainerChildren(
      p,
      parent,
      containerChildren(p, parent).map((n) =>
        ids.includes(stringValue(n.id))
          ? editDesignValue(n, [field], () => (value === null ? undefined : value))
          : n,
      ),
    ),
  );
}
