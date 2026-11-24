import { useEffect, useRef, type ReactNode } from 'react';
import { FlowCommentBadge, FlowIcon } from './FlowIcon';

export type ScreenTab = { id: string; code: string; name: string; comments: number };

export function FlowScreenStrip({
  screens,
  selected,
  onSelect,
  onAdd,
  children,
  after,
}: {
  screens: readonly ScreenTab[];
  selected?: string;
  onSelect: (id: string) => void;
  onAdd?: () => void;
  children?: ReactNode;
  after?: ReactNode;
}) {
  const strip = useRef<HTMLDivElement>(null);
  const index = screens.findIndex((s) => s.id === selected);
  useEffect(() => {
    const container = strip.current;
    const active = container?.querySelector<HTMLElement>('[aria-pressed="true"]');
    if (!container || !active) return;
    const bounds = container.getBoundingClientRect(),
      item = active.getBoundingClientRect();
    if (item.left < bounds.left) container.scrollLeft += item.left - bounds.left;
    else if (item.right > bounds.right) container.scrollLeft += item.right - bounds.right;
  }, [selected]);
  return (
    <nav className="flow-strip" aria-label="화면 목록">
      <button
        type="button"
        className="flow-strip-arrow"
        aria-label="이전 화면"
        disabled={index < 1}
        onClick={() => {
          const s = screens[index - 1];
          if (s) onSelect(s.id);
        }}
      >
        <FlowIcon name="left" />
      </button>
      <div className="flow-screen-buttons" ref={strip}>
        {screens.map((s) => (
          <button type="button" key={s.id} aria-pressed={selected === s.id} onClick={() => onSelect(s.id)}>
            <span>{s.code}</span>
            {s.name}
            <FlowCommentBadge count={s.comments} />
          </button>
        ))}
        {children}
        {onAdd && (
          <button type="button" className="flow-add-screen" onClick={onAdd}>
            <FlowIcon name="plus" size={14} />
            화면 추가
          </button>
        )}
      </div>
      <button
        type="button"
        className="flow-strip-arrow"
        aria-label="다음 화면"
        disabled={index >= screens.length - 1}
        onClick={() => {
          const s = screens[index + 1];
          if (s) onSelect(s.id);
        }}
      >
        <FlowIcon name="right" />
      </button>
      {after}
    </nav>
  );
}
