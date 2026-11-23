import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects, asText } from '../../../../../../domain/flow/designValue';

type Point = { x: number; y: number };
export type GeometryBounds = { x: number; y: number; width: number; height: number };
export type PathOutline = { d: string; rule: 'nonzero' | 'evenodd'; fills?: readonly DesignObject[] };
const number = (v: DesignJson | undefined) => asNumber(v) ?? 0;
const point = (v: DesignJson | undefined): Point => {
  const o = asObject(v);
  return { x: number(o.x), y: number(o.y) };
};
const plus = (a: Point, b: Point): Point => ({ x: a.x + b.x, y: a.y + b.y });
const xy = (p: Point) => `${p.x} ${p.y}`;
const tau = Math.PI * 2;
const radians = (n: number) => (n * Math.PI) / 180;
const rule = (o: DesignObject): 'nonzero' | 'evenodd' => (o.fillRule === 'evenodd' ? 'evenodd' : 'nonzero');

function extrema(a: number, b: number, c: number, d: number): number[] {
  const aa = -a + 3 * b - 3 * c + d,
    bb = 2 * (a - 2 * b + c),
    cc = b - a;
  if (Math.abs(aa) < 1e-10) return Math.abs(bb) < 1e-10 ? [] : [-cc / bb];
  const disc = bb * bb - 4 * aa * cc;
  return disc < 0 ? [] : [(-bb + Math.sqrt(disc)) / (2 * aa), (-bb - Math.sqrt(disc)) / (2 * aa)];
}
function cubicAt(a: number, b: number, c: number, d: number, t: number) {
  const u = 1 - t;
  return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d;
}
function cubicExtents(p: Point, a: Point, b: Point, q: Point): Point[] {
  return [...extrema(p.x, a.x, b.x, q.x), ...extrema(p.y, a.y, b.y, q.y)]
    .filter((t) => t > 0 && t < 1)
    .map((t) => ({ x: cubicAt(p.x, a.x, b.x, q.x, t), y: cubicAt(p.y, a.y, b.y, q.y, t) }));
}
function arcExtents(p: Point, q: Point, arc: DesignObject): Point[] {
  let rx = Math.abs(number(arc.rx)),
    ry = Math.abs(number(arc.ry));
  if (!rx || !ry || (p.x === q.x && p.y === q.y)) return [];
  const angle = radians(number(arc.rotation)),
    c = Math.cos(angle),
    s = Math.sin(angle);
  const dx = (p.x - q.x) / 2,
    dy = (p.y - q.y) / 2,
    x = c * dx + s * dy,
    y = -s * dx + c * dy;
  const scale = Math.max(1, Math.hypot(x / rx, y / ry));
  rx *= scale;
  ry *= scale;
  const z = (x * x) / (rx * rx) + (y * y) / (ry * ry);
  const factor = (arc.large === arc.clockwise ? -1 : 1) * Math.sqrt(Math.max(0, (1 - z) / z));
  const cx = factor * ((rx * y) / ry),
    cy = -factor * ((ry * x) / rx);
  const center = { x: c * cx - s * cy + (p.x + q.x) / 2, y: s * cx + c * cy + (p.y + q.y) / 2 };
  const start = Math.atan2((y - cy) / ry, (x - cx) / rx),
    end = Math.atan2((-y - cy) / ry, (-x - cx) / rx);
  const dir = arc.clockwise === true ? 1 : -1,
    travel = (phi: number) => (((dir * (phi - start)) % tau) + tau) % tau;
  const ax = Math.atan2(-s * ry, c * rx),
    ay = Math.atan2(c * ry, s * rx);
  return [ax, ax + Math.PI, ay, ay + Math.PI]
    .filter((t) => travel(t) <= travel(end) + 1e-12)
    .map((t) => ({
      x: center.x + c * rx * Math.cos(t) - s * ry * Math.sin(t),
      y: center.y + s * rx * Math.cos(t) + c * ry * Math.sin(t),
    }));
}

