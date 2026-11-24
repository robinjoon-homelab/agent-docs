import {
  useEffect,
  useRef,
  useState,
  type RefObject,
  type PointerEvent as ReactPointerEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import {
  sameAddress,
  object,
  reorderDesignChild,
  type DesignEditScope,
} from '../../../../../../domain/flow/editDesign';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { snapMove, type Rect } from '../../../../../../domain/flow/flow';
import {
  positionMovable,
  sameGeometry,
  snapResize,
  writeLayerRect,
  type Handle,
  type Guide,
} from '../../../../../../domain/flow/editCanvasGeometry';
import { designRows, lookupRow } from './designLocation';
import {
  documentPoint,
  groupBounds,
  intersects,
  measureLayers,
  onCanvasPath,
  selectableSiblings,
  selectedBoxes,
  transformable,
  type LayerBox,
} from './designEditLayout';
import type { EditCommandProps } from './DesignEditCommands';

type Gesture = {
  mode: 'pending' | 'move' | 'resize' | 'range' | 'reorder';
  pointer: number;
  startX: number;
  startY: number;
  anchor: LayerBox | undefined;
  items: LayerBox[];
  candidates: LayerBox[];
  handle?: Handle;
  selected: readonly DesignAddress[];
  click: readonly DesignAddress[];
  additive: boolean;
  moved: boolean;
  positions: Map<LayerBox, Rect>;
  before: boolean;
  reorder?: LayerBox;
  range?: Rect;
};
const pointerCapture = (
  element: HTMLElement,
): { setPointerCapture?: (id: number) => void; hasPointerCapture?: (id: number) => boolean } => element;
export type EditPreview = {
  box?: Rect;
  range?: Rect;
  guides?: Guide[];
  coordinates?: Rect;
  reordering?: boolean;
};
function grouped(box: LayerBox, siblings: readonly LayerBox[]) {
  const group = box.node.group;
  return typeof group === 'string'
    ? siblings.filter((b) => b.node.group === group).map((b) => b.address)
    : [box.address];
}
function toggle(old: readonly DesignAddress[], next: readonly DesignAddress[]) {
  const keys = new Set(next.map(designAddressKey));
  const rest = old.filter((a) => !keys.has(designAddressKey(a)));
  return rest.length === old.length ? [...old, ...next] : rest;
}
function clearPreview(g: Gesture) {
  for (const layer of g.items) {
    const e = layer.element;
    e.removeAttribute('data-edit-gesture');
    for (const k of ['--edit-x', '--edit-y', '--edit-w', '--edit-h']) e.style.removeProperty(k);
  }
}
function showPreview(layer: LayerBox, rect: Rect, resize: boolean) {
  const e = layer.element,
    s = e.style;
  e.setAttribute('data-edit-gesture', resize ? 'resize' : 'move');
  let x = rect.x - layer.rect.x,
    y = rect.y - layer.rect.y;
  if (resize) {
    s.setProperty('--edit-w', String(Math.max(0, rect.w - (layer.contentInset?.x ?? 0))) + 'px');
    s.setProperty('--edit-h', String(Math.max(0, rect.h - (layer.contentInset?.y ?? 0))) + 'px');
    // End/center anchored elements move automatically when their width changes. Correct the preview using the actual new border box.
    s.setProperty('--edit-x', '0px');
    s.setProperty('--edit-y', '0px');
    const now = e.getBoundingClientRect(),
      parent = layer.parentElement.getBoundingClientRect();
    x =
      rect.x -
      ((now.left - parent.left) / layer.localScale.x -
        layer.parentElement.clientLeft +
        layer.parentElement.scrollLeft);
    y =
      rect.y -
      ((now.top - parent.top) / layer.localScale.y -
        layer.parentElement.clientTop +
        layer.parentElement.scrollTop);
  }
  s.setProperty('--edit-x', String(x) + 'px');
  s.setProperty('--edit-y', String(y) + 'px');
}
function nearestLayer(
  chain: readonly LayerBox[],
  scope: DesignEditScope,
  selected: readonly DesignAddress[],
  precise: boolean,
  annotated: Set<string>,
) {
  if (precise) return chain[0];
  const annotation = chain.find((b) => annotated.has(designAddressKey(b.address)));
  const existing = chain.find((b) => selected.some((a) => sameAddress(a, b.address)));
  return (
    existing ??
    annotation ??
    chain.find((b) => b.address.owner === scope.screen && !b.address.via?.length) ??
    chain[0]
  );
}
function savePositions(
  blocks: readonly FlowDesignBlock[],
  scope: DesignEditScope,
  changes: Map<LayerBox, Rect>,
) {
  const rows = designRows(resolveDesignScene(blocks, scope.screen, scope.state).root, blocks);
  let next = [...blocks];
  for (const [layer, position] of changes) {
    const current = lookupRow(rows, layer.address);
    const parent = current?.parent;
    if (
      !current ||
      !sameGeometry(layer.node, current.occurrence.node) ||
      !parent ||
      !layer.row.parent ||
      !sameAddress(parent, layer.row.parent)
    )
      throw Error('다른 편집으로 레이어 위치나 크기가 바뀌었습니다.');
    next = writeLayerRect(next, scope, layer, position);
  }
  return next;
}
export function useDesignDrag(
  host: RefObject<HTMLDivElement | null>,
  props: EditCommandProps,
  annotated: Set<string>,
) {
  const { scope, editable, selection, onSelect, rows, mutate, onMessage } = props;
  const gesture = useRef<Gesture | null>(null),
    consumed = useRef(false);
  const [preview, setPreview] = useState<EditPreview>();
  useEffect(
    () => () => {
      if (gesture.current) clearPreview(gesture.current);
      gesture.current = null;
    },
    [scope.screen, scope.state, editable],
  );
  function down(e: ReactPointerEvent<HTMLDivElement>) {
    consumed.current = false;
    if (!editable || !host.current || e.button !== 0 || !(e.target instanceof Element)) return;
    const resize = e.target.closest<HTMLElement>('[data-resize-handle]')?.dataset.resizeHandle as
      Handle | undefined;
    if (
      !resize &&
      (e.target.closest('[data-design-edit-ui],.design-canvas-warnings,.design-canvas-inspection') ||
        !e.target.closest('.design-canvas'))
    )
      return;
    const boxes = measureLayers(host.current, rows);
    const chain = onCanvasPath(host.current, e.target, boxes);
    const hit = resize
      ? selectedBoxes(boxes, selection)[0]
      : nearestLayer(chain, scope, selection, e.altKey, annotated);
    const siblings = selectableSiblings(
      boxes,
      hit?.row.parent ?? selectedBoxes(boxes, selection)[0]?.row.parent ?? rows[0]?.address,
    );
    const cluster = hit ? grouped(hit, siblings) : [];
    const chosen = hit
      ? resize
        ? selection
        : e.shiftKey
          ? toggle(selection, cluster)
          : selection.some((a) => sameAddress(a, hit.address))
            ? selection
            : cluster
      : e.shiftKey
        ? selection
        : [];
    const items = selectedBoxes(boxes, chosen);
    const unlocked = items.filter((b) => b.node.locked !== true && transformable(b));
    const canMove =
      !!hit &&
      unlocked.some((b) => sameAddress(b.address, hit.address)) &&
      unlocked.every((b) => b.parentElement === hit.parentElement && positionMovable(b.node));
    const parent = hit?.row.parent && lookupRow(rows, hit.row.parent);
    const layout = object(parent?.occurrence.node.layout)?.mode;
    const auto = layout === 'row' || layout === 'column' || layout === 'grid';
    const mode = !hit
      ? 'range'
      : resize && unlocked.length === 1
        ? 'resize'
        : canMove
          ? 'move'
          : auto && chosen.length === 1 && hit.node.locked !== true
            ? 'reorder'
            : 'pending';
    gesture.current = {
      mode,
      pointer: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      anchor: hit,
      items: mode === 'range' ? [] : unlocked,
      candidates: siblings,
      handle: resize,
      selected: chosen,
      click: !resize && !e.shiftKey ? cluster : chosen,
      additive: e.shiftKey,
      moved: false,
      positions: new Map(),
      before: false,
    };
    if (hit && !resize && (e.shiftKey || !selection.some((a) => sameAddress(a, hit.address))))
      onSelect(chosen);
    host.current.focus({ preventScroll: true });
    pointerCapture(host.current).setPointerCapture?.(e.pointerId);
    e.preventDefault();
    e.stopPropagation();
  }
  function move(e: ReactPointerEvent<HTMLDivElement>) {
    const g = gesture.current,
      h = host.current;
    if (!g || !h || g.pointer !== e.pointerId) return;
    const dx = e.clientX - g.startX,
      dy = e.clientY - g.startY;
    if (!g.moved && Math.hypot(dx, dy) < 2) return;
    g.moved = true;
    const anchor = g.anchor;
    if (g.mode === 'range') {
      const a = documentPoint(h, g.startX, g.startY),
        b = documentPoint(h, e.clientX, e.clientY);
      g.range = {
        x: Math.min(a.x, b.x),
        y: Math.min(a.y, b.y),
        w: Math.abs(a.x - b.x),
        h: Math.abs(a.y - b.y),
      };
      setPreview({ range: g.range });
      return;
    }
    if (!anchor || g.mode === 'pending') return;
    const px = dx / anchor.localScale.x,
      py = dy / anchor.localScale.y;
    const others = g.candidates.filter((b) => !g.items.some((i) => i.element === b.element));
    const group = groupBounds(g.items, 'rect') ?? anchor.rect;
    let guides: Guide[] = [],
      coordinates: Rect;
    if (g.mode === 'resize' && g.handle) {
      const result = snapResize(
        anchor.rect,
        g.handle,
        px,
        py,
        others.map((b) => b.rect),
        anchor.parent,
        e.altKey,
      );
      coordinates = result.rect;
      guides = result.guides;
      g.positions.set(anchor, coordinates);
      showPreview(anchor, coordinates, true);
    } else if (g.mode === 'move') {
      const snap = snapMove(
        group,
        px,
        py,
        others.map((b) => b.rect),
        anchor.parent.width,
        e.altKey,
      );
      guides = snap.guides;
      coordinates = { ...group, x: snap.x, y: snap.y };
      for (const b of g.items) {
        const to = { ...b.rect, x: b.rect.x + snap.x - group.x, y: b.rect.y + snap.y - group.y };
        g.positions.set(b, to);
        showPreview(b, to, false);
      }
    } else {
      const p = documentPoint(h, e.clientX, e.clientY),
        target = others.find((b) => intersects(b.overlay, { ...p, w: 1, h: 1 }));
      g.reorder = target;
      if (target) {
        const mode = object(lookupRow(rows, anchor.row.parent)?.occurrence.node.layout)?.mode;
        g.before =
          mode === 'row'
            ? p.x < target.overlay.x + target.overlay.w / 2
            : p.y < target.overlay.y + target.overlay.h / 2;
      }
      coordinates = { ...group, x: group.x + px, y: group.y + py };
      showPreview(anchor, coordinates, false);
    }
    const overlay = groupBounds(g.items, 'overlay') ?? anchor.overlay;
    const sx = anchor.overlay.w / anchor.rect.w,
      sy = anchor.overlay.h / anchor.rect.h;
    setPreview({
      box: {
        x: overlay.x + (coordinates.x - group.x) * sx,
        y: overlay.y + (coordinates.y - group.y) * sy,
        w: coordinates.w * sx,
        h: coordinates.h * sy,
      },
      coordinates,
      reordering: g.mode === 'reorder',
      guides: guides.map((guide) => ({
        ...guide,
        value:
          guide.axis === 'x'
            ? anchor.overlay.x + (guide.value - anchor.rect.x) * sx
            : anchor.overlay.y + (guide.value - anchor.rect.y) * sy,
      })),
    });
  }
  function finish(e: ReactPointerEvent<HTMLDivElement>, cancel: boolean) {
    const g = gesture.current,
      h = host.current;
    if (!h || !g || g.pointer !== e.pointerId) return;
    gesture.current = null;
    consumed.current = true;
    if (pointerCapture(h).hasPointerCapture?.(e.pointerId)) h.releasePointerCapture(e.pointerId);
    clearPreview(g);
    setPreview(undefined);
    if (cancel || !editable) return;
    if (!g.moved) {
      onSelect(g.click);
      return;
    }
    if (g.mode === 'range' && g.range) {
      const range = g.range;
      const hits = g.candidates.filter((b) => !b.node.locked && intersects(b.overlay, range));
      const addresses = [...(g.additive ? g.selected : []), ...hits.flatMap((b) => grouped(b, g.candidates))];
      onSelect([...new Map(addresses.map((a) => [designAddressKey(a), a])).values()]);
    } else if (g.positions.size) {
      const error = mutate((bs) => savePositions(bs, scope, g.positions));
      if (error) onMessage(error);
    } else if (g.mode === 'reorder' && g.anchor?.row.parent && g.reorder) {
      const parent = g.anchor.row.parent,
        source = g.anchor.address,
        target = g.reorder.address;
      const error = mutate((bs) => reorderDesignChild(bs, scope, parent, source, target, g.before));
      if (error) onMessage(error);
    }
    e.stopPropagation();
  }
  function click(e: ReactMouseEvent<HTMLDivElement>) {
    if (
      !editable ||
      !host.current ||
      !(e.target instanceof Element) ||
      e.target.closest('[data-design-edit-ui]') ||
      !e.target.closest('.design-canvas')
    )
      return;
    if (!consumed.current) {
      const boxes = measureLayers(host.current, rows),
        chain = onCanvasPath(host.current, e.target, boxes);
      const hit = nearestLayer(chain, scope, selection, e.altKey, annotated);
      const key = e.target.closest<HTMLElement>('[data-design-address]')?.dataset.designAddress;
      const row = hit?.row ?? rows.find((r) => designAddressKey(r.address) === key);
      if (row?.parent) {
        const choices = hit ? grouped(hit, selectableSiblings(boxes, hit.row.parent)) : [row.address];
        onSelect(e.shiftKey ? toggle(selection, choices) : choices);
      } else onSelect([]);
    }
    consumed.current = false;
    e.stopPropagation();
  }
  return {
    preview,
    events: {
      onPointerDownCapture: down,
      onPointerMoveCapture: move,
      onPointerUpCapture: (e: ReactPointerEvent<HTMLDivElement>) => finish(e, false),
      onPointerCancelCapture: (e: ReactPointerEvent<HTMLDivElement>) => finish(e, true),
      onLostPointerCapture: (e: ReactPointerEvent<HTMLDivElement>) => finish(e, true),
      onClickCapture: click,
    },
  };
}
