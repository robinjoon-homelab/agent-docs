import type { FlowElementBlock } from '../../../../../domain/document/block';
import { FlowIcon } from './FlowIcon';
// The source artboard selects the first row only in its m2-list example (mapped to this block ID).
const SPACE_PICKER_EXAMPLE = 'fl00000023';
export function FlowElementVisual({ element: e }: { element: FlowElementBlock }) {
  switch (e.kind) {
    case 'title':
      return <div className={`flow-drawn-title ${e.look === 'small' ? 'small' : ''}`}>{e.value}</div>;
    case 'text':
      return <p className="flow-drawn-text">{e.value}</p>;
    case 'field':
    case 'select':
    case 'area':
      return (
        <div
          className={`flow-control ${e.kind === 'area' ? 'area' : ''} ${e.look === 'inactive' ? 'inactive' : ''}`}
        >
          <span>{e.label}</span>
          <span>
            {e.value}
            {e.kind === 'select' && (
              <svg
                width="14"
                height="14"
                viewBox="0 0 16 16"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
              >
                <path d="M4 6l4 4 4-4" />
              </svg>
            )}
          </span>
        </div>
      );
    case 'button':
      return <span className={`flow-drawn-button ${e.look ?? 'primary'}`}>{e.value}</span>;
    case 'option':
      return <span className="flow-drawn-option">{e.value}</span>;
    case 'list':
      return (
        <div className="flow-drawn-list">
          {e.items?.map((item, i) => (
            <span key={i} className={e.id === SPACE_PICKER_EXAMPLE && i === 0 ? 'selected' : undefined}>
              {item}
              {e.id === SPACE_PICKER_EXAMPLE && i === 0 && (
                <svg
                  width="16"
                  height="16"
                  viewBox="0 0 16 16"
                  fill="none"
                  stroke="var(--focus)"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  aria-hidden="true"
                >
                  <path d="M3.5 8.5l3 3 6-7" />
                </svg>
              )}
            </span>
          ))}
        </div>
      );
    case 'path':
      return (
        <div className="flow-drawn-path">
          {e.date ? (
            <>
              <div>오늘: {e.date}</div>
              <div>
                저장 경로: <strong>{e.value}</strong>
              </div>
            </>
          ) : (
            <div>
              {e.label}: <strong>{e.value}</strong>
            </div>
          )}
        </div>
      );
    case 'notice':
      return <p className={`flow-drawn-notice ${e.look ?? 'info'}`}>{e.value}</p>;
    case 'link':
      return <span className="flow-drawn-link">{e.value}</span>;
    case 'bar':
      return (
        <span className="flow-drawn-bar">
          <FlowIcon name="menu" size={18} />
          <FlowIcon name="search" size={18} />
        </span>
      );
    case 'crumb':
      return <span className="flow-drawn-crumb">{e.value}</span>;
    case 'block':
      return <p className="flow-drawn-block">{e.value}</p>;
    case 'box':
      return <div className={`flow-drawn-box ${e.look ?? 'white'}`} />;
    case 'image':
      return (
        <div className="flow-drawn-image">
          <FlowIcon name="image" size={22} />
          {e.value}
        </div>
      );
  }
}
