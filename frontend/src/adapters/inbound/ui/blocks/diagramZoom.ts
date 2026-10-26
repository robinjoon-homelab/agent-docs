/** Position and zoom of a diagram inside the viewer: content point p shows at (x + p.x * scale, y + p.y * scale). */
export type DiagramView = { readonly scale: number; readonly x: number; readonly y: number };
export type Size = { readonly width: number; readonly height: number };
export type Point = { readonly x: number; readonly y: number };

export const MIN_SCALE = 0.1;
export const MAX_SCALE = 8;

function clampScale(scale: number): number {
  return Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
}

/** The whole diagram as large as the frame allows, centered, with padding on the tighter side. */
export function fitView(content: Size, frame: Size, padding: number): DiagramView {
  const scale = clampScale(
    Math.min((frame.width - padding * 2) / content.width, (frame.height - padding * 2) / content.height),
  );
  return {
    scale,
    x: (frame.width - content.width * scale) / 2,
    y: (frame.height - content.height * scale) / 2,
  };
}

/** Zooms by factor while the content under point stays under it, as wheel and pinch zoom are expected to. */
export function zoomAt(view: DiagramView, factor: number, point: Point): DiagramView {
  const scale = clampScale(view.scale * factor);
  if (scale === view.scale) return view;
  const ratio = scale / view.scale;
  return { scale, x: point.x - (point.x - view.x) * ratio, y: point.y - (point.y - view.y) * ratio };
}

/** The drawing's own size from the viewBox mermaid writes on the svg element, or null when it has none. */
export function diagramSize(svg: string): Size | null {
  const viewBox = /<svg[^>]*\sviewBox="([^"]+)"/.exec(svg)?.[1];
  if (viewBox === undefined) return null;
  const [, , width, height] = viewBox
    .trim()
    .split(/[\s,]+/)
    .map(Number);
  if (width === undefined || height === undefined || !(width > 0) || !(height > 0)) return null;
  return { width, height };
}
