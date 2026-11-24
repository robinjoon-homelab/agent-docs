import type { FlowDesignBlock, DesignObject, DesignJson } from '../document/designBlock';
import { equalDesignValues } from '../document/designBlock';
import { type DesignAddress } from './designAddress';
import { editDesignNode, object, type DesignEditScope } from './editDesign';
import { type Rect, resizeElement } from './flow';

export const resizeHandles = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'] as const;
export type Handle = (typeof resizeHandles)[number];
export type Guide = { axis: 'x' | 'y'; value: number };

type ParentSize = { width: number; height: number };
export type PlacedLayer = {
  address: DesignAddress;
  node: DesignObject;
  rect: Rect;
  parent: ParentSize;
  contentInset?: { x: number; y: number };
  parentMode?: string;
};
export function positionMovable(node: DesignObject): boolean {
  const p = object(node.position);
  return (
    p?.mode === 'absolute' &&
    ['x', 'y'].every((axis) => {
      const anchors = object(p[axis]);
      return (
        anchors &&
        Object.values(anchors).length > 0 &&
        Object.values(anchors).every((v) => typeof v === 'number' || typeof object(v)?.percent === 'number')
      );
    })
  );
}
function shifted(value: DesignJson, delta: number, parent: number): DesignJson {
  if (typeof value === 'number') return value + delta;
  const percent = object(value)?.percent;
  if (typeof percent === 'number' && parent > 0) return { percent: percent + (100 * delta) / parent };
  throw Error('바인딩된 위치는 속성에서 수정하세요.');
}

/** Preserve anchoring at either edge or the center. A stretched axis is resized by its insets. */
export function writeLayerRect(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  layer: PlacedLayer,
  next: Rect,
) {
  let result = [...blocks];
  const write = (path: string, value: DesignJson) => {
    result = editDesignNode(result, scope, layer.address, { kind: 'leaf', path, value });
  };
  for (const axis of ['x', 'y'] as const) {
    const dim = axis === 'x' ? 'w' : 'h';
    const distance = next[axis] - layer.rect[axis];
    const growth = next[dim] - layer.rect[dim];
    if (!distance && !growth) continue;
    const p = object(layer.node.position),
      anchors = object(p?.[axis]);
    if (p?.mode === 'absolute' && anchors) {
      const span = axis === 'x' ? layer.parent.width : layer.parent.height;
      for (const [key, value] of Object.entries(anchors)) {
        const delta =
          key === 'start'
            ? distance
            : key === 'end'
              ? -(distance + growth)
              : key === 'center'
                ? distance + growth / 2
                : 0;
        if (delta) write('position.' + axis + '.' + key, shifted(value, delta, span));
      }
    }
    if (growth && !(p?.mode === 'absolute' && anchors?.start !== undefined && anchors.end !== undefined)) {
      const old = object(object(layer.node.size)?.[axis]);
      write('size.' + axis, {
        mode: 'fixed',
        px: Math.max(0, Math.round((next[dim] - (layer.contentInset?.[axis] ?? 0)) * 10000) / 10000),
        ...(old?.min !== undefined ? { min: old.min } : {}),
        ...(old?.max !== undefined ? { max: old.max } : {}),
      });
      const main = axis === 'x' ? layer.parentMode === 'row' : layer.parentMode === 'column';
      const flex = object(layer.node.flexItem);
      if (main && p?.mode !== 'absolute' && flex) {
        if (flex.basis !== undefined && flex.basis !== 'auto') write('flexItem.basis', 'auto');
        if (typeof flex.grow === 'number' && flex.grow > 0) write('flexItem.grow', 0);
        if (flex.shrink !== 0) write('flexItem.shrink', 0);
      }
    }
  }
  return result;
}

export function sameGeometry(a: DesignObject, b: DesignObject) {
  return ['size', 'position', 'localTransform', 'locked', 'flexItem'].every((k) =>
    equalDesignValues(a[k] ?? null, b[k] ?? null),
  );
}

/** Snap dragged edges to the same four-pixel grid and adjacent guides used when moving. */
export function snapResize(
  rect: Rect,
  handle: Handle,
  dx: number,
  dy: number,
  others: readonly Rect[],
  parent: ParentSize,
  alt: boolean,
) {
  const guides: Guide[] = [];
  const deltas = { x: dx, y: dy };
  const limits = { x: parent.width, y: parent.height };
  for (const axis of ['x', 'y'] as const) {
    const start = axis === 'x' ? 'w' : 'n',
      end = axis === 'x' ? 'e' : 's',
      span = axis === 'x' ? 'w' : 'h';
    if (alt || (!handle.includes(start) && !handle.includes(end))) continue;
    const origin = rect[axis] + (handle.includes(end) ? rect[span] : 0);
    const edge = 4 * Math.round((origin + deltas[axis]) / 4);
    const targets = [
      0,
      limits[axis] / 2,
      limits[axis],
      ...others.flatMap((r) => [r[axis], r[axis] + r[span] / 2, r[axis] + r[span]]),
    ];
    const nearest = targets
      .filter((n) => Math.abs(edge - n) <= 4)
      .sort((a, b) => Math.abs(a - edge) - Math.abs(b - edge))[0];
    if (nearest !== undefined) guides.push({ axis, value: nearest });
    deltas[axis] = (nearest ?? edge) - origin;
  }
  // resizeElement accepts coordinates already snapped above. A free layout may overflow its containing block.
  const reach = Math.max(parent.width, rect.x + rect.w);
  const next = resizeElement(rect, { handle, dx: deltas.x, dy: deltas.y, width: reach, alt: true });
  if (!handle.includes('e') && !handle.includes('w')) {
    next.x = rect.x;
    next.w = rect.w;
  }
  if (!handle.includes('n') && !handle.includes('s')) {
    next.y = rect.y;
    next.h = rect.h;
  }
  return {
    rect: next,
    guides: guides.filter(
      (g) => g.value === next[g.axis] || g.value === next[g.axis] + next[g.axis === 'x' ? 'w' : 'h'],
    ),
  };
}