class Path {
  readonly commands: string[] = [];
  bounds: GeometryBounds | undefined;
  private current: Point = { x: 0, y: 0 };
  private include(p: Point) {
    const b = this.bounds;
    if (!b) {
      this.bounds = { ...p, width: 0, height: 0 };
      return;
    }
    const x = Math.min(b.x, p.x),
      y = Math.min(b.y, p.y);
    this.bounds = {
      x,
      y,
      width: Math.max(b.x + b.width, p.x) - x,
      height: Math.max(b.y + b.height, p.y) - y,
    };
  }
  move(p: Point) {
    this.commands.push('M' + xy(p));
    this.current = p;
    this.include(p);
  }
  line(q: Point) {
    this.commands.push('L' + xy(q));
    this.current = q;
    this.include(q);
  }
  cubic(a: Point, b: Point, q: Point) {
    this.commands.push('C' + [a, b, q].map(xy).join(' '));
    for (const p of cubicExtents(this.current, a, b, q)) this.include(p);
    this.current = q;
    this.include(q);
  }
  arc(arc: DesignObject, q: Point) {
    this.commands.push(
      `A${number(arc.rx)} ${number(arc.ry)} ${number(arc.rotation)} ${arc.large === true ? 1 : 0} ${arc.clockwise === true ? 1 : 0} ${xy(q)}`,
    );
    for (const p of arcExtents(this.current, q, arc)) this.include(p);
    this.current = q;
    this.include(q);
  }
  close() {
    this.commands.push('Z');
  }
  get d() {
    return this.commands.join(' ');
  }
}

