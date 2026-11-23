import { useLayoutEffect, useState, useRef } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { asNumber, asObject, asObjects } from '../../../../../../domain/flow/designValue';
import { DesignPathLayers } from './DesignGeometryPaint';
import { usesVectorSurface } from './designStyleSurface';
import type { AssetUrl, StyleValue } from './designStyleValues';

function radius(value: StyleValue, size: number): number {
  const pixels = asNumber(value) ?? (size * (asNumber(asObject(value).percent) ?? 0)) / 100;
  return Math.max(0, pixels);
}

function roundedBox(width: number, height: number, corners: DesignObject) {
  const values = [corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft];
  const rx = values.map((v) => radius(v, width)),
    ry = values.map((v) => radius(v, height));
  const fit = (available: number, a: number, b: number) => (a + b === 0 ? 1 : available / (a + b));
  const [tlx = 0, trx = 0, brx = 0, blx = 0] = rx,
    [tly = 0, try_ = 0, bry = 0, bly = 0] = ry;
  const k = Math.min(
    1,
    fit(width, tlx, trx),
    fit(width, blx, brx),
    fit(height, tly, bly),
    fit(height, try_, bry),
  );
  const arc = (x: number, y: number, toX: number, toY: number) =>
    x && y ? `A${k * x} ${k * y} 0 0 1 ${toX} ${toY}` : `L${toX} ${toY}`;
  return [
    `M${k * tlx} 0 H${width - k * trx}`,
    arc(trx, try_, width, k * try_),
    `V${height - k * bry}`,
    arc(brx, bry, width - k * brx, height),
    `H${k * blx}`,
    arc(blx, bly, 0, height - k * bly),
    `V${k * tly}`,
    arc(tlx, tly, k * tlx, 0),
    'Z',
  ].join(' ');
}

type SurfaceProps = { node: DesignObject; uid: string; assetUrl?: AssetUrl };
function PaintedSurface({ node, uid, assetUrl }: SurfaceProps) {
  const svg = useRef<SVGSVGElement>(null);
  const [box, setBox] = useState<{ width: number; height: number } | null>(null);
  useLayoutEffect(() => {
    const target = svg.current;
    if (!target || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver((entries) => {
      const area = entries[0]?.contentRect;
      if (area)
        setBox((prev) =>
          prev?.width === area.width && prev.height === area.height
            ? prev
            : { width: area.width, height: area.height },
        );
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, []);
  const size = asObject(node.size),
    width = box?.width ?? asNumber(asObject(size.x).px) ?? 0,
    height = box?.height ?? asNumber(asObject(size.y).px) ?? 0;
  const style = asObject(node.style),
    fills = asObjects(style.fills),
    strokes = asObjects(style.strokes);
  return (
    <svg
      ref={svg}
      aria-hidden="true"
      focusable="false"
      style={{
        position: 'absolute',
        inset: 0,
        display: 'block',
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
        overflow: 'visible',
      }}
    >
      {width > 0 && height > 0 && (
        <DesignPathLayers
          uid={uid}
          d={roundedBox(width, height, asObject(style.radius))}
          fills={fills}
          strokes={strokes}
          {...(assetUrl ? { assetUrl } : {})}
          bounds={{ x: 0, y: 0, width, height }}
        />
      )}
    </svg>
  );
}

/** Unusual paints and dashed/multiple strokes share SVG paint geometry with authored paths. */
export function DesignBoxSurface(props: SurfaceProps) {
  return usesVectorSurface(props.node) ? <PaintedSurface {...props} /> : null;
}
