import type { DesignObject } from '../../../../../../domain/document/designBlock';
import type { DesignOccurrence } from '../../../../../../domain/flow/designScene';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';
import { asNumber, asObject, asObjects, asText } from '../../../../../../domain/flow/designValue';
import {
  DesignGeometry,
  DesignSilhouette,
  DesignClipPaths,
  geometryViewport,
  geometryPlacement,
  type GeometryBounds,
} from './DesignGeometry';
import { designColor, designTextStyle } from './designStyle';
import { validAssetId } from './designStyleValues';

export function geometryNode(layer: DesignOccurrence): DesignObject {
  if (!layer.children.length) return layer.node;
  return {
    ...layer.node,
    [layer.node.kind === 'boolean' ? 'operands' : 'children']: layer.children.map(geometryNode),
  };
}
export function simpleOutline(mode: unknown, source: DesignOccurrence) {
  return mode === 'outline' && (source.node.kind === 'shape' || source.node.kind === 'vector');
}
type MaskProps = {
  source: DesignOccurrence;
  node: DesignObject;
  uid: string;
  assetUrl: (id: string) => string;
};
function maskLength(axis: DesignObject, extent: number, fallback: number) {
  return axis.mode === 'fixed'
    ? (asNumber(axis.px) ?? fallback)
    : axis.mode === 'percent'
      ? (extent * (asNumber(axis.percent) ?? 100)) / 100
      : axis.mode === 'fill'
        ? extent
        : fallback;
}
function PaintedMask({
  layer,
  area,
  uid,
  assetUrl,
}: {
  layer: DesignOccurrence;
  area: GeometryBounds;
  uid: string;
  assetUrl: (id: string) => string;
}) {
  const n = layer.node,
    style = asObject(n.style);
  if (n.visible === false) return null;
  const geometry = geometryNode(layer),
    natural = geometryViewport(geometry),
    size = asObject(n.size);
  const width = maskLength(asObject(size.x), area.width, natural?.width ?? area.width),
    height = maskLength(asObject(size.y), area.height, natural?.height ?? area.height);
  const local = { x: 0, y: 0, width, height };
  let paint;
  if (n.kind === 'shape' || n.kind === 'vector' || n.kind === 'boolean')
    paint = <DesignGeometry node={geometry} uid={uid} assetUrl={assetUrl} />;
  else if (n.kind === 'image' && validAssetId(n.asset))
    paint = (
      <image
        href={assetUrl(n.asset)}
        width={width}
        height={height}
        preserveAspectRatio={
          n.fit === 'fill' ? 'none' : n.fit === 'cover' ? 'xMidYMid slice' : 'xMidYMid meet'
        }
      />
    );
  else if (n.kind === 'text') {
    const typography = asObject(style.text);
    paint = (
      <text
        dominantBaseline="text-before-edge"
        style={designTextStyle(typography, assetUrl)}
        fill={designColor(typography.color) ?? 'currentColor'}
      >
        {typeof n.text === 'string'
          ? n.text
          : asObjects(asObject(n.text).runs).map((r) => (
              <tspan
                key={asText(r.id)}
                style={designTextStyle(asObject(r.style), assetUrl)}
                fill={designColor(asObject(r.style).color)}
              >
                {asText(r.text)}
              </tspan>
            ))}
      </text>
    );
  } else {
    const shape: DesignObject = {
      kind: 'shape',
      geometry: { kind: 'rect', origin: { x: 0, y: 0 }, width, height },
      style,
    };
    paint = (
      <>
        {n.kind === 'box' && <DesignGeometry node={shape} uid={uid + '-surface'} assetUrl={assetUrl} />}
        {layer.children.map((c, i) => (
          <PaintedMask
            key={designAddressKey(c.address)}
            layer={c}
            area={local}
            uid={uid + '-' + String(i)}
            assetUrl={assetUrl}
          />
        ))}
      </>
    );
  }
  return (
    <g transform={geometryPlacement(geometry, area)} opacity={asNumber(style.opacity)}>
      {paint}
    </g>
  );
}
export function DesignCanvasMask({ source, node, uid, assetUrl }: MaskProps) {
  const natural = geometryViewport(geometryNode(source)),
    size = asObject(node.size);
  const width = asNumber(asObject(size.x).px) ?? natural?.width ?? 1,
    height = asNumber(asObject(size.y).px) ?? natural?.height ?? 1;
  const area = { x: 0, y: 0, width, height };
  return (
    <svg width="0" height="0" className="design-canvas-defs" aria-hidden="true">
      <defs>
        {simpleOutline(node.mode, source) ? (
          <clipPath id={uid + '-mask'} clipPathUnits="userSpaceOnUse">
            <DesignClipPaths node={geometryNode(source)} area={area} />
          </clipPath>
        ) : (
          <mask
            id={uid + '-mask'}
            maskUnits="userSpaceOnUse"
            x={0}
            y={0}
            width={width}
            height={height}
            style={{ maskType: node.mode === 'luminance' ? 'luminance' : 'alpha' }}
          >
            {node.mode === 'outline' ? (
              <g color="white">
                <DesignSilhouette node={geometryNode(source)} uid={uid + '-source'} placed area={area} />
              </g>
            ) : (
              <PaintedMask layer={source} uid={uid + '-source'} area={area} assetUrl={assetUrl} />
            )}
          </mask>
        )}
      </defs>
    </svg>
  );
}
