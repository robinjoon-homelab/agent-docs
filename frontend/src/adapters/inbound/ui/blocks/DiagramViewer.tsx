import type { KeyboardEvent, PointerEvent } from 'react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../common/Icon';
import type { DiagramView, Point, Size } from './diagramZoom';
import { diagramSize, fitView, zoomAt } from './diagramZoom';

const STEP = 1.25;
const PADDING = 24;
/** The viewer's 16px inset on each side, its border, and the toolbar row. */
const CHROME = { width: 34, height: 34 + 45 };
const FALLBACK_SIZE: Size = { width: 800, height: 600 };

function initialFrame(): Size {
  return { width: window.innerWidth - CHROME.width, height: window.innerHeight - CHROME.height };
}

/**
 * Near full-screen view of a rendered diagram. Wheel or pinch zooms around the pointer, dragging pans, and the toolbar
 * or the + - 0 keys zoom and fit. It renders into the body, so the document editor never receives its keys or clicks.
 */
export function DiagramViewer({ svg, onClose }: { svg: string; onClose: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const frameRef = useRef<HTMLDivElement>(null);
  const lastPointer = useRef<Point | null>(null);
  const [size] = useState(() => diagramSize(svg) ?? FALLBACK_SIZE);
  const [view, setView] = useState<DiagramView>(() => fitView(size, initialFrame(), PADDING));
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog !== null && !dialog.open) dialog.showModal();
  }, []);

  // React registers wheel listeners as passive, and only an active one may stop the page from scrolling.
  useEffect(() => {
    const frame = frameRef.current;
    if (frame === null) return;
    const zoom = (event: WheelEvent): void => {
      event.preventDefault();
      const bounds = frame.getBoundingClientRect();
      const point = { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
      setView((current) => zoomAt(current, Math.exp(-event.deltaY * 0.002), point));
    };
    frame.addEventListener('wheel', zoom, { passive: false });
    return () => frame.removeEventListener('wheel', zoom);
  }, []);

  const frameSize = (): Size => ({
    width: frameRef.current?.clientWidth ?? initialFrame().width,
    height: frameRef.current?.clientHeight ?? initialFrame().height,
  });
  const zoomCentered = (factor: number): void => {
    const frame = frameSize();
    setView((current) => zoomAt(current, factor, { x: frame.width / 2, y: frame.height / 2 }));
  };
  const fit = (): void => setView(fitView(size, frameSize(), PADDING));
  const actualSize = (): void => zoomCentered(1 / view.scale);

  const onKeyDown = (event: KeyboardEvent): void => {
    if (event.key === '+' || event.key === '=') zoomCentered(STEP);
    else if (event.key === '-') zoomCentered(1 / STEP);
    else if (event.key === '0') fit();
    else return;
    event.preventDefault();
  };

  const onPointerDown = (event: PointerEvent<HTMLDivElement>): void => {
    if (event.button !== 0) return;
    event.currentTarget.setPointerCapture(event.pointerId);
    lastPointer.current = { x: event.clientX, y: event.clientY };
    setDragging(true);
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>): void => {
    const last = lastPointer.current;
    if (last === null) return;
    const dx = event.clientX - last.x;
    const dy = event.clientY - last.y;
    lastPointer.current = { x: event.clientX, y: event.clientY };
    setView((current) => ({ ...current, x: current.x + dx, y: current.y + dy }));
  };
  const endDrag = (): void => {
    lastPointer.current = null;
    setDragging(false);
  };

  return createPortal(
    <dialog
      ref={dialogRef}
      className="diagram-viewer"
      aria-label="다이어그램 크게 보기"
      onClose={onClose}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      onKeyDown={onKeyDown}
    >
      <div className="diagram-viewer-bar">
        <span className="diagram-viewer-hint faint small">휠로 확대·축소, 끌어서 이동</span>
        <button
          type="button"
          className="icon-btn"
          onClick={() => zoomCentered(1 / STEP)}
          aria-label="축소"
          title="축소 (-)"
        >
          <Icon name="minus" />
        </button>
        <button
          type="button"
          className="btn btn-ghost btn-small zoom-level"
          onClick={actualSize}
          title="원래 크기로 보기"
        >
          {Math.round(view.scale * 100)}%
        </button>
        <button
          type="button"
          className="icon-btn"
          onClick={() => zoomCentered(STEP)}
          aria-label="확대"
          title="확대 (+)"
        >
          <Icon name="plus" />
        </button>
        <button type="button" className="btn btn-ghost btn-small" onClick={fit} title="화면에 맞춤 (0)">
          화면에 맞춤
        </button>
        <button type="button" className="icon-btn" onClick={onClose} aria-label="닫기" title="닫기 (Esc)">
          <Icon name="close" />
        </button>
      </div>
      <div
        ref={frameRef}
        className={`diagram-viewer-frame${dragging ? ' is-dragging' : ''}`}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
      >
        {/* mermaid runs with securityLevel "strict", which sanitizes labels before producing the SVG. */}
        <div
          className="diagram-viewer-content"
          style={{
            width: size.width,
            height: size.height,
            transform: `translate(${view.x}px, ${view.y}px) scale(${view.scale})`,
          }}
          dangerouslySetInnerHTML={{ __html: svg }}
        />
      </div>
    </dialog>,
    document.body,
  );
}
