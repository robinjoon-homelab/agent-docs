import type { DesignJson, DesignObject, FlowDesignBlock } from '../document/designBlock';
import type { DesignAddress, DesignHop } from './designAddress';
import { designAddressKey } from './designAddress';
import { asObjects, editDesignValue } from './designValue';
import { equalDesignValues, isDesignObject } from '../document/designBlock';
import { applyPrecisionEdit, type PrecisionEdit } from './precisionDesign';

export function object(value: DesignJson | undefined): DesignObject | undefined {
  return isDesignObject(value) ? value : undefined;
}
export const records = asObjects;
export function stringValue(value: DesignJson | undefined, fallback = ''): string {
  return typeof value === 'string' ? value : fallback;
}

export function sourceBlock(blocks: readonly FlowDesignBlock[], id: string, kind?: string) {
  return blocks.find((b) => b.design.id === id && (!kind || b.design.kind === kind));
}
export function ownedChildren(n: DesignObject): DesignObject[] {
  const direct = ['children', 'operands', 'fallback', 'template'].flatMap((k) => records(n[k]));
  const slots = Object.values(object(n.slots) ?? {}).flatMap(records);
  const mask = object(n.source);
  return [...direct, ...slots, ...(mask ? [mask] : [])];
}
function roots(b: DesignObject) {
  const root = object(b.root);
  return root
    ? [root]
    : records(b.changes).flatMap((c) => {
        const n = object(c.node);
        return c.op === 'insert' && n ? [n] : [];
      });
}
export function findDesignNode(blocks: readonly FlowDesignBlock[], a: DesignAddress) {
  const b = sourceBlock(blocks, a.owner);
  const pending = b ? roots(b.design) : [];
  while (pending.length) {
    const n = pending.pop();
    if (n?.id === a.node) return n;
    if (n) pending.push(...ownedChildren(n));
  }
}
export function sameAddress(a: DesignAddress, b: DesignAddress): boolean {
  return designAddressKey(a) === designAddressKey(b);
}
export function readAddress(value: unknown): DesignAddress | undefined {
  if (
    value === null ||
    typeof value !== 'object' ||
    !('owner' in value) ||
    typeof value.owner !== 'string' ||
    !('node' in value) ||
    typeof value.node !== 'string'
  )
    return undefined;
  if (!('via' in value)) return { owner: value.owner, node: value.node };
  if (!Array.isArray(value.via)) return undefined;
  const via: DesignHop[] = [];
  for (const item of value.via as unknown[]) {
    if (!item || typeof item !== 'object' || !('owner' in item) || typeof item.owner !== 'string')
      return undefined;
    if (
      'instance' in item &&
      typeof item.instance === 'string' &&
      'component' in item &&
      typeof item.component === 'string'
    )
      via.push({ owner: item.owner, instance: item.instance, component: item.component });
    else if (
      'repeat' in item &&
      typeof item.repeat === 'string' &&
      'itemKey' in item &&
      typeof item.itemKey === 'string'
    )
      via.push({ owner: item.owner, repeat: item.repeat, itemKey: item.itemKey });
    else return undefined;
  }
  return { owner: value.owner, node: value.node, via };
}
function storedAddress(a: DesignAddress): DesignObject {
  return { owner: a.owner, node: a.node, ...(a.via?.length ? { via: a.via.map((h) => ({ ...h })) } : {}) };
}
function updateBlock(blocks: readonly FlowDesignBlock[], id: string, fn: (v: DesignObject) => DesignObject) {
  if (!sourceBlock(blocks, id)) throw Error('선택한 원본이 없어졌습니다.');
  return blocks.map((b) => (b.design.id === id ? { ...b, design: fn(b.design) } : b));
}
export function editDesignCollection(
  blocks: readonly FlowDesignBlock[],
  a: DesignAddress,
  fn: (v: DesignObject) => DesignObject,
) {
  const n = findDesignNode(blocks, a);
  if (!n) throw Error('선택한 요소가 없어졌습니다.');
  function walk(value: DesignJson): DesignJson {
    if (value === n) return fn(n);
    if (Array.isArray(value)) {
      const arr = value as readonly DesignJson[];
      const result = arr.map(walk);
      return result.some((x, i) => x !== arr[i]) ? result : arr;
    }
    const v = object(value);
    if (!v) return value;
    const entries = Object.entries(v).map(([k, child]) => [k, walk(child)] as const);
    return entries.some(([k, x]) => v[k] !== x) ? Object.fromEntries(entries) : v;
  }
  return updateBlock(blocks, a.owner, (v) => object(walk(v)) ?? v);
}
type NodeEdit =
  { kind: 'leaf'; path: string; value: DesignJson } | { kind: 'input'; prop: string; value: string };
