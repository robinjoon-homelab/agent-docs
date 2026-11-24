import { useMemo, useRef, useState } from 'react';
import type { FocusEvent, MouseEvent, ReactNode } from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import type { FlowFrame } from '../../../../../../domain/document/block';
import { asObject } from '../../../../../../domain/flow/designValue';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import type { DesignScene } from '../../../../../../domain/flow/designScene';
import { fitScale, FRAMES } from '../../../../../../domain/flow/flow';
import { deviceFrameBox, FlowDeviceFrame } from '../FlowDeviceFrame';
import { FlowIcon } from '../FlowIcon';
import { useStageArea } from '../useStageArea';
import { DesignStageMarkers, type DesignStageMarker } from './DesignStageMarkers';
import { DesignStageContext } from './DesignStageContext';
import './designViewStage.css';

export type { DesignStageMarker } from './DesignStageMarkers';
type Props = {
  blocks: readonly FlowDesignBlock[];
  screen: FlowDesignBlock;
  state?: FlowDesignBlock | null;
  scene: DesignScene;
  editing: boolean;
  markers: readonly DesignStageMarker[];
  highlighted?: DesignAddress | null;
  selection?: DesignAddress | null;
  onHighlight?: (target: DesignAddress | null) => void;
  onInfo?: (target: DesignAddress) => void;
  onComment?: (target: DesignAddress | null) => void;
  back?: () => void;
  forward?: () => void;
  trail?: string;
  palette?: ReactNode;
  tools?: ReactNode;
  framePicker?: ReactNode;
  diagnosticsTarget?: HTMLElement | null;
  children: ReactNode;
};

