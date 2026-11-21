import type { FlowElementBlock } from '../../../../../domain/document/block';
import type { Flow, FlowScreen } from '../../../../../domain/flow/flow';
import { KINDS, numbered, nextScreens } from '../../../../../domain/flow/flow';
import type { Thread } from '../../../../../domain/thread/thread';
export function FlowOverview({
  flow,
  active,
  threads,
  expanded,
  onExpand,
  onSelect,
  onHover,
  onNavigate,
  hover,
}: {
  flow: Flow;
  active: FlowScreen;
  threads: readonly Thread[];
  expanded: readonly string[];
  onExpand: (id: string) => void;
  onSelect: (element: FlowElementBlock) => void;
  onHover: (id: string | null) => void;
  onNavigate: (id: string) => void;
  hover: string | null;
}) {
  const { screen } = active,
    items = numbered(active.elements);
  return (
    <div className="flow-overview">
      <div className="flow-section-eyebrow">
        <span className="flow-screen-id">{screen.screenId}</span>화면 설명
      </div>
      <h2>{screen.name}</h2>
      <p className="flow-description">{screen.desc}</p>
      <div className="flow-entry">
        <span>진입</span>
        <div>{screen.entry}</div>
        <span>다음 화면</span>
        <div className="flow-nexts">
          {nextScreens(active).length ? (
            nextScreens(active).map((target) => (
              <button key={target} onClick={() => onNavigate(target)}>
                {target} {flow.screens.find((s) => s.screen.screenId === target)?.screen.name}
              </button>
            ))
          ) : (
            <span>없음 (흐름의 끝)</span>
          )}
        </div>
      </div>
      <div className="flow-section-title">
        <h3>주요 요소 {items.length}</h3>
        <span>요소의 (i) 설명을 줄여 보여 줍니다</span>
      </div>
      {!items.length && <p className="muted">설명이 붙은 요소가 없습니다.</p>}
      <ol className="flow-hot-items">
        {items.map((element, index) => {
          const count = threads.filter(
            (t) => t.anchor.kind !== 'document' && t.anchor.blockId === element.id && t.status !== 'resolved',
          ).length;
          return (
            <li
              key={element.id}
              className={hover === element.id ? 'is-hovered' : undefined}
              onMouseEnter={() => onHover(element.id)}
              onMouseLeave={() => onHover(null)}
            >
              <span className="flow-number">{index + 1}</span>
              <div>
                <div className="flow-hot-title">
                  <button onClick={() => onSelect(element)}>{element.label}</button>
                  <span className="flow-kind-chip">{KINDS[element.kind]}</span>
                  {element.actions[0]?.to && <span className="flow-target">→ {element.actions[0].to}</span>}
                  {count > 0 && <span className="flow-comment-badge">댓글 {count}</span>}
                </div>
                <p>
                  {element.desc
                    ? expanded.includes(element.id)
                      ? element.desc
                      : element.desc.slice(0, 60) + (element.desc.length > 60 ? '…' : '')
                    : '(설명 없음)'}
                  {element.desc.length > 60 && (
                    <button onClick={() => onExpand(element.id)}>
                      {expanded.includes(element.id) ? '접기' : '더보기'}
                    </button>
                  )}
                </p>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
