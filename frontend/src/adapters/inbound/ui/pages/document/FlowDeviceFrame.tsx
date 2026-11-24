import type { ReactNode } from 'react';
import type { FlowFrame } from '../../../../../domain/document/block';
import { fitScale } from '../../../../../domain/flow/flow';
import type { StageArea } from './useStageArea';

export function deviceFrameBox(frame: FlowFrame | 'custom', width: number, height: number) {
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
  return { chrome, width: width + pad * 2, height: height + pad * 2 + (chrome === 'window' ? 30 : 0) };
}

export function FlowDeviceFrame({
  frame,
  width,
  height,
  title,
  children,
  full = false,
  area = null,
  scale: requestedScale,
}: {
  frame: FlowFrame | 'custom';
  width: number;
  height: number;
  title: string;
  children: ReactNode;
  full?: boolean;
  area?: StageArea | null;
  scale?: number;
}) {
  const { chrome, width: w, height: h } = deviceFrameBox(frame, width, height);
  const scale = requestedScale ?? (full ? 1 : fitScale(w, h, area ?? { width: 740, height: 690 }));
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
