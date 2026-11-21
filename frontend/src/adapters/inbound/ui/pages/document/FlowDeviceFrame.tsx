import type { ReactNode } from 'react';
import type { FlowFrame } from '../../../../../domain/document/block';
export function FlowDeviceFrame({
  frame,
  width,
  height,
  title,
  children,
  full = false,
}: {
  frame: FlowFrame | 'custom';
  width: number;
  height: number;
  title: string;
  children: ReactNode;
  full?: boolean;
}) {
  const chrome = frame.startsWith('phone')
    ? 'phone'
    : frame.startsWith('tablet')
      ? 'tablet'
      : frame === 'laptop' || frame === 'desktop'
        ? 'window'
        : frame === 'modal'
          ? 'modal'
          : 'plain';
  const pad = chrome === 'phone' ? 8 : chrome === 'tablet' ? 12 : chrome === 'modal' ? 0 : 1;
  const w = width + pad * 2,
    h = height + pad * 2 + (chrome === 'window' ? 30 : 0);
  const scale = full ? 1 : Math.floor(Math.min(1, 740 / w, 690 / h) * 100) / 100;
  return (
    <div className="flow-frame-box" style={{ width: Math.round(w * scale), height: Math.round(h * scale) }}>
      <div
        className={`flow-device chrome-${chrome}`}
        style={{ width: w, height: h, transform: `scale(${scale})` }}
      >
        {chrome === 'window' && (
          <div className="flow-window-bar">
            <i />
            <i />
            <i />
            <span>{title}</span>
          </div>
        )}
        <div className="flow-screen-viewport" style={{ width, height }}>
          {children}
        </div>
      </div>
    </div>
  );
}