export function DesignViewStage({
  blocks,
  screen,
  state,
  scene,
  editing,
  markers,
  highlighted,
  selection,
  onHighlight,
  onInfo,
  onComment,
  back,
  forward,
  trail,
  palette,
  tools,
  framePicker,
  diagnosticsTarget,
  children,
}: Props) {
  const [zoom, setZoom] = useState<number | 'fit'>('fit');
  const [all, setAll] = useState(false);
  const [localHover, setLocalHover] = useState<DesignAddress | null>(null);
  const context = useMemo(
    () => ({ diagnostics: diagnosticsTarget ?? null, editing }),
    [diagnosticsTarget, editing],
  );
  const frameRef = useRef<HTMLDivElement>(null);
  const areaRef = useRef<HTMLDivElement>(null);
  const area = useStageArea(areaRef, editing && palette ? 170 : 0, 30);
  const defaultFrame = blocks.find((b) => b.design.kind === 'settings')?.design.frame;
  const value = screen.design.frame ?? defaultFrame ?? 'desktop';
  const note = value === 'note';
  const preset: FlowFrame | 'custom' =
    typeof value === 'string' && Object.hasOwn(FRAMES, value) ? (value as FlowFrame) : 'custom';
  const empty =
    scene.root?.node.kind === 'box' &&
    !scene.root.children.length &&
    !scene.root.mounts.length &&
    !Object.keys(asObject(scene.root.node.style)).length;
  const { width, height } = scene.frame;
  const box = deviceFrameBox(preset, width, height);
  const scale =
    zoom === 'fit'
      ? fitScale(box.width, box.height, area ?? { width: editing ? 560 : 740, height: 690 })
      : zoom;
  const label = preset === 'custom' ? '직접 입력' : FRAMES[preset][0];
  const scope = JSON.stringify([screen.id, state?.id, editing]);
  const markMap = useMemo(
    () => new Map(markers.map((m) => [designAddressKey(m.target), m.target])),
    [markers],
  );
  function target(el: EventTarget | null) {
    if (!(el instanceof Element)) return null;
    const node = el.closest<HTMLElement>('[data-stage-marker],[data-design-annotation-key]');
    const key = node?.dataset.stageMarker ?? node?.dataset.designAnnotationKey;
    return key ? (markMap.get(key) ?? null) : null;
  }
  const hover = (addr: DesignAddress | null) => {
    setLocalHover(addr);
    onHighlight?.(addr);
  };
  const enter = (e: MouseEvent | FocusEvent) => {
    const next = target(e.target),
      previous = target(e.relatedTarget);
    if (next !== previous) hover(next);
  };
  const hovered = highlighted === undefined ? localHover : highlighted;
  const code = typeof screen.design.code === 'string' ? screen.design.code : '';
  const name = typeof screen.design.name === 'string' ? screen.design.name : '';
  return (
    <section className="flow-preview design-preview" aria-label="화면 미리보기">
      <div className={'flow-toolbar' + (editing ? '' : ' is-view')}>
        <button type="button" disabled={!back} onClick={back}>
          <FlowIcon name="left" size={14} />
          뒤로
        </button>
        {forward && (
          <button type="button" onClick={forward} aria-label="앞으로" title="앞으로">
            <FlowIcon name="right" size={14} />
          </button>
        )}
        <span className="flow-trail">{trail ?? code}</span>
        {editing && <span className="flow-editing-chip">편집 중</span>}
        {editing && tools}
        <span className="flow-stage-hint">
          {editing ? 'Shift+클릭·빈 곳 끌기로 여러 개 선택' : '요소를 누르면 이동 · (i)로 설명'}
        </span>
        {editing && framePicker}
        {!editing && !note && (
          <button
            type="button"
            className="flow-markers-toggle"
            aria-pressed={all}
            onClick={() => setAll(!all)}
          >
            번호 모두 표시
          </button>
        )}
        {!note && (
          <div className="flow-segmented" role="group" aria-label="배율">
            {(['fit', 0.5, 0.75, 1] as const).map((level) => (
              <button key={level} type="button" aria-pressed={zoom === level} onClick={() => setZoom(level)}>
                {level === 'fit' ? '맞춤' : `${level * 100}%`}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="flow-stage-content" ref={areaRef}>
        {editing && !note && palette}
        {note ? (
          children
        ) : (
          <div className="flow-stage">
            <FlowDeviceFrame
              frame={preset}
              width={width}
              height={height}
              title={'agent-docs · ' + code + ' ' + name}
              scale={scale}
            >
              <div
                className={'design-stage-frame' + (editing ? ' is-editing' : '')}
                style={{ width, height }}
                ref={frameRef}
                onMouseOver={enter}
                onMouseLeave={() => hover(null)}
                onFocus={enter}
                onBlur={(e) => {
                  if (!(e.relatedTarget instanceof Node) || !e.currentTarget.contains(e.relatedTarget))
                    hover(null);
                }}
                onKeyDown={(e) => {
                  if (!editing && e.key === 'i' && !e.ctrlKey && !e.metaKey && !e.altKey) {
                    const a = target(e.target);
                    if (a && onInfo) {
                      e.preventDefault();
                      onInfo(a);
                    }
                  }
                }}
              >
                <DesignStageContext.Provider value={context}>{children}</DesignStageContext.Provider>
                {empty && (
                  <div className="flow-empty">
                    {editing
                      ? '요소가 없습니다. 왼쪽 "요소 추가"에서 넣고 끌어서 놓으세요.'
                      : '아직 요소가 없습니다.'}
                  </div>
                )}
                <DesignStageMarkers
                  container={frameRef}
                  scope={scope}
                  scale={scale}
                  width={width}
                  height={height}
                  markers={markers}
                  all={all}
                  editing={editing}
                  selected={selection ? designAddressKey(selection) : undefined}
                  highlighted={hovered ? designAddressKey(hovered) : undefined}
                  onInfo={onInfo}
                  onComment={onComment}
                />
              </div>
            </FlowDeviceFrame>
            <p className="flow-frame-caption">
              {label} · {width}×{height} · {Math.round(scale * 100)}%
              {!screen.design.frame && !state?.design.frame ? ' · 문서 기본값' : ''}
            </p>
          </div>
        )}
      </div>
    </section>
  );
}
