import { createContext, useCallback, useContext, useSyncExternalStore, useId, useMemo, useRef } from 'react';
import type { CSSProperties, KeyboardEvent, MouseEvent, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { DesignStageContext } from './DesignStageContext';
import { useServices } from '../../../../../../config/ServicesContext';
import type {
  DesignJson,
  DesignObject,
  FlowDesignBlock,
} from '../../../../../../domain/document/designBlock';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';
import { designAnnotationIndex } from '../../../../../../domain/flow/designAnnotations';
import type { DesignAddress, DesignOccurrence, DesignScene } from '../../../../../../domain/flow/designScene';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { asNumber, asObject, asText } from '../../../../../../domain/flow/designValue';
import { FlowIcon } from '../FlowIcon';
import { FlowRichText } from '../FlowRichText';
import { DesignGeometry, geometryViewport } from './DesignGeometry';
import { DesignCanvasMask, geometryNode, simpleOutline } from './DesignCanvasMask';
import { DesignBoxSurface } from './DesignBoxSurface';
import { designColor, designNodeStyle } from './designStyle';
import { validAssetId } from './designStyleValues';
import { ScrollDiagnostics, useDesignFonts, useInitialScroll } from './DesignCanvasResources';
import { DesignCanvasDiagnostics } from './DesignCanvasDiagnostics';
import { DesignCanvasText } from './DesignCanvasText';
import './designCanvas.css';

export type DesignCanvasProps = {
  scene?: DesignScene;
  blocks: readonly FlowDesignBlock[];
  screen: string;
  state?: string | null;
  selection?: DesignAddress | null;
  showDiagnostics?: boolean;
  mode?: 'viewport' | 'full';
  scale?: number;
  onSelect?: (address: DesignAddress) => void;
  onActivate?: (address: DesignAddress) => void;
};
type CanvasContext = {
  layoutWarnings: ScrollDiagnostics;
  selected?: string;
  annotations: ReadonlyMap<string, string>;
  assetUrl: (id: string) => string;
  onSelect?: DesignCanvasProps['onSelect'];
  onActivate?: DesignCanvasProps['onActivate'];
};
const Canvas = createContext<CanvasContext | null>(null);
const geometric = (kind: DesignJson | undefined) =>
  kind === 'shape' || kind === 'vector' || kind === 'boolean';
const icons = [
  'flow',
  'comment',
  'left',
  'right',
  'panel',
  'plus',
  'eye',
  'edit',
  'menu',
  'search',
  'image',
  'info',
] as const;
function Icon({ node, assetUrl }: { node: DesignObject; assetUrl: CanvasContext['assetUrl'] }) {
  const shape = asObject(node.icon);
  if (validAssetId(shape.svgAsset))
    return <img className="design-canvas-media" src={assetUrl(shape.svgAsset)} alt="" draggable={false} />;
  if (shape.builtin === 'chevron-down')
    return (
      <svg
        className="design-canvas-graphic"
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M4 6l4 4 4-4" />
      </svg>
    );
  const builtin = icons.find((i) => i === shape.builtin);
  return builtin ? (
    <FlowIcon name={builtin} />
  ) : (
    <span className="design-canvas-fallback" aria-label={asText(shape.builtin)}>
      ◇
    </span>
  );
}
function Media({ node, assetUrl }: { node: DesignObject; assetUrl: CanvasContext['assetUrl'] }) {
  if (!validAssetId(node.asset)) return <span>{asText(node.alt)}</span>;
  const fit = node.fit === 'cover' || node.fit === 'fill' ? node.fit : 'contain';
  return (
    <img
      className="design-canvas-media"
      src={assetUrl(node.asset)}
      style={{ objectFit: fit }}
      alt={asText(node.alt) ?? ''}
      draggable={false}
    />
  );
}
function GeometricLayer({
  node,
  uid,
  assetUrl,
}: {
  node: DesignObject;
  uid: string;
  assetUrl: CanvasContext['assetUrl'];
}) {
  const v = asObject(node.viewBox),
    has = asNumber(v.width) !== undefined;
  const box = has ? [v.x, v.y, v.width, v.height].map((v) => asNumber(v) ?? 0).join(' ') : undefined;
  return (
    <svg
      width="100%"
      height="100%"
      className="design-canvas-graphic"
      viewBox={box}
      preserveAspectRatio={
        v.fit === 'stretch' ? 'none' : v.fit === 'cover' ? 'xMidYMid slice' : 'xMidYMid meet'
      }
      aria-hidden="true"
    >
      <DesignGeometry node={node} uid={uid} assetUrl={assetUrl} />
    </svg>
  );
}
type LayerProps = {
  layer: DesignOccurrence;
  parentMode?: string;
  annotation?: DesignAddress;
  decorative?: boolean;
};
function Layer({ layer, parentMode, annotation, decorative = false }: LayerProps) {
  const ctx = useContext(Canvas),
    ref = useRef<HTMLDivElement>(null),
    uid = 'design' + useId().replace(/[^a-zA-Z0-9_-]/g, '');
  const n = layer.node;
  useInitialScroll(ref, asObject(n.scroll), designAddressKey(layer.address), ctx?.layoutWarnings);
  if (!ctx || n.visible === false) return null;
  const target = layer.mounts.at(-1) ?? layer.address;
  const matches = [...layer.mounts].reverse().concat(layer.address);
  const ownAnchor = matches.find((a) => ctx.annotations.has(designAddressKey(a)));
  const anchorKey = ownAnchor && designAddressKey(ownAnchor);
  const anchor = ownAnchor ?? annotation;
  const enabled = !decorative && Boolean(ctx.onSelect || (anchor && ctx.onActivate));
  const selected = matches.some((a) => designAddressKey(a) === ctx.selected);
  function choose(event: MouseEvent<HTMLDivElement> | KeyboardEvent<HTMLDivElement>) {
    if (!ctx || !enabled) return;
    event.stopPropagation();
    ctx.onSelect?.(target);
    if (anchor) ctx.onActivate?.(anchor);
  }
  const node = geometric(n.kind) ? geometryNode(layer) : n;
  const style = designNodeStyle(n, parentMode, ctx.assetUrl);
  if (n.kind === 'icon' && n.ink !== undefined) style.color = designColor(n.ink);
  if (geometric(n.kind)) {
    const g = geometryViewport(node);
    if (g) {
      const explicit = Object.keys(asObject(n.viewBox)).length > 0;
      style.width ??= explicit ? g.width : Math.max(1, g.x + g.width);
      style.height ??= explicit ? g.height : Math.max(1, g.y + g.height);
    }
    if (parentMode === 'free' && !n.position) {
      style.position = 'absolute';
      style.left = 0;
      style.top = 0;
    }
  }
  if (n.kind === 'mask' && layer.maskSource) {
    if (simpleOutline(n.mode, layer.maskSource)) style.clipPath = 'url(#' + uid + '-mask)';
    else style.maskImage = 'url(#' + uid + '-mask)';
  }
  const children = layer.children.map((c) => (
    <Layer
      key={designAddressKey(c.address)}
      layer={c}
      parentMode={asText(asObject(n.layout).mode) ?? 'free'}
      annotation={anchor}
      decorative={decorative}
    />
  ));
  let content: ReactNode;
  if (geometric(n.kind)) content = <GeometricLayer node={node} uid={uid} assetUrl={ctx.assetUrl} />;
  else if (n.kind === 'text') content = <DesignCanvasText text={n.text} assetUrl={ctx.assetUrl} />;
  else if (n.kind === 'image') content = <Media node={n} assetUrl={ctx.assetUrl} />;
  else if (n.kind === 'icon') content = <Icon node={n} assetUrl={ctx.assetUrl} />;
  else if (n.kind === 'unresolved')
    content = <span className="design-canvas-fallback">연결할 컴포넌트가 없습니다.</span>;
  else
    content = (
      <>
        <DesignBoxSurface node={n} uid={uid} assetUrl={ctx.assetUrl} />
        {children}
      </>
    );
  return (
    <>
      {n.kind === 'mask' && layer.maskSource && (
        <DesignCanvasMask source={layer.maskSource} node={n} uid={uid} assetUrl={ctx.assetUrl} />
      )}
      <div
        ref={ref}
        className={'design-canvas-layer' + (selected ? ' is-selected' : '')}
        data-design-node={layer.address.node}
        data-design-owner={layer.address.owner}
        data-design-address={designAddressKey(target)}
        data-design-annotation-key={anchorKey}
        data-design-source-address={designAddressKey(layer.address)}
        data-design-mount-keys={layer.mounts.map(designAddressKey).join('\n')}
        data-design-scroll-x={asText(asObject(n.scroll).x) ?? asNumber(asObject(n.scroll).x)}
        data-design-scroll-y={asText(asObject(n.scroll).y) ?? asNumber(asObject(n.scroll).y)}
        data-design-kind={asText(n.kind)}
        style={style}
        role={asText(n.role) ?? (enabled && ownAnchor ? 'button' : undefined)}
        aria-label={asText(n.accessibleName) ?? (anchorKey ? ctx.annotations.get(anchorKey) : undefined)}
        tabIndex={enabled && (selected || ownAnchor) ? 0 : undefined}
        onClick={enabled ? choose : undefined}
        onKeyDown={
          enabled
            ? (e) => {
                if (e.target === e.currentTarget && (e.key === 'Enter' || e.key === ' ')) {
                  e.preventDefault();
                  choose(e);
                }
              }
            : undefined
        }
      >
        {content}
      </div>
    </>
  );
}
export function DesignCanvas(props: DesignCanvasProps) {
  const { assets } = useServices();
  const assetUrl = useCallback((id: string) => assets.contentUrl(id), [assets]);
  return <DesignCanvasView {...props} assetUrl={assetUrl} />;
}
export function DesignCanvasView({
  scene: providedScene,
  blocks,
  screen,
  state,
  selection,
  assetUrl,
  showDiagnostics = false,
  scale = 1,
  mode = 'viewport',
  onSelect,
  onActivate,
}: DesignCanvasProps & { assetUrl: (id: string) => string }) {
  const canvas = useRef<HTMLDivElement>(null);
  const stage = useContext(DesignStageContext);
  const scene = useMemo(
    () => providedScene ?? resolveDesignScene(blocks, screen, state),
    [providedScene, blocks, screen, state],
  );
  const block = blocks.find((b) => b.design.kind === 'screen' && b.design.id === screen)?.design;
  const annotations = useMemo(
    () =>
      new Map(
        Array.from(designAnnotationIndex(blocks, screen, state)).flatMap(([key, b]) =>
          b.design.enabled === false
            ? []
            : [[key, asText(b.design.name) ?? asText(asObject(b.design.target).node) ?? '요소'] as const],
        ),
      ),
    [blocks, screen, state],
  );
  const layoutWarnings = useMemo(() => new ScrollDiagnostics(), []);
  const warnings = [
    ...scene.warnings,
    ...useSyncExternalStore(layoutWarnings.subscribe, layoutWarnings.read, layoutWarnings.read),
  ];
  const ctx = {
    layoutWarnings,
    annotations,
    selected: selection ? designAddressKey(selection) : undefined,
    onSelect,
    onActivate,
    assetUrl,
  };
  useDesignFonts(canvas, scene.root, assetUrl, layoutWarnings);
  const style: CSSProperties = {
    width: scene.frame.width,
    ...(mode === 'viewport' ? { height: scene.frame.height } : { minHeight: scene.frame.height }),
    zoom: Number.isFinite(scale) && scale > 0 ? scale : 1,
  };
  const diagnostics = (
    <>
      {showDiagnostics && (
        <DesignCanvasDiagnostics
          key={JSON.stringify([screen, state, selection])}
          canvas={canvas}
          selection={selection}
        />
      )}
      {warnings.length > 0 && (
        <details className="design-canvas-warnings">
          <summary>디자인 경고 {warnings.length}건</summary>
          {warnings.map((w) => (
            <p key={w}>{w}</p>
          ))}
        </details>
      )}
    </>
  );
  return (
    <Canvas.Provider value={ctx}>
      <div
        key={JSON.stringify([screen, state])}
        ref={canvas}
        data-design-empty-slots={scene.emptySlots.join('\n')}
        data-design-warnings={scene.warnings.join('\n')}
        data-design-frame-width={scene.frame.width}
        data-design-frame-height={scene.frame.height}
        data-design-mode={mode}
        className={'design-canvas design-canvas-' + mode}
        data-design-screen={screen}
        data-design-state={state ?? undefined}
        style={style}
      >
        {block?.frame === 'note' ? (
          <FlowRichText assetUrl={assetUrl} text={asText(block.description) ?? ''} />
        ) : (
          scene.root && <Layer key={JSON.stringify([screen, state])} layer={scene.root} />
        )}
        {stage?.editing && mode === 'viewport' && (
          <div className="design-edit-clearance" aria-hidden="true" />
        )}
      </div>
      {stage ? stage.diagnostics && createPortal(diagnostics, stage.diagnostics) : diagnostics}
    </Canvas.Provider>
  );
}
