import type { CSSProperties } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects } from '../../../../../../domain/flow/designValue';
import {
  blendMode,
  colorWithOpacity,
  designColor,
  keyword,
  length,
  type StyleValue,
} from './designStyleValues';

const geometryKinds = ['shape', 'vector', 'boolean'];
const applied = (v: StyleValue) => asObjects(v).filter((x) => x.visible !== false);

function border(v: StyleValue) {
  const b = asObject(v);
  const width = length(b.width),
    color = designColor(b.color);
  if (!width || !color) return undefined;
  return `${width} ${keyword(b.line, 'solid', 'dashed', 'dotted') ?? 'solid'} ${color}`;
}

export function usesVectorSurface(n: DesignObject): boolean {
  if (n.kind !== 'box' && n.kind !== 'group') return false;
  const st = asObject(n.style);
  const paints = applied(st.fills);
  const strokes = applied(st.strokes);
  return (
    paints.length > 1 ||
    paints.some((f) => f.kind !== 'solid' || (f.blend !== undefined && f.blend !== 'normal')) ||
    strokes.length > 1 ||
    strokes.some(
      (l) =>
        asObject(l.paint).kind !== 'solid' ||
        l.dash !== undefined ||
        l.nonScaling === true ||
        (l.blend !== undefined && l.blend !== 'normal'),
    )
  );
}

export function backgroundStyle(node: DesignObject): CSSProperties {
  const st = asObject(node.style);
  if (geometryKinds.includes(typeof node.kind === 'string' ? node.kind : '')) return {};
  const complex = usesVectorSurface(node);
  const fill = asObject(st.fill);
  const stops = asObjects(fill.stops);
  const images: string[] = [];
  const dot = asObject(st.backgroundPattern),
    dotColor = designColor(dot.color),
    rad = length(dot.radius);
  if (dot.kind === 'dots' && dotColor && rad)
    images.push(`radial-gradient(${dotColor} ${rad}, transparent ${rad})`);
  const angle = asNumber(fill.angle);
  if (angle !== undefined && stops.length > 1) {
    const positions = stops.flatMap((s) => {
      const color = designColor(s.color),
        at = asNumber(s.at);
      return color && at !== undefined ? [`${color} ${at * 100}%`] : [];
    });
    if (positions.length > 1) images.push(`linear-gradient(${angle}deg, ${positions.join(', ')})`);
  }
  const top = applied(st.fills).at(-1);
  const backgroundColor = !complex && top ? colorWithOpacity(top.color, top.opacity) : designColor(st.fill);
  const tileWidth = length(dot.tileWidth),
    tileHeight = length(dot.tileHeight);
  const edges = asObject(st.border),
    r = asObject(st.radius);
  return {
    backgroundColor,
    backgroundImage: images.length ? images.join(',') : undefined,
    backgroundSize: dot.kind === 'dots' && tileWidth && tileHeight ? `${tileWidth} ${tileHeight}` : undefined,
    borderTop: border(edges.top),
    borderRight: border(edges.right),
    borderBottom: border(edges.bottom),
    borderLeft: border(edges.left),
    borderTopLeftRadius: length(r.topLeft),
    borderTopRightRadius: length(r.topRight),
    borderBottomLeftRadius: length(r.bottomLeft),
    borderBottomRightRadius: length(r.bottomRight),
  };
}

function shadowString(e: DesignObject) {
  const color = colorWithOpacity(e.color, e.opacity);
  const x = length(e.x),
    y = length(e.y),
    blur = length(e.blur);
  if (!color || !x || !y || !blur) return undefined;
  return `${e.kind === 'innerShadow' ? 'inset ' : ''}${x} ${y} ${blur} ${length(e.spread) ?? '0px'} ${color}`;
}

export function compositeStyle(n: DesignObject): CSSProperties {
  const st = asObject(n.style),
    filters: string[] = [],
    backgroundFilters: string[] = [],
    shadows: string[] = [];
  const vector = geometryKinds.includes(typeof n.kind === 'string' ? n.kind : '');
  // A design stroke is painted without changing the layout width.
  if (!vector && !usesVectorSurface(n))
    for (const l of applied(st.strokes).toReversed()) {
      const p = asObject(l.paint),
        w = asNumber(l.width),
        color = colorWithOpacity(p.color, (asNumber(p.opacity) ?? 1) * (asNumber(l.opacity) ?? 1));
      if (!color || w === undefined || l.visible === false) continue;
      const edge = l.align === 'center' || l.align === undefined ? w / 2 : w;
      if (l.align !== 'outside') shadows.push(`inset 0 0 0 ${edge}px ${color}`);
      if (l.align !== 'inside') shadows.push(`0 0 0 ${edge}px ${color}`);
    }
  const effects = applied(st.effects);
  for (const e of effects) {
    const radius = length(e.radius);
    if (e.kind === 'layerBlur' && radius) filters.push(`blur(${radius})`);
    if (e.kind === 'backgroundBlur' && radius) backgroundFilters.push(`blur(${radius})`);
    if (e.kind === 'dropShadow' || e.kind === 'innerShadow') {
      const text = shadowString(e);
      if (e.kind === 'dropShadow' && e.surface !== 'box' && (asNumber(e.spread) ?? 0) === 0) {
        const color = colorWithOpacity(e.color, e.opacity),
          blur = asNumber(e.blur) ?? 0;
        if (color)
          filters.push(`drop-shadow(${length(e.x) ?? '0px'} ${length(e.y) ?? '0px'} ${blur / 2}px ${color})`);
      } else if (text) shadows.push(text);
    }
  }
  for (const e of asObjects(st.shadows)) {
    const shadow = shadowString(e);
    if (shadow) shadows.push(shadow);
  }
  const ov = asObject(st.overflow),
    overflow = (v: StyleValue) => keyword(v, 'visible', 'clip', 'scroll', 'auto');
  return {
    boxShadow: shadows.length ? shadows.join(', ') : undefined,
    filter: filters.length ? filters.join(' ') : undefined,
    backdropFilter: backgroundFilters.length ? backgroundFilters.join(' ') : undefined,
    overflow: overflow(st.overflow) ?? (n.clipContent === true ? 'clip' : undefined),
    overflowX: overflow(ov.x),
    overflowY: overflow(ov.y),
    opacity: asNumber(st.opacity),
    zIndex: asNumber(st.zIndex),
    mixBlendMode: blendMode(st.blend),
    isolation: st.isolate === true ? 'isolate' : undefined,
  };
}
