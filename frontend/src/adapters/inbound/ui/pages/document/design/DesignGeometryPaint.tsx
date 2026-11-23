import type { CSSProperties, ReactNode } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects } from '../../../../../../domain/flow/designValue';
import { blendMode, designColor, keyword, numbers, validAssetId, type AssetUrl } from './designStyleValues';
import type { GeometryBounds } from './DesignGeometryPaths';
import { toSvgMatrix } from './DesignGeometryPaths';

type FillRule = 'evenodd' | 'nonzero';
export type GeometryPaintProps = { paint: DesignObject; uid: string; assetUrl?: AssetUrl };
type PathLayersProps = {
  d: string;
  rule?: FillRule;
  fills: readonly DesignObject[];
  strokes: readonly DesignObject[];
  uid: string;
  assetUrl?: AssetUrl;
  bounds?: GeometryBounds;
  clipD?: string;
};
const unit = (n: unknown, value = 1) =>
  typeof n === 'number' && Number.isFinite(n) ? Math.max(0, Math.min(1, n)) : value;
function stops(p: DesignObject) {
  return asObjects(p.stops).map((s) => ({ at: unit(s.at, 0), color: designColor(s.color) ?? 'transparent' }));
}
const spread = (p: DesignObject) => keyword(p.spread, 'pad', 'repeat', 'reflect') ?? 'pad';
export const paintValue = (p: DesignObject, uid: string) =>
  p.kind === 'solid' ? (designColor(p.color) ?? 'none') : `url(#${uid})`;
const interpolation = (p: DesignObject) => keyword(p.interpolation, 'srgb', 'srgb-linear', 'oklab') ?? 'srgb';
function sample(p: DesignObject, list: ReturnType<typeof stops>, at: number) {
  const index = list.findIndex((s) => s.at > at);
  if (index < 0) return list.at(-1)?.color ?? 'transparent';
  const b = list[index],
    a = list[index - 1];
  if (!a || !b) return b?.color ?? 'transparent';
  const fraction = (at - a.at) / (b.at - a.at);
  return `color-mix(in ${interpolation(p)}, ${a.color} ${(1 - fraction) * 100}%, ${b.color})`;
}
function gradientStops(p: DesignObject) {
  const list = stops(p);
  return list.flatMap((s, index) => {
    const next = list[index + 1];
    const original = <stop key={`s${index}`} offset={s.at} stopColor={s.color} />;
    if (interpolation(p) !== 'oklab' || !next || next.at <= s.at) return [original];
    // SVG has no OKLab interpolation mode. Sample the authored interpolation on each interval.
    const samples = Array.from({ length: 15 }, (_, i) => {
      const t = s.at + ((i + 1) / 16) * (next.at - s.at);
      return <stop key={`s${index}-${i}`} offset={t} stopColor={sample(p, list, t)} />;
    });
    return [original, ...samples];
  });
}
const transform = (p: DesignObject) => toSvgMatrix(p.matrix);
function diamondPaint(p: DesignObject, uid: string) {
  const regions = [
    [1, 1],
    [1, -1],
    [-1, -1],
    [-1, 1],
  ];
  return (
    <pattern
      id={uid}
      width="1"
      height="1"
      patternContentUnits="objectBoundingBox"
      patternTransform={transform(p)}
    >
      {regions.map(([dx = 1, dy = 1], i) => {
        const cornerX = (1 + dx) / 2,
          cornerY = (1 + dy) / 2,
          id = `${uid}-quadrant-${i}`;
        return (
          <g key={i}>
            <linearGradient
              id={id}
              gradientUnits="userSpaceOnUse"
              x1={0.5}
              y1={0.5}
              x2={0.5 + dx * 0.25}
              y2={0.5 + dy * 0.25}
              spreadMethod={spread(p)}
              colorInterpolation={interpolation(p) === 'srgb-linear' ? 'linearRGB' : 'sRGB'}
            >
              {gradientStops(p)}
            </linearGradient>
            <path d={`M.5 .5H${cornerX}V${cornerY}H.5Z`} fill={`url(#${id})`} />
          </g>
        );
      })}
    </pattern>
  );
}
function angularPaint(p: DesignObject, uid: string) {
  const list = stops(p);
  const stopsAt = [
    ...new Set([...Array.from({ length: 361 }, (_, i) => i / 360), ...list.map((s) => s.at)]),
  ].sort((a, b) => a - b);
  const outer = (a: number) => [0.5 + Math.cos(a * Math.PI * 2), 0.5 + Math.sin(a * Math.PI * 2)].join(' ');
  return (
    <pattern
      id={uid}
      width="1"
      height="1"
      patternContentUnits="objectBoundingBox"
      patternTransform={transform(p)}
    >
      {stopsAt.slice(0, -1).map((a, i) => {
        const b = stopsAt[i + 1] ?? 1;
        return (
          <path
            key={a}
            d={`M.5 .5L${outer(a)}L${outer(b)}Z`}
            fill={sample(p, list, (a + b) / 2)}
            shapeRendering="crispEdges"
          />
        );
      })}
    </pattern>
  );
}
const anchor = (v: unknown, x: boolean) =>
  typeof v === 'number' && v === 0
    ? x
      ? 'xMin'
      : 'YMin'
    : v === 1
      ? x
        ? 'xMax'
        : 'YMax'
      : x
        ? 'xMid'
        : 'YMid';
