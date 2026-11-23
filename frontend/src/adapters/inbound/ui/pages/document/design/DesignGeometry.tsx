import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects, asText } from '../../../../../../domain/flow/designValue';
import { blendMode, type AssetUrl } from './designStyleValues';
import { DesignPaintDefinition, DesignPathLayers, paintValue } from './DesignGeometryPaint';
import {
  geometryOffset,
  geometryPaths,
  geometryViewport,
  toSvgMatrix,
  type GeometryBounds,
} from './DesignGeometryPaths';

export { geometryViewport } from './DesignGeometryPaths';
export type { GeometryBounds } from './DesignGeometryPaths';

const reference = (id: string) => `url(#${id})`;
const region = (b: GeometryBounds, margin = 0) => ({
  x: b.x - margin,
  y: b.y - margin,
  width: b.width + 2 * margin,
  height: b.height + 2 * margin,
});
const boxPath = (b: GeometryBounds) => `M${b.x} ${b.y}h${b.width}v${b.height}h${-b.width}Z`;
const defaultArea: GeometryBounds = { x: 0, y: 0, width: 1, height: 1 };

function fillsOf(style: DesignObject): DesignObject[] {
  if (style.fills !== undefined) return asObjects(style.fills);
  if (style.fill === undefined) return [];
  const f = asObject(style.fill);
  if (!Array.isArray(f.stops)) return [{ kind: 'solid', color: style.fill }];
  const a = ((asNumber(f.angle) ?? 90) * Math.PI) / 180,
    dx = Math.sin(a),
    dy = -Math.cos(a);
  return [
    { kind: 'linear', stops: f.stops, matrix: [dx, dy, -dy, dx, (1 - dx + dy) / 2, (1 - dx - dy) / 2] },
  ];
}
function amount(value: DesignJson | undefined, size: number) {
  return asNumber(value) ?? ((asNumber(asObject(value).percent) ?? 0) * size) / 100;
}
export function geometryPlacement(node: DesignObject, area: GeometryBounds) {
  const natural = geometryViewport(node) ?? defaultArea,
    size = asObject(node.size);
  const w = asNumber(asObject(size.x).px) ?? natural.width,
    h = asNumber(asObject(size.y).px) ?? natural.height;
  const offset = geometryOffset(node, area, { ...natural, width: w, height: h });
  const style = asObject(asObject(node.style).transform),
    origin = asObject(style.origin),
    trans = [];
  trans.push(`translate(${offset.x} ${offset.y})`);
  const matrix = toSvgMatrix(asObject(node.localTransform).matrix);
  if (matrix) trans.push(matrix);
  else {
    const ox = amount(origin.x, w),
      oy = amount(origin.y, h);
    trans.push(
      `translate(${ox} ${oy}) translate(${amount(style.translateX, w)} ${amount(style.translateY, h)}) scale(${asNumber(style.scale) ?? 1}) translate(${-ox} ${-oy})`,
    );
  }
  if (node.viewBox && natural.width > 0 && natural.height > 0) {
    let sx = w / natural.width,
      sy = h / natural.height;
    const fit = asObject(node.viewBox).fit;
    if (fit === 'contain' || fit === 'cover') sx = sy = fit === 'cover' ? Math.max(sx, sy) : Math.min(sx, sy);
    trans.push(
      `translate(${(w - natural.width * sx) / 2} ${(h - natural.height * sy) / 2}) scale(${sx} ${sy}) translate(${-natural.x} ${-natural.y})`,
    );
  }
  return trans.join(' ');
}

/** SVG clipPath accepts geometry leaves; group transforms must be projected onto individual paths. */
export function DesignClipPaths({
  node,
  area,
  transform = '',
}: {
  node: DesignObject;
  area: GeometryBounds;
  transform?: string;
}) {
  if (node.visible === false) return null;
  const here = transform + ' ' + geometryPlacement(node, area);
  if (node.kind === 'group')
    return asObjects(node.children).map((c) => (
      <DesignClipPaths key={asText(c.id)} node={c} area={geometryViewport(node) ?? area} transform={here} />
    ));
  return geometryPaths(node).regions.map((r, i) => (
    <path key={i} d={r.d} clipRule={r.rule} transform={here} />
  ));
}
type SilhouetteProps = { node: DesignObject; uid: string; area?: GeometryBounds; placed?: boolean };
export function DesignSilhouette({ node, uid, area = defaultArea, placed = false }: SilhouetteProps) {
  const t = placed ? geometryPlacement(node, area) : undefined;
  if (node.visible === false) return null;
  if (node.kind === 'boolean')
    return (
      <g transform={t}>
        <BooleanSilhouette node={node} uid={uid} />
      </g>
    );
  if (node.kind === 'group')
    return (
      <g transform={t}>
        {asObjects(node.children).map((c, i) => (
          <DesignSilhouette
            key={i}
            node={c}
            uid={`${uid}-g${i}`}
            area={geometryViewport(node) ?? area}
            placed
          />
        ))}
      </g>
    );
  if (node.kind !== 'shape' && node.kind !== 'vector') return null;
  return (
    <g transform={t}>
      {geometryPaths(node).regions.map((r, i) => (
        <path key={i} d={r.d} fill="currentColor" fillRule={r.rule} stroke="none" />
      ))}
    </g>
  );
}