export type DesignEditScope = { screen: string; state?: string; original?: boolean };
function fieldKey(e: DesignObject): string {
  const a = readAddress(e.target);
  return JSON.stringify([e.op, a && designAddressKey(a), e.path ?? e.prop, e.parts, e.field]);
}
function appendChange(value: DesignJson | undefined, change: DesignObject): DesignObject[] {
  const old = records(value);
  const last = old.at(-1);
  return last &&
    ['leaf', 'detailSet', 'input'].includes(stringValue(change.op)) &&
    fieldKey(last) === fieldKey(change)
    ? [...old.slice(0, -1), change]
    : [...old, change];
}
function dispatchDesignChange(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  a: DesignAddress,
  payload: DesignObject,
  write: (n: DesignObject) => DesignObject,
): FlowDesignBlock[] {
  const op = (addr: DesignAddress): DesignObject => ({ ...payload, target: storedAddress(addr) });
  if (scope.state && !scope.original) {
    const state = sourceBlock(blocks, scope.state, 'state');
    if (state?.design.screen !== scope.screen) throw Error('현재 화면의 상태를 찾을 수 없습니다.');
    return updateBlock(blocks, scope.state, (b) => ({ ...b, changes: appendChange(b.changes, op(a)) }));
  }
  if (!scope.original && a.via?.length) {
    const [first, ...via] = a.via;
    if (!first || !('instance' in first) || first.owner !== scope.screen)
      throw Error('이 요소는 상태 또는 컴포넌트 원본에서 수정할 수 있습니다.');
    const parent = { owner: first.owner, node: first.instance };
    const mount = findDesignNode(blocks, parent);
    if (mount?.component !== first.component) throw Error('선택한 인스턴스 원본이 달라졌습니다.');
    return editDesignCollection(blocks, parent, (n) => ({
      ...n,
      overrides: appendChange(n.overrides, op({ ...a, via })),
    }));
  }
  if (!scope.original && a.owner !== scope.screen) throw Error('선택한 요소가 이 화면에 속하지 않습니다.');
  return editDesignCollection(blocks, a, write);
}
export function editDesignNode(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  a: DesignAddress,
  e: NodeEdit,
) {
  const payload: DesignObject =
    e.kind === 'leaf'
      ? { op: 'leaf', path: e.path, value: e.value }
      : { op: 'input', prop: e.prop, value: e.value };
  return dispatchDesignChange(blocks, scope, a, payload, (n) =>
    e.kind === 'input'
      ? { ...n, inputs: { ...object(n.inputs), [e.prop]: e.value } }
      : editDesignValue(n, e.path.split('.'), () => e.value),
  );
}
export function editDesignPrecision(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  a: DesignAddress,
  e: PrecisionEdit,
) {
  return dispatchDesignChange(blocks, scope, a, { ...e }, (n) => applyPrecisionEdit(n, e));
}
export function reorderDesignChild(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  parent: DesignAddress,
  child: DesignAddress,
  sibling: DesignAddress,
  before: boolean,
): FlowDesignBlock[] {
  if (sameAddress(child, sibling)) throw Error('같은 요소로 이동할 수 없습니다.');
  if (scope.state && !scope.original) {
    const state = sourceBlock(blocks, scope.state, 'state');
    if (state?.design.screen !== scope.screen) throw Error('현재 상태를 찾을 수 없습니다.');
    return updateBlock(blocks, scope.state, (b) => ({
      ...b,
      changes: [
        ...records(b.changes),
        {
          op: 'move',
          target: storedAddress(child),
          at: { parent: storedAddress(parent), [before ? 'before' : 'after']: storedAddress(sibling) },
        },
      ],
    }));
  }
  if (
    parent.owner !== child.owner ||
    parent.owner !== sibling.owner ||
    (!scope.original && (parent.owner !== scope.screen || parent.via?.length))
  )
    throw Error('원본을 열어 자식 순서를 수정하세요.');
  return editDesignCollection(blocks, parent, (n) => {
    const children = records(n.children);
    const moving = children.find((c) => c.id === child.node);
    if (!moving) throw Error('선택 요소가 부모에서 이동했습니다.');
    const others = children.filter((c) => c !== moving);
    const index = others.findIndex((c) => c.id === sibling.node);
    if (index < 0) throw Error('기준 요소가 부모에서 이동했습니다.');
    others.splice(index + (before ? 0 : 1), 0, moving);
    return { ...n, children: others };
  });
}
export function setDesignToken(blocks: readonly FlowDesignBlock[], owner: string, id: string, color: string) {
  if (!/^#(?:[a-f\d]{3,4}|[a-f\d]{6}|[a-f\d]{8})$/i.test(color)) throw Error('16진수 색을 입력하세요.');
  return updateBlock(blocks, owner, (b) => {
    const entries = records(b.entries);
    if (!entries.some((t) => t.id === id && t.type === 'color' && typeof t.value === 'string'))
      throw Error('색 토큰이 변경되었습니다.');
    return { ...b, entries: entries.map((t) => (t.id === id ? { ...t, value: color } : t)) };
  });
}
export function setDesignInfo(
  blocks: readonly FlowDesignBlock[],
  owner: string,
  field: 'name' | 'description',
  v: string,
) {
  if (field === 'name' && !v.trim()) throw Error('이름을 입력하세요.');
  return updateBlock(blocks, owner, (b) => ({ ...b, [field]: v }));
}

export function setDesignAction(
  blocks: readonly FlowDesignBlock[],
  annotation: string,
  change: { add: DesignObject } | { remove: number; expected: DesignObject },
) {
  return updateBlock(blocks, annotation, (b) => {
    if (b.kind !== 'annotation') throw Error('주석을 찾을 수 없습니다.');
    const actions = records(b.actions);
    if ('remove' in change) {
      if (!equalDesignValues(actions[change.remove] ?? null, change.expected))
        throw Error('이동 목록이 바뀌었습니다.');
      return { ...b, actions: actions.filter((_, i) => i !== change.remove) };
    }
    const to = object(change.add.to);
    if (!to || !sourceBlock(blocks, stringValue(to.screen), 'screen'))
      throw Error('목적 화면을 찾을 수 없습니다.');
    if (to.state && sourceBlock(blocks, stringValue(to.state), 'state')?.design.screen !== to.screen)
      throw Error('목적 상태를 찾을 수 없습니다.');
    return { ...b, actions: [...actions, change.add] };
  });
}