function imagePaint({ paint, uid, assetUrl }: GeometryPaintProps) {
  const asset = asObject(paint.image).asset;
  if (!validAssetId(asset) || !assetUrl) return null;
  const href = assetUrl(asset),
    p = asObject(paint.placement);
  if (p.kind === 'tile') {
    const size = numbers(p.size, 2),
      shift = numbers(p.offset, 2);
    if (!size) return null;
    return (
      <pattern
        id={uid}
        patternUnits="userSpaceOnUse"
        x={shift?.[0]}
        y={shift?.[1]}
        width={size[0]}
        height={size[1]}
        patternTransform={`rotate(${asNumber(p.rotation) ?? 0})`}
      >
        <image href={href} width={size[0]} height={size[1]} preserveAspectRatio="none" />
      </pattern>
    );
  }
  const a = numbers(p.anchor, 2);
  const aspect =
    p.mode === 'stretch' || p.kind === 'crop'
      ? 'none'
      : anchor(a?.[0], true) + anchor(a?.[1], false) + (p.mode === 'cover' ? ' slice' : ' meet');
  return (
    <pattern id={uid} width="1" height="1" patternContentUnits="objectBoundingBox">
      <image
        href={href}
        width="1"
        height="1"
        preserveAspectRatio={aspect}
        transform={p.kind === 'crop' ? transform(p) : undefined}
      />
    </pattern>
  );
}
export function DesignPaintDefinition({ paint, uid, assetUrl }: GeometryPaintProps): ReactNode {
  const shared = {
    id: uid,
    gradientTransform: transform(paint),
    spreadMethod: spread(paint),
    colorInterpolation: interpolation(paint) === 'srgb-linear' ? 'linearRGB' : 'sRGB',
  } as const;
  switch (paint.kind) {
    case 'linear':
      return (
        <linearGradient {...shared} x1="0" y1=".5" x2="1" y2=".5">
          {gradientStops(paint)}
        </linearGradient>
      );
    case 'radial':
      return (
        <radialGradient {...shared} cx=".5" cy=".5" r=".5">
          {gradientStops(paint)}
        </radialGradient>
      );
    case 'angular':
      return angularPaint(paint, uid);
    case 'diamond':
      return diamondPaint(paint, uid);
    case 'image':
      return imagePaint({ paint, uid, assetUrl });
    default:
      return null;
  }
}
function outsideArea(b: GeometryBounds | undefined, w: number) {
  if (!b) return { x: '-200%', y: '-200%', width: '500%', height: '500%' } as const;
  const extra = w + 1;
  return { x: b.x - extra, y: b.y - extra, width: b.width + 2 * extra, height: b.height + 2 * extra };
}
export function DesignPathLayers({
  d,
  rule = 'nonzero',
  fills,
  strokes,
  uid,
  assetUrl,
  bounds,
  clipD = d,
}: PathLayersProps) {
  const activeFills = fills.filter((f) => f.visible !== false),
    activeStrokes = strokes.filter((s) => s.visible !== false);
  if (!d) return null;
  return (
    <>
      {activeFills.map((p, i) => {
        const id = `${uid}-f${i}`;
        return (
          <g key={id} opacity={unit(p.opacity)} style={{ mixBlendMode: blendMode(p.blend) }}>
            <defs>
              <DesignPaintDefinition uid={id} paint={p} assetUrl={assetUrl} />
            </defs>
            <path d={d} fillRule={rule} fill={paintValue(p, id)} stroke="none" />
          </g>
        );
      })}
      {activeStrokes.map((s, i) => {
        const p = asObject(s.paint);
        if (p.visible === false) return null;
        const id = `${uid}-s${i}`,
          mask = id + '-out',
          clip = id + '-in',
          w = asNumber(s.width) ?? 1,
          inside = s.align === 'inside',
          outside = s.align === 'outside';
        const area = outsideArea(bounds, w),
          dash = Array.isArray(s.dash)
            ? s.dash.filter((v) => typeof v === 'number' && Number.isFinite(v)).join(' ')
            : undefined;
        const style: CSSProperties = { mixBlendMode: blendMode(s.blend) ?? blendMode(p.blend) };
        return (
          <g key={id} opacity={unit(s.opacity) * unit(p.opacity)} style={style}>
            <defs>
              <DesignPaintDefinition uid={id} paint={p} assetUrl={assetUrl} />
              {inside && (
                <clipPath id={clip}>
                  <path d={clipD} clipRule={rule} />
                </clipPath>
              )}
              {outside && (
                <mask id={mask} maskUnits="userSpaceOnUse" {...area} style={{ maskType: 'luminance' }}>
                  <rect {...area} fill="white" />
                  <path d={clipD} fillRule={rule} fill="black" />
                </mask>
              )}
            </defs>
            <path
              d={d}
              fill="none"
              stroke={paintValue(p, id)}
              strokeWidth={inside || outside ? 2 * w : w}
              strokeLinecap={keyword(s.cap, 'round', 'square', 'butt')}
              strokeLinejoin={keyword(s.join, 'round', 'bevel', 'miter')}
              strokeMiterlimit={asNumber(s.miterLimit)}
              strokeDasharray={dash}
              strokeDashoffset={asNumber(s.dashOffset)}
              vectorEffect={s.nonScaling === true ? 'non-scaling-stroke' : undefined}
              clipPath={inside ? `url(#${clip})` : undefined}
              mask={outside ? `url(#${mask})` : undefined}
            />
          </g>
        );
      })}
    </>
  );
}