function UseMask({
  uid,
  source,
  area,
  inverse = false,
}: {
  uid: string;
  source: string;
  area: GeometryBounds;
  inverse?: boolean;
}) {
  return (
    <mask id={uid} maskUnits="userSpaceOnUse" {...area} style={{ maskType: 'luminance' }}>
      {inverse && <rect {...area} fill="white" />}
      <use href={'#' + source} color={inverse ? 'black' : 'white'} />
    </mask>
  );
}

function BooleanSilhouette({ node, uid }: { node: DesignObject; uid: string }) {
  const operands = asObjects(node.operands),
    area = geometryViewport(node) ?? defaultArea;
  if (!operands.length) return null;
  const operation = node.operation;
  return (
    <>
      <defs>
        {operands.map((op, i) => {
          const source = `${uid}-op${i}`,
            prev = `${uid}-step${i - 1}`,
            step = `${uid}-step${i}`,
            gate = `${uid}-gate${i}`,
            prior = `${uid}-prior${i}`;
          return (
            <g key={source}>
              <g id={source}>
                <DesignSilhouette node={op} uid={source + '-inside'} area={area} placed />
              </g>
              {i > 0 && operation !== 'union' && (
                <UseMask uid={gate} source={source} area={area} inverse={operation !== 'intersect'} />
              )}
              {i > 0 && operation === 'xor' && <UseMask uid={prior} source={prev} area={area} inverse />}
              <g id={step}>
                {i > 0 && (
                  <use href={'#' + prev} mask={operation === 'union' ? undefined : reference(gate)} />
                )}
                {(i === 0 || operation === 'union' || operation === 'xor') && (
                  <use
                    href={'#' + source}
                    mask={i > 0 && operation === 'xor' ? reference(prior) : undefined}
                  />
                )}
              </g>
            </g>
          );
        })}
      </defs>
      <use href={'#' + `${uid}-step${operands.length - 1}`} />
    </>
  );
}
type GeometryProps = { node: DesignObject; uid: string; assetUrl?: AssetUrl };
function BooleanLayers({ node, uid, assetUrl }: GeometryProps) {
  const bounds = geometryViewport(node) ?? defaultArea,
    style = asObject(node.style),
    shape = uid + '-geometry',
    mask = uid + '-face';
  const strokes = asObjects(style.strokes).filter((s) => s.visible !== false);
  return (
    <>
      <defs>
        <g id={shape}>
          <BooleanSilhouette node={node} uid={uid + '-expression'} />
        </g>
        <UseMask uid={mask} source={shape} area={bounds} />
      </defs>
      <g mask={reference(mask)}>
        <DesignPathLayers
          d={boxPath(bounds)}
          uid={uid + '-surface'}
          bounds={bounds}
          fills={fillsOf(style)}
          strokes={[]}
          assetUrl={assetUrl}
        />
      </g>
      {strokes.map((s, i) => {
        const w = asNumber(s.width) ?? 1,
          id = `${uid}-edge${i}`,
          outer = s.align === 'inside' ? 0 : s.align === 'outside' ? w : w / 2,
          inner = s.align === 'outside' ? 0 : s.align === 'inside' ? w : w / 2,
          area = region(bounds, w + 1);
        const p = asObject(s.paint),
          f = id + '-filter',
          m = id + '-mask';
        if (!w || p.visible === false) return null;
        return (
          <g
            key={id}
            opacity={(asNumber(s.opacity) ?? 1) * (asNumber(p.opacity) ?? 1)}
            style={{ mixBlendMode: blendMode(s.blend) }}
          >
            <defs>
              <DesignPaintDefinition uid={id} paint={p} assetUrl={assetUrl} />
              <filter id={f} filterUnits="userSpaceOnUse" {...area}>
                <feMorphology in="SourceAlpha" operator="dilate" radius={outer} result="outer" />
                <feMorphology in="SourceAlpha" operator="erode" radius={inner} result="inner" />
                <feComposite in="outer" in2="inner" operator="out" />
              </filter>
              <mask id={m} maskUnits="userSpaceOnUse" {...area} style={{ maskType: 'alpha' }}>
                <use href={'#' + shape} color="white" filter={reference(f)} />
              </mask>
            </defs>
            <rect {...area} fill={paintValue(p, id)} mask={reference(m)} />
          </g>
        );
      })}
    </>
  );
}
export function DesignGeometry({ node, uid, assetUrl }: GeometryProps) {
  if (node.kind === 'boolean') return <BooleanLayers node={node} uid={uid} assetUrl={assetUrl} />;
  const { edges, regions, bounds } = geometryPaths(node),
    style = asObject(node.style),
    fills = fillsOf(style),
    strokes = asObjects(style.strokes);
  const rule = node.fillRule === 'evenodd' ? 'evenodd' : 'nonzero';
  if (!node.network)
    return (
      <DesignPathLayers
        d={edges}
        uid={uid}
        rule={rule}
        fills={fills}
        strokes={strokes}
        assetUrl={assetUrl}
        bounds={bounds}
      />
    );
  return (
    <>
      {regions.map((r, i) => (
        <DesignPathLayers
          key={i}
          uid={`${uid}-face${i}`}
          d={r.d}
          rule={r.rule}
          fills={r.fills ?? fills}
          strokes={[]}
          assetUrl={assetUrl}
          bounds={bounds}
        />
      ))}
      <DesignPathLayers
        d={edges}
        uid={uid + '-edges'}
        clipD={regions.map((r) => r.d).join(' ')}
        fills={[]}
        strokes={strokes}
        assetUrl={assetUrl}
        bounds={bounds}
      />
    </>
  );
}
