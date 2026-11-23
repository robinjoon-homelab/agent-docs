import { useId, useLayoutEffect, type RefObject } from 'react';
import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import type { DesignOccurrence } from '../../../../../../domain/flow/designScene';
import { asObject, asNumber } from '../../../../../../domain/flow/designValue';
import { designFontFaceName } from './designStyle';
import { validAssetId } from './designStyleValues';

export class ScrollDiagnostics {
  private readonly notes = new Map<string, string>();
  private readonly listeners = new Set<() => void>();
  private snapshot: readonly string[] = [];
  readonly read = () => this.snapshot;
  readonly subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => {
      this.listeners.delete(fn);
    };
  };
  set(id: string, message?: string) {
    if (this.notes.get(id) === message) return;
    if (message) this.notes.set(id, message);
    else this.notes.delete(id);
    this.snapshot = Array.from(this.notes.values());
    for (const listen of this.listeners) listen();
  }
}
// Embedded browsers can expose document.fonts without a global FontFaceSet constructor.
function availableFonts(source: { fonts?: FontFaceSet }): FontFaceSet | undefined {
  return source.fonts;
}
export function useInitialScroll(
  ref: { current: HTMLDivElement | null },
  scroll: DesignObject,
  address: string,
  report: ScrollDiagnostics | undefined,
) {
  const x = scroll.x,
    y = scroll.y;
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el || (x === undefined && y === undefined)) return;
    const apply = () => {
      const invalid =
        ((x === 'end' || (typeof x === 'number' && x > 0)) && el.scrollWidth <= el.clientWidth) ||
        ((y === 'end' || (typeof y === 'number' && y > 0)) && el.scrollHeight <= el.clientHeight);
      report?.set(address, invalid ? '스크롤 범위가 없습니다: ' + address : undefined);
      if (x === 'end' || typeof x === 'number') el.scrollLeft = x === 'end' ? el.scrollWidth : x;
      if (y === 'end' || typeof y === 'number') el.scrollTop = y === 'end' ? el.scrollHeight : y;
    };
    apply();
    let cancelled = false;
    const suspend = () => {
      cancelled = true;
    };
    let raf = 0;
    const afterLayout = () => {
      if (cancelled) return;
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(() => {
        if (!cancelled) apply();
      });
    };
    const resize = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(afterLayout);
    resize?.observe(el);
    for (const child of el.children) resize?.observe(child);
    el.addEventListener('wheel', suspend, { passive: true });
    el.addEventListener('pointerdown', suspend, { passive: true });
    el.addEventListener('keydown', suspend);
    const fonts = availableFonts(document);
    fonts?.addEventListener('loadingdone', afterLayout);
    void fonts?.ready.then(afterLayout);
    afterLayout();
    return () => {
      cancelled = true;
      cancelAnimationFrame(raf);
      fonts?.removeEventListener('loadingdone', afterLayout);
      resize?.disconnect();
      report?.set(address, undefined);
      el.removeEventListener('wheel', suspend);
      el.removeEventListener('pointerdown', suspend);
      el.removeEventListener('keydown', suspend);
    };
  }, [ref, x, y, address, report]);
}
function collectFonts(node: DesignOccurrence, ids: Map<string, { id: string; face: number }>) {
  function add(value: DesignJson | undefined) {
    const font = asObject(value);
    if (!validAssetId(font.asset)) return;
    const face = asNumber(font.face) ?? 0;
    ids.set(designFontFaceName(font.asset, face), { id: font.asset, face });
  }
  const queue: DesignJson[] = [node.node];
  while (queue.length) {
    const value = queue.pop();
    if (!value || typeof value !== 'object') continue;
    if (Array.isArray(value)) {
      queue.push(...(value as readonly DesignJson[]));
      continue;
    }
    const obj = asObject(value);
    add(obj.font);
    if (Array.isArray(obj.fallback)) for (const font of obj.fallback as readonly DesignJson[]) add(font);
    for (const child of Object.values(obj))
      if (child !== null && typeof child === 'object') queue.push(child);
  }
  for (const child of node.children) collectFonts(child, ids);
  if (node.maskSource) collectFonts(node.maskSource, ids);
}

const pendingFonts = new WeakMap<HTMLElement, Promise<string[]>>();
export async function designAssetFontsReady(canvas: HTMLElement): Promise<string[]> {
  return (await pendingFonts.get(canvas)) ?? [];
}
export function useDesignFonts(
  canvas: RefObject<HTMLElement | null>,
  layer: DesignOccurrence | null,
  assetUrl: (id: string) => string,
  warnings: ScrollDiagnostics,
) {
  const scope = useId().replace(/[^a-zA-Z0-9_-]/g, '');
  useLayoutEffect(() => {
    const el = canvas.current,
      fonts = availableFonts(document);
    if (!layer || !el) return;
    const ids = new Map<string, { id: string; face: number }>();
    collectFonts(layer, ids);
    const registered: FontFace[] = [];
    let live = true;
    async function load(family: string, file: { id: string; face: number }): Promise<string | null> {
      const targetName = family + '-' + scope;
      const url = assetUrl(file.id);
      if (url && fonts && typeof FontFace !== 'undefined') {
        const font = new FontFace(
          targetName,
          'url(' + JSON.stringify(url + (file.face > 0 ? `#${file.face + 1}` : '')) + ')',
        );
        // Register before requesting the face so document.fonts.ready observes the pending load.
        fonts.add(font);
        registered.push(font);
        el?.style.setProperty('--' + family, JSON.stringify(targetName));
        try {
          await font.load();
          return null;
        } catch {
          /* The stored fallback stack still applies. */
        }
      }
      const message = '첨부 글꼴을 불러오지 못했습니다: ' + file.id;
      if (live) warnings.set('font:' + family, message);
      return message;
    }
    const ready = Promise.all(Array.from(ids, ([family, file]) => load(family, file))).then((messages) =>
      messages.filter((m): m is string => m !== null),
    );
    pendingFonts.set(el, ready);
    return () => {
      live = false;
      if (pendingFonts.get(el) === ready) pendingFonts.delete(el);
      for (const f of registered) fonts?.delete(f);
      for (const family of ids.keys()) {
        el.style.removeProperty('--' + family);
        warnings.set('font:' + family, undefined);
      }
    };
  }, [canvas, layer, assetUrl, scope, warnings]);
}
