import { bounds, type Rect } from '../../../../../../domain/flow/flow';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { object, stringValue } from '../../../../../../domain/flow/editDesign';
import { type PlacedLayer } from '../../../../../../domain/flow/editCanvasGeometry';
import type { DesignRow } from './designLocation';

export type LayerBox = PlacedLayer & {
  element: HTMLElement;
  parentElement: HTMLElement;
  row: DesignRow;
  localScale: { x: number; y: number };
  overlay: Rect;
};
export function documentPoint(host: HTMLElement, x: number, y: number) {
  const b = host.getBoundingClientRect();
  return {
    x: ((x - b.left) * host.offsetWidth) / (b.width || 1),
    y: ((y - b.top) * host.offsetHeight) / (b.height || 1),
  };
}
function keys(element: HTMLElement) {
  return [
    element.dataset.designAddress,
    element.dataset.designSourceAddress,
    ...(element.dataset.designMountKeys ?? '').split('\n'),
  ].filter(Boolean);
}
// An all-absolute free-layout box can have zero CSS height. Its children still inherit the device zoom.
function positiveScale(el: HTMLElement, axis: 'x' | 'y', first: DOMRect): number {
  let current: HTMLElement | null = el;
  let rect = first;
  let multiplier = 1;
  while (current) {
    const layout = axis === 'x' ? current.offsetWidth : current.offsetHeight;
    const visual = axis === 'x' ? rect.width : rect.height;
    if (layout > 0 && visual > 0) return (multiplier * visual) / layout;
    const css = getComputedStyle(current);
    if (css.transform && css.transform !== 'none' && typeof DOMMatrixReadOnly !== 'undefined') {
      const m = new DOMMatrixReadOnly(css.transform);
      multiplier *= Math.abs(axis === 'x' ? m.a : m.d) || 1;
    }
    multiplier *= Number(css.zoom) || 1;
    current = current.parentElement;
    if (current) rect = current.getBoundingClientRect();
  }
  return multiplier;
}
const pixel = (n: number) => Math.round(n * 10000) / 10000;
export function measureLayers(host: HTMLElement, rows: readonly DesignRow[]) {
  const byKey = new Map<string, HTMLElement>();
  const parents = new Map(rows.map((r) => [designAddressKey(r.address), r]));
  for (const el of host.querySelectorAll<HTMLElement>('[data-design-address]')) {
    for (const key of keys(el)) if (key) byKey.set(key, el);
  }
  const parentBounds = new Map<HTMLElement, DOMRect>();
  const hostRect = host.getBoundingClientRect();
  const scaleX = hostRect.width / (host.offsetWidth || 1) || 1,
    scaleY = hostRect.height / (host.offsetHeight || 1) || 1;
  const found = new Map<string, LayerBox>();
  for (const row of rows) {
    const key = designAddressKey(row.address),
      element = byKey.get(key),
      parent = element?.parentElement;
    if (!row.visible || !element || !parent) continue;
    let box = parentBounds.get(parent);
    if (!box) {
      box = parent.getBoundingClientRect();
      parentBounds.set(parent, box);
    }
    const r = element.getBoundingClientRect();
    if (!r.width || !r.height) continue;
    const sx = positiveScale(parent, 'x', box),
      sy = positiveScale(parent, 'y', box);
    const rect = {
      x: pixel((r.left - box.left) / sx - parent.clientLeft + parent.scrollLeft),
      y: pixel((r.top - box.top) / sy - parent.clientTop + parent.scrollTop),
      w: pixel(r.width / sx),
      h: pixel(r.height / sy),
    };
    const overlay = {
      x: (r.left - hostRect.left) / scaleX,
      y: (r.top - hostRect.top) / scaleY,
      w: r.width / scaleX,
      h: r.height / scaleY,
    };
    const style = row.occurrence.node.boxSizing === 'content-box' ? getComputedStyle(element) : null;
    const inset = (...values: string[]) => values.reduce((total, v) => total + (parseFloat(v) || 0), 0);
    const contentInset = style
      ? {
          x: inset(style.paddingLeft, style.paddingRight, style.borderLeftWidth, style.borderRightWidth),
          y: inset(style.paddingTop, style.paddingBottom, style.borderTopWidth, style.borderBottomWidth),
        }
      : undefined;
    const parentRow = row.parent ? parents.get(designAddressKey(row.parent)) : undefined;
    found.set(key, {
      contentInset,
      parentMode: stringValue(object(parentRow?.occurrence.node.layout)?.mode),
      address: row.address,
      node: row.occurrence.node,
      row,
      element,
      parentElement: parent,
      rect,
      overlay,
      parent: { width: parent.clientWidth, height: parent.clientHeight },
      localScale: { x: sx, y: sy },
    });
  }
  return found;
}

export function onCanvasPath(host: HTMLElement, event: Element, boxes: Map<string, LayerBox>) {
  const chain: LayerBox[] = [];
  for (
    let e: HTMLElement | null = event.closest<HTMLElement>('[data-design-address]');
    e && host.contains(e);
    e = e.parentElement?.closest<HTMLElement>('[data-design-address]') ?? null
  ) {
    const box = boxes.get(e.dataset.designAddress ?? '');
    if (box?.row.parent) chain.push(box);
  }
  return chain;
}
export function selectedBoxes(boxes: Map<string, LayerBox>, selection: readonly DesignAddress[]) {
  const result: LayerBox[] = [];
  for (const addr of selection) {
    const item = boxes.get(designAddressKey(addr));
    if (item && !result.some((x) => x.element === item.element)) result.push(item);
  }
  return result;
}
export function selectableSiblings(boxes: Map<string, LayerBox>, key: DesignAddress | undefined) {
  const parentKey = key ? designAddressKey(key) : undefined;
  const found: LayerBox[] = [];
  for (const box of boxes.values()) {
    if (
      box.row.parent &&
      designAddressKey(box.row.parent) === parentKey &&
      !found.some((x) => x.element === box.element)
    )
      found.push(box);
  }
  return found;
}
export function groupBounds(items: readonly LayerBox[], field: 'overlay' | 'rect') {
  return items.length ? bounds(items.map((x) => x[field])) : undefined;
}
export function intersects(a: Rect, b: Rect) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}
export function transformable(box: LayerBox) {
  // An axis-aligned outline cannot safely project a pointer delta through a rotated or mirrored parent.
  for (let p: HTMLElement | null = box.element; p; p = p.parentElement) {
    const t = getComputedStyle(p).transform;
    if (!t || t === 'none') continue;
    if (typeof DOMMatrixReadOnly === 'undefined') return false;
    const m = new DOMMatrixReadOnly(t);
    if (!m.is2D || Math.abs(m.b) > 0.0001 || Math.abs(m.c) > 0.0001 || m.a <= 0 || m.d <= 0) return false;
  }
  return true;
}
