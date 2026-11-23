import type { DesignObject } from '../../../../../../../domain/document/designBlock';
import { numberValue, paintColor } from './markupStyle';
import { identifier, requireMarkup, type Fields } from './markupTree';

type Point = { x: number; y: number };
export function svgRotation(raw: string | undefined): Fields {
  if (raw === undefined) return {};
  const angle = /^rotate\(\s*(-?(?:\d+(?:\.\d+)?|\.\d+))\s*\)$/.exec(raw)?.[1];
  requireMarkup(angle !== undefined, 'SVG 회전은 rotate(각도)로 지정해 주세요.');
  const theta = (numberValue(angle) / 180) * Math.PI;
  requireMarkup(Number.isFinite(theta), '유한한 회전 각도가 필요합니다.');
  return {
    localTransform: { matrix: [Math.cos(theta), Math.sin(theta), -Math.sin(theta), Math.cos(theta), 0, 0] },
  };
}
type Vertex = Point & { in?: Point; out?: Point };
function coordinates(raw: string): (string | number)[] {
  const values: (string | number)[] = [];
  const token = /\s+|,|[MLCZ]|-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?/gy;
  let offset = 0;
  while (offset < raw.length) {
    const match = token.exec(raw);
    requireMarkup(match, '지원하는 SVG 경로 명령은 M, L, C, Z입니다. 위치 ' + String(offset));
    const v = match[0];
    offset = token.lastIndex;
    if (v === ',' || !v.trim()) continue;
    if (/^[MLCZ]$/.test(v)) values.push(v);
    else {
      const n = Number(v);
      requireMarkup(Number.isFinite(n), '유한한 SVG 좌표가 필요합니다.');
      values.push(n);
    }
  }
  return values;
}
function contour(a: Record<string, string>): DesignObject {
  const v = coordinates(a.d ?? '');
  const points: Vertex[] = [];
  let i = 0,
    command: string | undefined,
    closed = false;
  function point(): Point {
    const x = v[i++],
      y = v[i++];
    requireMarkup(typeof x === 'number' && typeof y === 'number', 'SVG 경로의 x, y 좌표가 필요합니다.');
    return { x, y };
  }
  while (i < v.length) {
    const t = v[i];
    if (typeof t === 'string') {
      command = t;
      i++;
    }
    requireMarkup(
      command && (points.length ? command !== 'M' : command === 'M'),
      'SVG path마다 M으로 시작하는 윤곽 한 개를 지정해 주세요.',
    );
    if (command === 'Z') {
      requireMarkup(i === v.length, 'Z 뒤에는 새 path를 사용해 주세요.');
      closed = true;
      break;
    }
    const p = point();
    if (command === 'C') {
      const q = point(),
        end = point(),
        previous = points.at(-1);
      requireMarkup(previous, 'C 이전에 시작점이 필요합니다.');
      previous.out = { x: p.x - previous.x, y: p.y - previous.y };
      points.push({ ...end, in: { x: q.x - end.x, y: q.y - end.y } });
    } else {
      points.push(p);
      command = 'L';
    }
  }
  requireMarkup(points.length, '빈 SVG 경로입니다.');
  const ids =
    a['data-points'] === undefined
      ? points.map((_, j) => 'v' + String(j + 1))
      : a['data-points'].trim().split(/\s+/).map(identifier);
  requireMarkup(
    ids.length === points.length && new Set(ids).size === ids.length,
    'SVG 꼭짓점 ID의 개수 또는 중복을 확인해 주세요.',
  );
  return {
    id: identifier(a['data-contour-key'] ?? 'contour'),
    closed,
    vertices: points.map((p, j) => ({ id: ids[j] as string, ...p })),
  };
}
export function pathNode(a: Record<string, string>, tokens: Set<string>): DesignObject {
  const rule = a['fill-rule'] ?? 'nonzero';
  requireMarkup(rule === 'nonzero' || rule === 'evenodd', '지원하지 않는 SVG 채움 규칙: ' + rule);
  const fill = a.fill ?? '#000000';
  const style: Fields = { fills: [] };
  if (fill !== 'none')
    style.fills = [
      { id: identifier(a['data-fill-key'] ?? 'fill'), kind: 'solid', color: paintColor(fill, tokens) },
    ];
  else requireMarkup(!('data-fill-key' in a), '빈 채움에는 ID를 지정할 수 없습니다.');
  if (a.stroke && a.stroke !== 'none') {
    const width = numberValue(a['stroke-width'] ?? '1');
    requireMarkup(width >= 0, '선 두께는 음수가 될 수 없습니다.');
    const stroke: Fields = {
      id: identifier(a['data-stroke-key'] ?? 'stroke'),
      width,
      paint: {
        id: identifier(a['data-ink-key'] ?? 'ink'),
        kind: 'solid',
        color: paintColor(a.stroke, tokens),
      },
    };
    const cap = a['stroke-linecap'],
      join = a['stroke-linejoin'];
    if (cap !== undefined) {
      requireMarkup(['round', 'butt', 'square'].includes(cap), '지원하지 않는 SVG 선끝: ' + cap);
      stroke.cap = cap;
    }
    if (join !== undefined) {
      requireMarkup(['round', 'miter', 'bevel'].includes(join), '지원하지 않는 SVG 이음: ' + join);
      stroke.join = join;
    }
    style.strokes = [stroke];
  } else {
    for (const k of [
      'stroke-width',
      'stroke-linecap',
      'stroke-linejoin',
      'data-stroke-key',
      'data-ink-key',
    ]) {
      requireMarkup(!(k in a), '선이 없는 경로의 속성: ' + k);
    }
  }
  return { kind: 'vector', fillRule: rule, contours: [contour(a)], style, ...svgRotation(a.transform) };
}
