import { designAssetFontsReady } from './DesignCanvasResources';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';

type Size = { width: number; height: number };
type Rect = Size & { x: number; y: number };
export type MeasuredDesignLayer = {
  address: string;
  sourceAddress: string;
  owner: string;
  node: string;
  rect: Rect;
  visibleRect: Rect | null;
  client: Size;
  content: Size;
  scroll: { x: number; y: number };
  overlappingSibling?: string;
  issues: string[];
};
export type CanvasMeasurement = {
  screen: string;
  state: string | null;
  mode: 'full' | 'viewport';
  fonts: { family: string; weight: string; loaded: number; total: number; failed: number }[];
  viewport: Size;
  natural: Size;
  layers: MeasuredDesignLayer[];
  warnings: string[];
  emptySlots: string[];
};
const frame = () => new Promise<void>((done) => requestAnimationFrame(() => done()));
const bounds = (el: HTMLElement): Rect => {
  const r = el.getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height };
};
function clip(rect: Rect, box: Rect, x: boolean, y: boolean): Rect | null {
  const left = x ? Math.max(rect.x, box.x) : rect.x,
    top = y ? Math.max(rect.y, box.y) : rect.y;
  const right = x ? Math.min(rect.x + rect.width, box.x + box.width) : rect.x + rect.width,
    bottom = y ? Math.min(rect.y + rect.height, box.y + box.height) : rect.y + rect.height;
  return right <= left || bottom <= top
    ? null
    : { x: left, y: top, width: right - left, height: bottom - top };
}
const clips = (overflow: string) => ['auto', 'scroll', 'clip', 'hidden'].includes(overflow);
type Geometry = { rect: Rect; x: boolean; y: boolean };
export function visibleDesignBounds(
  el: HTMLElement,
  rect: Rect,
  until: HTMLElement,
  ancestors: Map<HTMLElement, Geometry>,
) {
  let remaining: Rect | null = rect;
  for (let p = el.parentElement; p && remaining; p = p.parentElement) {
    let g = ancestors.get(p);
    if (!g) {
      const css = getComputedStyle(p),
        r = bounds(p),
        mask = Boolean(
          (css.clipPath && css.clipPath !== 'none') || (css.maskImage && css.maskImage !== 'none'),
        );
      const cx = p.offsetWidth ? r.width / p.offsetWidth : 1,
        cy = p.offsetHeight ? r.height / p.offsetHeight : 1;
      g = {
        rect: {
          x: r.x + p.clientLeft * cx,
          y: r.y + p.clientTop * cy,
          width: p.clientWidth * cx,
          height: p.clientHeight * cy,
        },
        x: mask || clips(css.overflowX || css.overflow),
        y: mask || clips(css.overflowY || css.overflow),
      };
      ancestors.set(p, g);
    }
    if (g.x || g.y) remaining = clip(remaining, g.rect, g.x, g.y);
    if (p === until) break;
  }
  return remaining;
}
const wantsScroll = (v: string | undefined) => v === 'end' || (v !== undefined && Number(v) > 0);
function measureLayer(
  el: HTMLElement,
  canvas: HTMLElement,
  ancestors: Map<HTMLElement, Geometry>,
  normalized: (r: Rect) => Rect,
): MeasuredDesignLayer {
  const css = getComputedStyle(el),
    actual = bounds(el),
    view = visibleDesignBounds(el, actual, canvas, ancestors),
    rect = normalized(actual),
    visibleRect = view ? normalized(view) : null;
  const issues: string[] = [];
  if (!visibleRect || visibleRect.height + 0.01 < rect.height || visibleRect.width + 0.01 < rect.width)
    issues.push('clipped');
  if (
    el.dataset.designKind === 'text' &&
    ((clips(css.overflowX || css.overflow) && el.scrollWidth > el.clientWidth) ||
      (clips(css.overflowY || css.overflow) && el.scrollHeight > el.clientHeight))
  )
    issues.push('clippedText');
  if (wantsScroll(el.dataset.designScrollX) && el.scrollWidth <= el.clientWidth)
    issues.push('zeroScrollRangeX');
  if (wantsScroll(el.dataset.designScrollY) && el.scrollHeight <= el.clientHeight)
    issues.push('zeroScrollRangeY');
  if (
    el.dataset.designKind === 'image' &&
    [...el.querySelectorAll('img')].some((img) => img.complete && !img.naturalWidth)
  )
    issues.push('unavailableAsset');
  return {
    address: el.dataset.designAddress ?? '',
    sourceAddress: el.dataset.designSourceAddress ?? '',
    owner: el.dataset.designOwner ?? '',
    node: el.dataset.designNode ?? '',
    rect,
    visibleRect,
    client: { width: el.clientWidth, height: el.clientHeight },
    content: { width: el.scrollWidth, height: el.scrollHeight },
    scroll: { x: el.scrollLeft, y: el.scrollTop },
    issues,
  };
}
function bundledFontStatus(fonts: FontFaceSet | undefined): CanvasMeasurement['fonts'] {
  const byFace = new Map<string, CanvasMeasurement['fonts'][number]>();
  for (const face of fonts ?? []) {
    const family = face.family.replace(/^["']|["']$/g, '');
    if (family !== 'Agent Docs Design Sans' && family !== 'Agent Docs Design Mono') continue;
    const key = family + ' ' + face.weight,
      group = byFace.get(key) ?? { family, weight: face.weight, loaded: 0, total: 0, failed: 0 };
    group.total++;
    if (face.status === 'loaded') group.loaded++;
    if (face.status === 'error') group.failed++;
    byFace.set(key, group);
  }
  return Array.from(byFace.values());
}
/** Coordinates use the canvas origin and design px. Nested transforms are reflected in the bounding rectangle. */
export async function inspectDesignCanvas(
  canvas: HTMLElement,
  address?: DesignAddress | null,
): Promise<CanvasMeasurement> {
  await frame();
  const fontSet = (canvas.ownerDocument as { fonts?: FontFaceSet }).fonts;
  const assetFontWarnings = await designAssetFontsReady(canvas);
  await fontSet?.ready;
  await Promise.all(
    Array.from(canvas.querySelectorAll('img'), (img) =>
      typeof img.decode === 'function' ? img.decode().catch(() => undefined) : Promise.resolve(),
    ),
  );
  await frame();
  await frame();
  const size = bounds(canvas),
    viewport = {
      width: Number(canvas.dataset.designFrameWidth) || canvas.clientWidth,
      height: Number(canvas.dataset.designFrameHeight) || canvas.clientHeight,
    };
  const zoom = viewport.width > 0 && size.width > 0 ? size.width / viewport.width : 1,
    key = address ? designAddressKey(address) : null;
  const normalize = (r: Rect) => ({
    x: (r.x - size.x) / zoom + canvas.scrollLeft,
    y: (r.y - size.y) / zoom + canvas.scrollTop,
    width: r.width / zoom,
    height: r.height / zoom,
  });
  const ancestry = new Map<HTMLElement, Geometry>();
  const all = Array.from(canvas.querySelectorAll<HTMLElement>('[data-design-node]'));
  const chosen = key
    ? all.filter(
        (el) =>
          el.dataset.designAddress === key ||
          el.dataset.designSourceAddress === key ||
          el.dataset.designMountKeys?.split('\n').includes(key),
      )
    : all;
  const layers = chosen.map((el) => measureLayer(el, canvas, ancestry, normalize));
  const root = all[0],
    r = root ? normalize(bounds(root)) : null,
    natural = {
      width: Math.max(viewport.width, r ? r.x + r.width : 0),
      height: Math.max(viewport.height, r ? r.y + r.height : 0),
    };
  const siblings = new Map<Element, HTMLElement[]>();
  for (const el of all) {
    if (el.parentElement) {
      const at = siblings.get(el.parentElement) ?? [];
      at.push(el);
      siblings.set(el.parentElement, at);
    }
  }
  const rects = new Map<HTMLElement, Rect>();
  const rectOf = (el: HTMLElement) => {
    let rect = rects.get(el);
    if (!rect) {
      rect = normalize(bounds(el));
      rects.set(el, rect);
    }
    return rect;
  };
  for (const [i, l] of layers.entries()) {
    const el = chosen[i],
      visibleRect = l.visibleRect;
    if (!el) continue;
    if (
      l.rect.x < -0.01 ||
      l.rect.y < -0.01 ||
      l.rect.x + l.rect.width > natural.width + 0.01 ||
      l.rect.y + l.rect.height > natural.height + 0.01
    )
      l.issues.push('outsideFrame');
    if (!el.parentElement || !visibleRect) continue;
    const overlap = (siblings.get(el.parentElement) ?? []).find((other) => {
      if (other === el) return false;
      // Only report intersecting visible bounding boxes of distinct direct siblings.
      const raw = clip(visibleRect, rectOf(other), true, true);
      if (!raw || raw.width <= 0.01 || raw.height <= 0.01) return false;
      const visibleOther = visibleDesignBounds(other, bounds(other), canvas, ancestry);
      return visibleOther !== null && clip(visibleRect, normalize(visibleOther), true, true) !== null;
    });
    if (overlap) {
      l.issues.push('overlapsSibling');
      l.overlappingSibling = overlap.dataset.designAddress;
    }
  }
  return {
    screen: canvas.dataset.designScreen ?? '',
    state: canvas.dataset.designState ?? null,
    mode: canvas.dataset.designMode === 'full' ? 'full' : 'viewport',
    fonts: bundledFontStatus(fontSet),
    viewport,
    natural,
    layers,
    emptySlots: (canvas.dataset.designEmptySlots ?? '').split('\n').filter(Boolean),
    warnings: [
      ...assetFontWarnings,
      ...(canvas.dataset.designWarnings ?? '').split('\n').filter(Boolean),
      ...(key && !layers.length ? ['선택한 레이어가 현재 화면에 표시되지 않습니다.'] : []),
    ],
  };
}
