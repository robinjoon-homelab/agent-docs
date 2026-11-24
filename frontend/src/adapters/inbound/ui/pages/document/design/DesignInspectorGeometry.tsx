import { useState } from 'react';
import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import { object } from '../../../../../../domain/flow/editDesign';

type Patch = (fields: readonly (readonly [string, DesignJson])[]) => string | null;
function NumericField({
  label,
  value,
  onCommit,
  locked,
}: {
  label: string;
  value: number;
  onCommit: (v: number) => string | null;
  locked: boolean;
}) {
  const [draft, setDraft] = useState<{ base: number; text: string } | null>(null);
  function commit() {
    if (!draft) return;
    if (draft.base !== value || !draft.text.trim() || !Number.isFinite(Number(draft.text))) {
      setDraft(null);
      return;
    }
    onCommit(Number(draft.text));
    setDraft(null);
  }
  return (
    <label>
      {label}
      <input
        type="number"
        step="any"
        value={draft?.text ?? value}
        disabled={locked}
        onChange={(e) => setDraft({ base: draft?.base ?? value, text: e.target.value })}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur();
          if (e.key === 'Escape') setDraft(null);
        }}
      />
    </label>
  );
}
export function DesignInspectorGeometry({
  geometry,
  frame,
  write,
  onLayer,
  rectActions,
}: {
  geometry: DesignObject;
  frame?: { width: number; height: number };
  write: Patch;
  onLayer?: (front: boolean) => void;
  rectActions: boolean;
}) {
  const position = object(geometry.position),
    size = object(geometry.size),
    locked = geometry.locked === true;
  const fields: { key: string; label: string; value: number; isSize?: boolean }[] = [];
  if (position?.mode === 'absolute') {
    for (const axis of ['x', 'y'] as const) {
      const anchors = object(position[axis]);
      for (const k of ['start', 'end', 'center'] as const) {
        const v = anchors?.[k];
        if (typeof v === 'number')
          fields.push({
            key: 'position.' + axis + '.' + k,
            label:
              k === 'start'
                ? axis.toUpperCase()
                : (axis === 'x' ? '가로' : '세로') + (k === 'center' ? ' 중심' : ' 끝'),
            value: v,
          });
      }
    }
  }
  for (const axis of ['x', 'y'] as const) {
    const a = object(size?.[axis]);
    if (a?.mode === 'fixed' && typeof a.px === 'number')
      fields.push({
        key: 'size.' + axis + '.px',
        label: axis === 'x' ? '가로' : '세로',
        value: a.px,
        isSize: true,
      });
  }
  const x = object(position?.x),
    w = object(size?.x)?.px;
  return (
    <div className="flow-inspector-box">
      <h3>위치와 크기 (px)</h3>
      {fields.length > 0 && (
        <div className="flow-geometry">
          {fields.map((f) => (
            <NumericField
              key={f.key}
              label={f.label}
              value={f.value}
              locked={locked}
              onCommit={(v) => write([[f.key, f.isSize ? Math.max(rectActions ? 16 : 0, v) : v]])}
            />
          ))}
        </div>
      )}
      <div className="flow-inspector-buttons">
        {rectActions && frame && typeof x?.start === 'number' && typeof w === 'number' && (
          <>
            <button
              type="button"
              disabled={locked}
              onClick={() => write([['position.x.start', Math.round((frame.width - w) / 2)]])}
            >
              가로 가운데
            </button>
            <button
              type="button"
              disabled={locked}
              onClick={() =>
                write([
                  ['position.x.start', 16],
                  ['size.x.px', Math.max(16, frame.width - 32)],
                ])
              }
            >
              좌우 여백 16 맞춤
            </button>
          </>
        )}
        {onLayer && (
          <>
            <button type="button" onClick={() => onLayer(true)}>
              맨 앞으로
            </button>
            <button type="button" onClick={() => onLayer(false)}>
              맨 뒤로
            </button>
          </>
        )}
      </div>
      <label className="flow-check">
        <input type="checkbox" checked={locked} onChange={(e) => write([['locked', e.target.checked]])} />
        위치 잠금
      </label>
      {fields.length > 0 && (
        <small className="flow-placement-hint">화살표 키로 1px, Shift와 함께 누르면 10px씩 옮깁니다.</small>
      )}
    </div>
  );
}
