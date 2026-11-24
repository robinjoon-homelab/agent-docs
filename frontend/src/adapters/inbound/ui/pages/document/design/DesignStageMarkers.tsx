import { useEffect, useState } from 'react';
import type { RefObject } from 'react';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { FlowCommentBadge, FlowIcon } from '../FlowIcon';
import { visibleDesignBounds } from './DesignCanvasDiagnostics';

export type DesignStageMarker = {
  target: DesignAddress;
  name: string;
  destination?: string;
  comments?: number;
  warning?: string;
};
type MarkerBox = { left: number; top: number; width: number; height: number; outside: boolean };
type StageGeometry = {
  boxes: ReadonlyMap<string, MarkerBox>;
  more: boolean;
  fold: number;
};

function geometry(canvas: HTMLElement): StageGeometry {
  const frame = canvas.getBoundingClientRect();
  const scale = frame.width / canvas.offsetWidth || 1;
  const boxes = new Map<string, MarkerBox>();
  const ancestors = new Map<
    HTMLElement,
    { rect: { x: number; y: number; width: number; height: number }; x: boolean; y: boolean }
  >();
  for (const el of canvas.querySelectorAll<HTMLElement>('[data-design-annotation-key]')) {
    const key = el.dataset.designAnnotationKey;
    if (!key) continue;
    const actual = el.getBoundingClientRect();
    const shown = visibleDesignBounds(el, actual, canvas, ancestors);
    if (shown)
      boxes.set(key, {
        left: (shown.x - frame.x) / scale,
        top: (shown.y - frame.y) / scale,
        width: shown.width / scale,
        height: shown.height / scale,
        outside: actual.right > frame.right + 0.5 || actual.left < frame.left - 0.5,
      });
  }
  const clearance = canvas.querySelector<HTMLElement>(':scope > .design-edit-clearance');
  const content = canvas.querySelector<HTMLElement>(':scope > .design-canvas-layer');
  if (clearance && content) {
    const rect = content.getBoundingClientRect();
    const bottom = canvas.scrollTop + (rect.bottom - frame.top) / scale;
    const extra =
      getComputedStyle(content).overflowY === 'visible'
        ? Math.max(0, content.scrollHeight - content.clientHeight)
        : 0;
    const top = `${Math.ceil(Math.max(canvas.clientHeight, bottom + extra))}px`;
    if (clearance.style.top !== top) clearance.style.top = top;
  }
  return {
    boxes,
    more: canvas.scrollHeight - canvas.clientHeight - Math.ceil(canvas.scrollTop) > 1,
    fold: canvas.clientHeight - canvas.scrollTop,
  };
}

function useStageGeometry(container: RefObject<HTMLDivElement | null>, scope: string, scale: number) {
  const [layout, setLayout] = useState<StageGeometry>({ boxes: new Map(), more: false, fold: 0 });
  useEffect(() => {
    const canvas = container.current?.querySelector<HTMLElement>('.design-canvas');
    if (!canvas) return;
    const measure = () => setLayout(geometry(canvas));
    measure();
    let pending = 0;
    const later = () => {
      if (pending) return;
      pending = requestAnimationFrame(() => {
        pending = 0;
        measure();
      });
    };
    const resize = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(later) : null;
    const observeLayers = () => {
      resize?.disconnect();
      resize?.observe(canvas);
      for (const el of canvas.querySelectorAll<HTMLElement>(
        ':scope > .design-canvas-layer,[data-design-annotation-key]',
      ))
        resize?.observe(el);
    };
    observeLayers();
    const mutation = new MutationObserver((records) => {
      if (records.some((r) => r.type === 'childList' || r.attributeName === 'data-design-annotation-key'))
        observeLayers();
      later();
    });
    mutation.observe(canvas, { subtree: true, childList: true, attributes: true, characterData: true });
    canvas.addEventListener('scroll', later, true);
    window.addEventListener('resize', later);
    const fonts = (document as { fonts?: FontFaceSet }).fonts;
    fonts?.addEventListener('loadingdone', later);
    return () => {
      mutation.disconnect();
      resize?.disconnect();
      cancelAnimationFrame(pending);
      canvas.removeEventListener('scroll', later, true);
      window.removeEventListener('resize', later);
      fonts?.removeEventListener('loadingdone', later);
    };
  }, [container, scope, scale]);
  return layout;
}

export function DesignStageMarkers({
  container,
  scope,
  scale,
  width,
  height,
  markers,
  all,
  editing,
  selected,
  highlighted,
  onInfo,
  onComment,
}: {
  container: RefObject<HTMLDivElement | null>;
  scope: string;
  scale: number;
  width: number;
  height: number;
  markers: readonly DesignStageMarker[];
  all: boolean;
  editing: boolean;
  selected?: string;
  highlighted?: string;
  onInfo?: (address: DesignAddress) => void;
  onComment?: (address: DesignAddress | null) => void;
}) {
  const layout = useStageGeometry(container, scope, scale);
  return (
    <div className={'design-marker-plane' + (editing ? ' is-editing' : '')} aria-label="요소 표시">
      {editing && layout.fold >= 0 && layout.fold <= height && (
        <div className="flow-boundary" style={{ top: layout.fold }}>
          <span>여기까지 첫 화면 ({height}px), 아래는 스크롤</span>
        </div>
      )}
      {!editing && layout.more && (
        <div className="flow-scroll-hint">
          <span>↓ 아래로 스크롤해 더 보기</span>
        </div>
      )}
      {markers.map((m, i) => {
        const key = designAddressKey(m.target),
          box = layout.boxes.get(key);
        if (!box) return null;
        const active = key === selected || key === highlighted;
        const warning = editing ? m.warning || (box.outside ? '틀 밖' : undefined) : undefined;
        if (!all && !active && !editing && !m.comments) return null;
        return (
          <div
            key={key}
            data-stage-marker={key}
            className={'design-node-marker' + (active ? ' is-active' : '')}
            style={{
              left: box.left,
              top: box.top,
              width: box.width,
              height: box.height,
            }}
          >
            {(active || all || editing) && (
              <b
                className="design-annotation-number"
                aria-hidden="true"
                style={{ left: Math.max(-box.left, editing ? 10 : -9), top: Math.max(-box.top, -9) }}
              >
                {i + 1}
              </b>
            )}
            {active && !editing && onInfo && (
              <button
                type="button"
                className="design-info-marker"
                aria-label={m.name + ' 설명'}
                style={{ right: Math.max(box.left + box.width - width, -10), top: Math.max(-box.top, -10) }}
                onClick={(e) => {
                  e.stopPropagation();
                  onInfo(m.target);
                }}
              >
                <FlowIcon name="info" size={12} />
              </button>
            )}
            {(active || editing) && m.destination && (
              <small className="design-go-marker">→ {m.destination}</small>
            )}
            {warning && <small className="flow-warning">{warning}</small>}
            {!!m.comments &&
              (onComment ? (
                <button
                  type="button"
                  className="design-count-marker"
                  aria-label={`${m.name} 댓글 ${m.comments}개`}
                  onClick={(e) => {
                    e.stopPropagation();
                    onComment(m.target);
                  }}
                >
                  <FlowCommentBadge count={m.comments} />
                </button>
              ) : (
                <span className="design-count-marker">
                  <FlowCommentBadge count={m.comments} />
                </span>
              ))}
          </div>
        );
      })}
    </div>
  );
}
