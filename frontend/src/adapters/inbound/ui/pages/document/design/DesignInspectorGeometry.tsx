import { useId, useState } from 'react';
import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import type { Rect } from '../../../../../../domain/flow/flow';
import type { PlacedLayer } from '../../../../../../domain/flow/editCanvasGeometry';
import { object } from '../../../../../../domain/flow/editDesign';

type Patch = (fields: readonly (readonly [string, DesignJson])[]) => string | null;
export type MeasuredDesignRect = Rect & Partial<Pick<PlacedLayer, 'parent' | 'contentInset'>>;
const coordinates = [
  { key: 'x', label: 'X' },
  { key: 'y', label: 'Y' },
  { key: 'w', label: '가로' },
  { key: 'h', label: '세로' },
] as const;

function NumericField({
  label,
  value,
  onCommit,
  reason,
}: {
  label: string;
  value: number | undefined;
  onCommit?: (v: number) => string | null;
  reason?: string;
}) {
  const [draft, setDraft] = useState<{ base: number; text: string } | null>(null);
  function commit() {
    if (!draft) return;
    const next = Number(draft.text);
    if (onCommit && draft.base === value && draft.text.trim() && Number.isFinite(next) && next !== value)
      onCommit(next);
    setDraft(null);
  }
  return (
    <label>
      {label}
      <input
        type="number"
        step="any"
        value={draft?.text ?? value ?? ''}
        placeholder="—"
        readOnly={!onCommit}
        title={!onCommit ? reason : undefined}
        onChange={(e) => {
          if (onCommit && value !== undefined) setDraft({ base: draft?.base ?? value, text: e.target.value });
        }}
        onBlur={commit}
        onKeyDown={(e) => {
          if (e.key === 'Enter') e.currentTarget.blur();
          if (e.key === 'Escape') setDraft(null);
        }}
      />
    </label>
  );
}
function numeric(value: DesignJson | undefined) {
  return typeof value === 'number' ? value : undefined;
}
// DOM zoom can report an integer position just below the pixel boundary. Only round the displayed measurement.
function displayPx(value: number | undefined) {
  if (value === undefined) return undefined;
  const nearest = Math.round(value);
  return Math.abs(value - nearest) <= 0.001 ? nearest : value;
}
function mutableAnchor(value: DesignJson, parentSpan: number | undefined) {
  return (
    typeof value === 'number' ||
    (typeof object(value)?.percent === 'number' && parentSpan !== undefined && parentSpan > 0)
  );
}
export function DesignInspectorGeometry({
  geometry,
  frame,
  write,
  onLayer,
  rectActions,
  measuredRect,
  canResizeMeasured,
  onRectChange,
  sourceMismatch,
}: {
  geometry: DesignObject;
  frame?: { width: number; height: number };
  write: Patch;
  onLayer?: (front: boolean) => void;
  rectActions: boolean;
  measuredRect?: MeasuredDesignRect;
  canResizeMeasured: boolean;
  onRectChange: (key: keyof Rect, value: number) => string | null;
  sourceMismatch: boolean;
}) {
  const position = object(geometry.position),
    size = object(geometry.size),
    locked = geometry.locked === true,
    absolute = position?.mode === 'absolute';
  const hint = useId();
  const authored: { label: string; value: number; path: string; percent?: boolean }[] = [];
  const alternateCoordinates = sourceMismatch || (!!measuredRect && !canResizeMeasured);
  const axisFields = coordinates.map(({ key, label }) => {
    const dimension = key === 'w' || key === 'h',
      axis = key === 'x' || key === 'w' ? 'x' : 'y';
    const anchors = absolute ? object(position[axis]) : undefined;
    const span = axis === 'x' ? measuredRect?.parent?.width : measuredRect?.parent?.height;
    const direct = anchors && Object.keys(anchors).length === 1 ? numeric(anchors.start) : undefined;
    const length = object(size?.[axis]);
    const fixed = length?.mode === 'fixed' ? numeric(length.px) : undefined;
    const value = displayPx(measuredRect?.[key]) ?? (dimension ? fixed : direct);
    // East/south handle resizing keeps the start fixed; an end/center anchor must absorb the size delta.
    const canWriteMeasured = dimension
      ? !anchors ||
        ['end', 'center'].every((k) => anchors[k] === undefined || mutableAnchor(anchors[k], span))
      : !!anchors &&
        Object.values(anchors).length > 0 &&
        Object.values(anchors).every((v) => mutableAnchor(v, span));
    let onCommit: ((n: number) => string | null) | undefined;
    if (!locked && !sourceMismatch) {
      if (measuredRect && canResizeMeasured && canWriteMeasured)
        onCommit = (n) => onRectChange(key, dimension ? Math.max(16, n) : n);
      else if (!measuredRect && (dimension ? fixed : direct) !== undefined)
        onCommit = (n) =>
          dimension
            ? write([['size.' + axis + '.px', Math.max(rectActions ? 16 : 0, n)]])
            : write([['position.' + axis + '.start', n]]);
    }
    const reason = locked
      ? '위치 잠금'
      : !dimension && !absolute
        ? '자동 배치 · 읽기 전용'
        : sourceMismatch
          ? '현재 인스턴스의 측정값 · 읽기 전용'
          : '측정값 · 읽기 전용';
    return { key, label, value, onCommit, reason };
  });
  for (const axis of ['x', 'y'] as const) {
    const anchors = absolute ? object(position[axis]) : undefined;
    const sizeAxis = object(size?.[axis]);
    if (alternateCoordinates && sizeAxis?.mode === 'fixed' && typeof sizeAxis.px === 'number')
      authored.push({
        label: (axis === 'x' ? '가로' : '세로') + ' 고정값 (px)',
        value: sizeAxis.px,
        path: 'size.' + axis + '.px',
      });
    if (!anchors) continue;
    for (const key of ['start', 'end', 'center'] as const) {
      const anchor = anchors[key],
        pct = object(anchor)?.percent;
      if (
        anchor === undefined ||
        (key === 'start' &&
          Object.keys(anchors).length === 1 &&
          typeof anchor === 'number' &&
          !alternateCoordinates)
      )
        continue;
      const value = typeof anchor === 'number' ? anchor : numeric(pct);
      if (value !== undefined)
        authored.push({
          path: 'position.' + axis + '.' + key,
          label:
            (axis === 'x' ? '가로 ' : '세로 ') +
            { start: '시작', end: '끝', center: '중심' }[key] +
            (typeof anchor === 'number' ? ' (px)' : ' (%)'),
          value,
          percent: typeof anchor !== 'number',
        });
    }
  }
  const x = object(position?.x),
    w = object(size?.x)?.px;
  return (
    <div className="flow-inspector-box" aria-describedby={!absolute ? hint : undefined}>
      <h3>위치와 크기 (px)</h3>
      <div className="flow-geometry">
        {axisFields.map((f) => (
          <NumericField key={f.key} label={f.label} value={f.value} onCommit={f.onCommit} reason={f.reason} />
        ))}
      </div>
      {!absolute && <small id={hint}>X와 Y는 자동 배치가 정한 읽기 전용 좌표입니다.</small>}
      {sourceMismatch && (
        <small>현재 인스턴스에서 측정한 값입니다. 원본에 지정한 값은 아래에서 수정할 수 있습니다.</small>
      )}
      {authored.length > 0 && (
        <details>
          <summary>배치 기준값</summary>
          <div className="flow-geometry">
            {authored.map((f) => (
              <NumericField
                key={f.path}
                label={f.label}
                value={f.value}
                reason="위치 잠금"
                onCommit={
                  locked
                    ? undefined
                    : (n) =>
                        write([
                          [
                            f.path,
                            f.percent ? { percent: n } : f.path.startsWith('size.') ? Math.max(0, n) : n,
                          ],
                        ])
                }
              />
            ))}
          </div>
        </details>
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
      {absolute && (
        <small className="flow-placement-hint">화살표 키로 1px, Shift와 함께 누르면 10px씩 옮깁니다.</small>
      )}
    </div>
  );
}