function segment(
  out: Path,
  a: DesignObject,
  b: DesignObject,
  outgoing: DesignJson | undefined,
  incoming: DesignJson | undefined,
  arc?: DesignJson,
) {
  if (arc) {
    out.arc(asObject(arc), point(b));
    return;
  }
  if (outgoing || incoming)
    out.cubic(plus(point(a), point(outgoing)), plus(point(b), point(incoming)), point(b));
  else out.line(point(b));
}
function contour(path: Path, c: DesignObject) {
  const vertices = asObjects(c.vertices),
    first = vertices[0];
  if (!first) return;
  path.move(point(first));
  for (let i = 1; i < vertices.length; i++) {
    const a = vertices[i - 1],
      b = vertices[i];
    if (a && b) segment(path, a, b, a.out, b.in, a.arcToNext);
  }
  const last = vertices.at(-1);
  if (c.closed === true && last) {
    segment(path, last, first, last.out, first.in, last.arcToNext);
    path.close();
  }
}
const ellipsePoint = (c: Point, r: Point, theta: number): Point => ({
  x: c.x + r.x * Math.cos(theta),
  y: c.y + r.y * Math.sin(theta),
});
function ellipseSection(out: Path, c: Point, r: Point, start: number, sweep: number) {
  const parts = Math.ceil(Math.abs(sweep) / Math.PI);
  for (let i = 1; i <= parts; i++)
    out.arc(
      { rx: r.x, ry: r.y, rotation: 0, large: false, clockwise: sweep > 0 },
      ellipsePoint(c, r, start + (sweep * i) / parts),
    );
}
function arcShape(out: Path, g: DesignObject) {
  const c = point(g.center),
    r = point(g.radii),
    start = radians(number(g.startAngle)),
    sweep = g.kind === 'ellipse' ? tau : radians(number(g.sweepAngle));
  if (!sweep) return;
  out.move(ellipsePoint(c, r, start));
  ellipseSection(out, c, r, start, sweep);
  if (g.kind === 'arc' && g.closure === 'sector') {
    const ratio = number(g.innerRatio),
      inner = { x: r.x * ratio, y: r.y * ratio };
    if (ratio > 0) {
      out.line(ellipsePoint(c, inner, start + sweep));
      ellipseSection(out, c, inner, start + sweep, -sweep);
    } else if (Math.abs(sweep) < tau) out.line(c);
  }
  if (g.kind === 'ellipse' || g.closure !== 'open') out.close();
}
function rectangle(out: Path, g: DesignObject) {
  const o = point(g.origin),
    w = number(g.width),
    h = number(g.height),
    corners = asObject(g.corners);
  const a = point(corners.topLeft),
    b = point(corners.topRight),
    c = point(corners.bottomRight),
    d = point(corners.bottomLeft);
  const ratios = [
    [w, a.x + b.x],
    [w, d.x + c.x],
    [h, a.y + d.y],
    [h, b.y + c.y],
  ];
  const scale = Math.min(
    1,
    ...ratios.map(([extent, sum]) => (sum && extent !== undefined ? extent / sum : 1)),
  );
  for (const r of [a, b, c, d]) {
    r.x *= scale;
    r.y *= scale;
  }
  const right = o.x + w,
    bottom = o.y + h;
  const turn = (r: Point, p: Point) => {
    if (r.x && r.y) out.arc({ rx: r.x, ry: r.y, clockwise: true, large: false }, p);
    else out.line(p);
  };
  out.move({ x: o.x + a.x, y: o.y });
  out.line({ x: right - b.x, y: o.y });
  turn(b, { x: right, y: o.y + b.y });
  out.line({ x: right, y: bottom - c.y });
  turn(c, { x: right - c.x, y: bottom });
  out.line({ x: o.x + d.x, y: bottom });
  turn(d, { x: o.x, y: bottom - d.y });
  out.line({ x: o.x, y: o.y + a.y });
  turn(a, { x: o.x + a.x, y: o.y });
  out.close();
}
function polygon(out: Path, g: DesignObject) {
  const star = g.kind === 'star',
    n = number(star ? g.points : g.sides) * (star ? 2 : 1);
  if (n < 3 || !Number.isSafeInteger(n)) return;
  const c = point(g.center),
    r = point(g.radii),
    first = radians(number(g.rotation)) - Math.PI / 2;
  for (let i = 0; i < n; i++) {
    const scale = star && i % 2 === 1 ? number(g.innerRatio) : 1;
    const p = ellipsePoint(c, { x: r.x * scale, y: r.y * scale }, first + (tau * i) / n);
    if (i === 0) out.move(p);
    else out.line(p);
  }
  out.close();
}
function shape(g: DesignObject): Path {
  const out = new Path();
  switch (g.kind) {
    case 'rect':
      rectangle(out, g);
      break;
    case 'ellipse':
    case 'arc':
      arcShape(out, g);
      break;
    case 'line':
      out.move(point(g.from));
      out.line(point(g.to));
      break;
    case 'polygon':
    case 'star':
      polygon(out, g);
      break;
  }
  return out;
}

function networkPaths(network: DesignObject) {
  const vertices = new Map(asObjects(network.vertices).map((n) => [asText(n.id), n]));
  const edges = new Map(asObjects(network.segments).map((e) => [asText(e.id), e]));
  const edge = (path: Path, e: DesignObject, reverse: boolean, first: boolean) => {
    const a = vertices.get(asText(reverse ? e.to : e.from)),
      b = vertices.get(asText(reverse ? e.from : e.to));
    if (!a || !b) return;
    if (first) path.move(point(a));
    segment(path, a, b, reverse ? e.in : e.out, reverse ? e.out : e.in);
  };
  const outline = new Path();
  for (const e of edges.values()) edge(outline, e, false, true);
  const regions = asObjects(network.regions).map((region) => {
    const p = new Path();
    if (Array.isArray(region.loops)) {
      for (const loop of region.loops as readonly DesignJson[]) {
        asObjects(loop).forEach((step, i) => {
          const e = edges.get(asText(step.segment));
          if (e) edge(p, e, step.reverse === true, i === 0);
        });
        p.close();
      }
    }
    return {
      d: p.d,
      rule: rule(region),
      fills: region.fills === undefined ? undefined : asObjects(region.fills),
    };
  });
  return { outline, regions };
}

