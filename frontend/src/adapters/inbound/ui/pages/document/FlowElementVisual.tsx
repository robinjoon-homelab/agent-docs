import type { FlowElementBlock } from '../../../../../domain/document/block';
import { FlowIcon } from './FlowIcon';
import { imageAssetId } from '../../../../../domain/flow/flow';
import { useServices } from '../../../../../config/ServicesContext';

function FlowImage({ assetId, alt }: { assetId: string; alt: string }) {
  const { assets } = useServices();
  return (
    <img
      className="flow-drawn-asset"
      src={assets.contentUrl(assetId)}
      alt={alt}
      loading="lazy"
      draggable={false}
    />
  );
}
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
            <span key={i}>{item}</span>
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
    case 'image': {
      const assetId = imageAssetId(e);
      if (assetId) return <FlowImage assetId={assetId} alt={e.label} />;
      return (
        <div className="flow-drawn-image">
          <FlowIcon name="image" size={22} />
          {e.value}
        </div>
      );
    }
  }
}
