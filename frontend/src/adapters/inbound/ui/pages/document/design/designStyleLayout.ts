import type { CSSProperties } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject } from '../../../../../../domain/flow/designValue';
import { keyword, length, numbers, type StyleValue } from './designStyleValues';

const align = (v: StyleValue) => keyword(v, 'start', 'end', 'center', 'baseline', 'stretch');
const distribute = (v: StyleValue) => {
  if (v === 'between' || v === 'around' || v === 'evenly') return `space-${v}` as const;
  return keyword(v, 'start', 'end', 'center');
};

function track(v: StyleValue): string | undefined {
  const fixed = length(v) ?? keyword(v, 'auto', 'min-content', 'max-content');
  if (fixed) return fixed;
  const t = asObject(v);
  const fr = asNumber(t.fr);
  const min = length(t.min);
  const max = fr === undefined ? trackLimit(t.max) : `${fr}fr`;
  if (min && max) return `minmax(${min},${max})`;
  return fr === undefined ? undefined : max;
}

function trackLimit(v: StyleValue): string | undefined {
  const fr = asNumber(asObject(v).fr);
  return fr === undefined ? (length(v) ?? keyword(v, 'min-content', 'max-content')) : `${fr}fr`;
}

function tracks(v: StyleValue): string | undefined {
  if (!Array.isArray(v)) return undefined;
  const list = v.map(track);
  return list.every((x) => x !== undefined) ? list.join(' ') : undefined;
}

function gridLine(v: StyleValue): string | undefined {
  if (v === 'all') return '1 / -1';
  const cell = asObject(v);
  const start = asNumber(cell.start);
  const span = asNumber(cell.span);
  if (span !== undefined) return `${start ?? 'auto'} / span ${span}`;
  return start?.toString();
}

function containerStyle(layout: DesignObject): CSSProperties {
  const mode = layout.mode;
  const flex = mode === 'row' || mode === 'column';
  const padding = asObject(layout.padding);
  return {
    display: flex ? 'flex' : mode === 'grid' ? 'grid' : undefined,
    flexDirection: flex ? (layout.reverse === true ? `${mode}-reverse` : mode) : undefined,
    flexWrap: flex && layout.wrap === true ? 'wrap' : undefined,
    alignItems: align(layout.align),
    alignContent: distribute(layout.alignContent),
    justifyContent: distribute(layout.justify),
    justifyItems: align(layout.justifyItems),
    gap: length(layout.gap),
    rowGap: length(layout.rowGap),
    columnGap: length(layout.columnGap),
    gridTemplateColumns: tracks(layout.columns),
    gridTemplateRows: tracks(layout.rows),
    gridAutoColumns: track(layout.autoColumns),
    gridAutoRows: track(layout.autoRows),
    gridAutoFlow: keyword(layout.autoFlow, 'row', 'column', 'row-dense', 'column-dense')?.replace('-', ' '),
    paddingTop: length(padding.top),
    paddingRight: length(padding.right),
    paddingBottom: length(padding.bottom),
    paddingLeft: length(padding.left),
  };
}

function dimension(size: DesignObject, main: boolean): string | undefined {
  if (size.mode === 'fixed') return length(size.px);
  const percent = asNumber(size.percent);
  if (size.mode === 'percent' && percent !== undefined) return `${percent}%`;
  return size.mode === 'fill' && !main ? '100%' : undefined;
}

function axis(position: StyleValue, size: DesignObject, main: boolean) {
  const a = asObject(position),
    scaled = asObject(a.scale);
  const parent = asNumber(scaled.parentExtent),
    start = asNumber(scaled.start),
    extent = asNumber(scaled.extent);
  if (parent && start !== undefined && extent !== undefined) {
    return {
      start: `${(100 * start) / parent}%`,
      end: undefined,
      size: `${(100 * extent) / parent}%`,
      centered: false,
    };
  }
  const center = length(a.center);
  return {
    start: center ? `calc(50% + ${center})` : length(a.start),
    end: length(a.end),
    size: a.start !== undefined && a.end !== undefined ? undefined : dimension(size, main),
    centered: !!center,
  };
}

export function layoutStyle(n: DesignObject, parentMode?: string): CSSProperties {
  const sz = asObject(n.size),
    sx = asObject(sz.x),
    sy = asObject(sz.y),
    pos = asObject(n.position);
  const mainX = parentMode === 'row' && pos.mode !== 'absolute',
    mainY = parentMode === 'column' && pos.mode !== 'absolute';
  const x = axis(pos.x, sx, mainX),
    y = axis(pos.y, sy, mainY),
    flex = asObject(n.flexItem),
    cell = asObject(n.cell);
  const axisMode = mainX ? sx.mode : mainY ? sy.mode : undefined;
  const margin = asObject(n.margin),
    optMargin = (v: StyleValue) => (v === 'auto' ? 'auto' : length(v));
  const tr = asObject(asObject(n.style).transform),
    origin = asObject(tr.origin);
  const matrix = numbers(asObject(n.localTransform).matrix, 6);
  const dx = length(tr.translateX),
    dy = length(tr.translateY),
    scale = asNumber(tr.scale);
  const transform = [
    x.centered ? 'translateX(-50%)' : '',
    y.centered ? 'translateY(-50%)' : '',
    dx ? `translateX(${dx})` : '',
    dy ? `translateY(${dy})` : '',
    scale !== undefined ? `scale(${scale})` : '',
    matrix ? `matrix(${matrix.join(',')})` : '',
  ]
    .filter(Boolean)
    .join(' ');
  return {
    ...containerStyle(asObject(n.layout)),
    boxSizing: n.boxSizing === 'content-box' ? 'content-box' : 'border-box',
    position: keyword(pos.mode, 'absolute', 'sticky') ?? 'relative',
    left: x.start,
    right: x.end,
    top: y.start,
    bottom: y.end,
    width: x.size,
    height: y.size,
    minWidth: length(sx.min),
    maxWidth: length(sx.max),
    minHeight: length(sy.min),
    maxHeight: length(sy.max),
    aspectRatio: asNumber(n.aspectRatio),
    flexGrow: asNumber(flex.grow) ?? (axisMode === 'fill' ? 1 : undefined),
    flexShrink: asNumber(flex.shrink),
    flexBasis:
      flex.basis === 'auto' ? 'auto' : (length(flex.basis) ?? (axisMode === 'fill' ? '0px' : undefined)),
    alignSelf: align(n.alignSelf),
    gridColumn: gridLine(cell.column),
    gridRow: gridLine(cell.row),
    marginTop: optMargin(margin.top),
    marginLeft: optMargin(margin.left),
    marginRight: optMargin(margin.right),
    marginBottom: optMargin(margin.bottom),
    transform: transform || undefined,
    transformOrigin: matrix
      ? '0 0'
      : length(origin.x) && length(origin.y)
        ? `${length(origin.x) ?? ''} ${length(origin.y) ?? ''}`
        : undefined,
  };
}