export function geometryPaths(node: DesignObject): {
  edges: string;
  regions: PathOutline[];
  bounds?: GeometryBounds;
} {
  if (node.network) {
    const { outline, regions } = networkPaths(asObject(node.network));
    return { edges: outline.d, regions, bounds: outline.bounds };
  }
  const path = node.kind === 'shape' ? shape(asObject(node.geometry)) : new Path();
  for (const c of asObjects(node.contours)) contour(path, c);
  return { edges: path.d, regions: [{ d: path.d, rule: rule(node) }], bounds: path.bounds };
}

export function geometryMatrix(v: DesignJson | undefined): number[] | undefined {
  return Array.isArray(v) && v.length === 6 && v.every((x) => typeof x === 'number' && Number.isFinite(x))
    ? (v as number[])
    : undefined;
}
export const toSvgMatrix = (v: DesignJson | undefined): string | undefined => {
  const m = geometryMatrix(v);
  return m ? 'matrix(' + m.join(' ') + ')' : undefined;
};
export function explicitViewport(node: DesignObject): GeometryBounds | undefined {
  const v = asObject(node.viewBox);
  const width = asNumber(v.width),
    height = asNumber(v.height);
  return width && height ? { x: number(v.x), y: number(v.y), width, height } : undefined;
}

const padding = (b: GeometryBounds, dx: number, dy: number): GeometryBounds => ({
  x: b.x + dx,
  y: b.y + dy,
  width: b.width,
  height: b.height,
});
function childBounds(n: DesignObject, area: GeometryBounds): GeometryBounds | undefined {
  const b = geometryViewport(n);
  if (!b) return undefined;
  const m = geometryMatrix(asObject(n.localTransform).matrix) ?? [1, 0, 0, 1, 0, 0];
  const corners = [
    [b.x, b.y],
    [b.x + b.width, b.y],
    [b.x, b.y + b.height],
    [b.x + b.width, b.y + b.height],
  ];
  const xs = corners.map(([x = 0, y = 0]) => (m[0] ?? 1) * x + (m[2] ?? 0) * y + (m[4] ?? 0));
  const ys = corners.map(([x = 0, y = 0]) => (m[1] ?? 0) * x + (m[3] ?? 1) * y + (m[5] ?? 0));
  const minX = Math.min(...xs),
    minY = Math.min(...ys);
  const offset = geometryOffset(n, area, b);
  return padding(
    { x: minX, y: minY, width: Math.max(...xs) - minX, height: Math.max(...ys) - minY },
    offset.x,
    offset.y,
  );
}
export function geometryOffset(n: DesignObject, area: GeometryBounds, child: GeometryBounds): Point {
  const pos = asObject(n.position);
  const axis = (value: DesignJson | undefined, size: number, extent: number) => {
    const a = asObject(value);
    const length = (v: DesignJson | undefined) => asNumber(v) ?? (number(asObject(v).percent) * size) / 100;
    if ('start' in a) return length(a.start);
    if ('end' in a) return size - length(a.end) - extent;
    if ('center' in a) return (size - extent) / 2 + length(a.center);
    return 0;
  };
  return { x: axis(pos.x, area.width, child.width), y: axis(pos.y, area.height, child.height) };
}
export function geometryViewport(node: DesignObject): GeometryBounds | undefined {
  const explicit = explicitViewport(node);
  if (explicit) return explicit;
  if (node.kind === 'shape' || node.kind === 'vector') return geometryPaths(node).bounds;
  const items = asObjects(node.kind === 'boolean' ? node.operands : node.children);
  const area = {
    x: 0,
    y: 0,
    width: number(asObject(asObject(node.size).x).px),
    height: number(asObject(asObject(node.size).y).px),
  };
  const bounds = items.map((c) => childBounds(c, area)).filter((b): b is GeometryBounds => b !== undefined);
  if (!bounds.length) return undefined;
  const x = Math.min(...bounds.map((b) => b.x)),
    y = Math.min(...bounds.map((b) => b.y));
  return {
    x,
    y,
    width: Math.max(...bounds.map((b) => b.x + b.width)) - x,
    height: Math.max(...bounds.map((b) => b.y + b.height)) - y,
  };
}
