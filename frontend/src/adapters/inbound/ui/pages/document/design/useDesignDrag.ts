import {
  useEffect,
  useRef,
  type PointerEvent as ReactPointerEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react';
import { lookupRow, designRows, type DesignRow } from './designLocation';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { editDesignNode, object, type DesignEditScope } from '../../../../../../domain/flow/editDesign';
import {
  equalDesignValues,
  type DesignJson,
  type DesignObject,
} from '../../../../../../domain/document/designBlock';
import type { DesignMutation } from './DesignNodeInspector';

type Drag = {
  address: DesignAddress;
  ptr: number;
  node: HTMLElement;
  original: string;
  startX: number;
  startY: number;
  ratioX: number;
  ratioY: number;
  position: DesignObject;
  dx: number;
  dy: number;
  moved: boolean;
};
function movableAxis(value: DesignJson | undefined) {
  const axis = object(value);
  if (!axis) return false;
  const edges = Object.entries(axis);
  return edges.length > 0 && edges.every(([k, v]) => (k === 'start' || k === 'end') && typeof v === 'number');
}
function coordinates(node: HTMLElement) {
  const parent = node.parentElement;
  if (!parent?.offsetWidth || !parent.offsetHeight) return;
  for (let p: HTMLElement | null = parent; p; p = p.parentElement) {
    const t = getComputedStyle(p).transform;
    if (t && t !== 'none') {
      if (typeof DOMMatrixReadOnly === 'undefined') return;
      const m = new DOMMatrixReadOnly(t);
      if (!m.is2D || m.b !== 0 || m.c !== 0 || m.a <= 0 || m.d <= 0) return;
    }
  }
  const rect = parent.getBoundingClientRect();
  return { x: rect.width / parent.offsetWidth, y: rect.height / parent.offsetHeight };
}
export function useDesignDrag(
  enabled: boolean,
  rows: readonly DesignRow[],
  scope: DesignEditScope,
  mutate: DesignMutation,
  select: (a: DesignAddress) => void,
) {
  const current = useRef<Drag | null>(null),
    consume = useRef(false);
  useEffect(
    () => () => {
      const d = current.current;
      if (d) d.node.style.translate = d.original;
      current.current = null;
    },
    [enabled, scope.screen, scope.state],
  );
  const down = (e: ReactPointerEvent<HTMLElement>) => {
    consume.current = false;
    if (!enabled || e.button !== 0 || !(e.target instanceof Element)) return;
    const node = e.target.closest<HTMLElement>('[data-design-address]');
    if (!node) return;
    const row = rows.find((r) => designAddressKey(r.address) === node.dataset.designAddress);
    if (!row || !row.parent) return;
    const n = row.occurrence.node,
      pos = object(n.position);
    if (n.locked === true || pos?.mode !== 'absolute' || !movableAxis(pos.x) || !movableAxis(pos.y)) return;
    const ratio = coordinates(node);
    if (!ratio?.x || !ratio.y) return;
    current.current = {
      ptr: e.pointerId,
      address: row.address,
      node,
      original: node.style.translate,
      startX: e.clientX,
      startY: e.clientY,
      ratioX: ratio.x,
      ratioY: ratio.y,
      position: pos,
      dx: 0,
      dy: 0,
      moved: false,
    };
    node.setPointerCapture(e.pointerId);
  };
  const move = (e: ReactPointerEvent<HTMLElement>) => {
    const d = current.current;
    if (!d || e.pointerId !== d.ptr) return;
    const dx = e.clientX - d.startX,
      dy = e.clientY - d.startY;
    if (!d.moved && Math.hypot(dx, dy) < 3) return;
    d.moved = true;
    d.dx = Math.round(dx / d.ratioX);
    d.dy = Math.round(dy / d.ratioY);
    d.node.style.translate = String(d.dx) + 'px ' + String(d.dy) + 'px';
    e.preventDefault();
  };
  const finish = (e: ReactPointerEvent<HTMLElement>, cancel: boolean) => {
    const d = current.current;
    if (!d || e.pointerId !== d.ptr) return;
    current.current = null;
    d.node.style.translate = d.original;
    if (d.node.hasPointerCapture(d.ptr)) d.node.releasePointerCapture(d.ptr);
    if (!enabled || !d.moved || cancel) return;
    consume.current = true;
    select(d.address);
    mutate((bs) => {
      const scene = resolveDesignScene(bs, scope.screen, scope.state);
      const now = lookupRow(designRows(scene.root, bs), d.address)?.occurrence.node;
      if (!now || now.locked === true || !equalDesignValues(d.position, now.position ?? null))
        throw Error('요소 위치가 변경되어 끌기를 적용하지 않았습니다.');
      let next = [...bs];
      for (const axis of ['x', 'y'] as const) {
        const distance = axis === 'x' ? d.dx : d.dy;
        if (!distance) continue;
        for (const [edge, val] of Object.entries(object(d.position[axis]) ?? {})) {
          if (typeof val === 'number')
            next = editDesignNode(next, scope, d.address, {
              kind: 'leaf',
              path: 'position.' + axis + '.' + edge,
              value: edge === 'start' ? val + distance : val - distance,
            });
        }
      }
      return next;
    });
  };
  const click = (e: ReactMouseEvent<HTMLElement>) => {
    if (consume.current) {
      consume.current = false;
      e.stopPropagation();
      e.preventDefault();
    }
  };
  return {
    onPointerDownCapture: down,
    onPointerMoveCapture: move,
    onPointerUpCapture: (e: ReactPointerEvent<HTMLElement>) => finish(e, false),
    onPointerCancelCapture: (e: ReactPointerEvent<HTMLElement>) => finish(e, true),
    onClickCapture: click,
  };
}
