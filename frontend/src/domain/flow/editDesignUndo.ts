import {
  equalDesignValues,
  isDesignObject,
  type DesignJson,
  type DesignObject,
  type FlowDesignBlock,
} from '../document/designBlock';

const same = (a: DesignJson | undefined, b: DesignJson | undefined) =>
  a === undefined || b === undefined ? a === b : equalDesignValues(a, b);
const conflict = () => {
  throw Error('같은 속성이 다른 편집에서 바뀌어 되돌릴 수 없습니다.');
};

function keyed(values: readonly DesignJson[]): values is readonly (DesignObject & { readonly id: string })[] {
  return (
    values.every((v) => isDesignObject(v) && typeof v.id === 'string') &&
    new Set(values.map((v) => (isDesignObject(v) ? v.id : null))).size === values.length
  );
}
function merge(
  base: DesignJson | undefined,
  target: DesignJson | undefined,
  current: DesignJson | undefined,
): DesignJson | undefined {
  if (same(base, target) || same(target, current)) return current;
  if (same(base, current)) return target;
  if (isDesignObject(base) && isDesignObject(target) && isDesignObject(current)) {
    const result: Record<string, DesignJson> = {};
    for (const k of new Set([...Object.keys(base), ...Object.keys(target), ...Object.keys(current)])) {
      const value = merge(base[k], target[k], current[k]);
      if (value !== undefined) result[k] = value;
    }
    return result;
  }
  if (Array.isArray(base) && Array.isArray(target) && Array.isArray(current)) {
    const b = base as readonly DesignJson[],
      t = target as readonly DesignJson[],
      c = current as readonly DesignJson[];
    if (keyed(b) && keyed(t) && keyed(c)) {
      const before = b.map((v) => v.id),
        after = t.map((v) => v.id),
        now = c.map((v) => v.id);
      // Independent field edits retain the latest order, including new siblings.
      const order = same(before, after) ? now : same(before, now) || same(after, now) ? after : conflict();
      const merged = new Map<string, DesignJson>();
      const index = (values: readonly (DesignObject & { readonly id: string })[]) =>
        new Map(values.map((v) => [v.id, v]));
      const baseMap = index(b),
        targetMap = index(t),
        currentMap = index(c);
      for (const id of new Set([...before, ...after, ...now])) {
        const value = merge(baseMap.get(id), targetMap.get(id), currentMap.get(id));
        if (value !== undefined) merged.set(id, value);
      }
      return order.flatMap((id) => {
        const value = merged.get(id);
        return value === undefined ? [] : [value];
      });
    }
  }
  return conflict();
}
export function restoreDesignEdit(
  base: readonly FlowDesignBlock[],
  target: readonly FlowDesignBlock[],
  current: readonly FlowDesignBlock[],
): FlowDesignBlock[] {
  // A screen number that was displayed is reserved even when its creation is undone.
  const sequence = (bs: readonly FlowDesignBlock[]) => {
    const serial = bs.find((b) => b.design.kind === 'settings')?.design.nextScreen;
    return typeof serial === 'number' ? serial : 0;
  };
  const nextScreen = Math.max(sequence(base), sequence(target), sequence(current));
  const normalized = (bs: readonly FlowDesignBlock[]) =>
    bs.map((b) => ({
      id: b.id,
      design: nextScreen && b.design.kind === 'settings' ? { ...b.design, nextScreen } : b.design,
    }));
  const result = merge(normalized(base), normalized(target), normalized(current));
  if (!Array.isArray(result)) return conflict();
  return (result as readonly DesignJson[]).map((v) => {
    if (!isDesignObject(v) || typeof v.id !== 'string' || !isDesignObject(v.design)) return conflict();
    return { id: v.id, type: 'flowDesign', design: v.design };
  });
}
