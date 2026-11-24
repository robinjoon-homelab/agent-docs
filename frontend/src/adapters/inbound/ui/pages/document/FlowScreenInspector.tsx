import type { FlowFrame, FlowScreenBlock, FlowSettingsBlock } from '../../../../../domain/document/block';
import type { Flow, FlowScreen } from '../../../../../domain/flow/flow';
import { FRAMES, nextScreenId, numbered } from '../../../../../domain/flow/flow';
export function FlowScreenInspector({
  flow,
  active,
  onScreen,
  onSettings,
  onMove,
  onDuplicate,
  onDelete,
}: {
  flow: Flow;
  active: FlowScreen;
  onScreen: (patch: Partial<FlowScreenBlock>) => void;
  onSettings: (patch: Partial<FlowSettingsBlock>) => void;
  onMove: (direction: number) => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  const screen = active.screen,
    index = flow.screens.indexOf(active),
    annotated = numbered(active.elements);
  return (
    <FlowScreenFields
      screen={screen}
      settings={flow.settings}
      nextCode={nextScreenId(flow.settings)}
      previous={index > 0}
      following={index < flow.screens.length - 1}
      canDelete={flow.screens.length > 1}
      canMakeNote={!active.elements.length}
      counts={{
        elements: active.elements.length,
        annotations: annotated.length,
        missing: annotated.filter((e) => !e.desc.trim()).length,
      }}
      onScreen={onScreen}
      onSettings={onSettings}
      onMove={onMove}
      onDuplicate={onDuplicate}
      onDelete={onDelete}
    />
  );
}

export function FlowScreenFields({
  screen,
  settings,
  nextCode,
  previous,
  following,
  canDelete,
  canMakeNote,
  counts,
  onScreen,
  onSettings,
  onMove,
  onDuplicate,
  onDelete,
}: {
  screen: Pick<FlowScreenBlock, 'screenId' | 'name' | 'entry' | 'desc' | 'frame' | 'frameW' | 'frameH'>;
  settings: Pick<FlowSettingsBlock, 'idPrefix' | 'frame'>;
  nextCode: string;
  previous: boolean;
  following: boolean;
  canDelete: boolean;
  canMakeNote: boolean;
  counts: { elements: number; annotations: number; missing: number };
  onScreen: (p: Partial<FlowScreenBlock>) => void;
  onSettings: (p: Partial<FlowSettingsBlock>) => void;
  onMove: (d: number) => void;
  onDuplicate: () => void;
  onDelete: () => void;
}) {
  return (
    <div className="flow-inspector">
      <div className="flow-section-eyebrow">
        <span className="flow-screen-id">{screen.screenId}</span>
        <h2>화면 속성</h2>
      </div>
      <p className="flow-inspector-hint">
        화면 ID는 자동으로 붙고, 지운 화면의 번호는 다시 쓰지 않습니다. 다음 새 화면은 {nextCode}입니다.
      </p>
      <label>
        화면 이름 (상단 버튼에 보이는 이름)
        <input value={screen.name} maxLength={100} onChange={(e) => onScreen({ name: e.target.value })} />
      </label>
      <label>
        진입 경로
        <input
          value={screen.entry}
          maxLength={200}
          placeholder="예: 사이드바 › 빠른 메모"
          onChange={(e) => onScreen({ entry: e.target.value })}
        />
      </label>
      <label>
        화면 설명
        <textarea
          rows={5}
          value={screen.desc}
          maxLength={20000}
          placeholder="이 화면이 무엇을 하는지, 언제 들어오는지, 핵심 규칙을 적으세요."
          onChange={(e) => onScreen({ desc: e.target.value })}
        />
        <small>보기 모드에서 이 화면 버튼을 누르면 오른쪽 맨 위에 보입니다.</small>
      </label>
      <div className="flow-inspector-box">
        <label>
          기기 틀 (이 화면)
          <select
            value={screen.frame ?? ''}
            onChange={(e) =>
              onScreen({
                frame: (e.target.value || null) as FlowFrame | 'custom' | 'note' | null,
                frameW: e.target.value === 'custom' ? 600 : null,
                frameH: e.target.value === 'custom' ? 720 : null,
              })
            }
          >
            <option value="">문서 기본값 따름 ({FRAMES[settings.frame][0]})</option>
            {Object.entries(FRAMES).map(([key, value]) => (
              <option key={key} value={key}>
                {value[0]} {value[1]}×{value[2]}
              </option>
            ))}
            <option value="custom">직접 입력</option>
            <option value="note" disabled={!canMakeNote}>
              글 페이지 (요소 없는 화면만)
            </option>
          </select>
          {screen.frame === 'note' && <small>가운데에 기기 틀 대신 화면 설명을 넓은 글로 보여 줍니다.</small>}
        </label>
        {screen.frame === 'custom' && (
          <div className="flow-size-inputs">
            {(['frameW', 'frameH'] as const).map((key, i) => (
              <span key={key}>
                {i > 0 ? ' × ' : ''}
                <input
                  aria-label={i === 0 ? '가로 (px)' : '세로 (px)'}
                  type="number"
                  min={200}
                  max={3000}
                  value={screen[key] ?? 600}
                  onChange={(e) =>
                    onScreen({ [key]: Math.max(200, Math.min(3000, Math.round(Number(e.target.value)))) })
                  }
                />
              </span>
            ))}{' '}
            px
          </div>
        )}
        <label>
          문서 기본 틀
          <select value={settings.frame} onChange={(e) => onSettings({ frame: e.target.value as FlowFrame })}>
            {Object.entries(FRAMES).map(([key, value]) => (
              <option key={key} value={key}>
                {value[0]} {value[1]}×{value[2]}
              </option>
            ))}
          </select>
          <small>새 화면과 "문서 기본값 따름" 화면에 쓰입니다. 틀을 바꿔도 요소 위치는 그대로입니다.</small>
        </label>
        <label>
          화면 ID 접두어 (문서 전체)
          <input
            className="flow-prefix"
            value={settings.idPrefix}
            maxLength={4}
            placeholder="S"
            onChange={(e) => onSettings({ idPrefix: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '') })}
          />
          <small>
            영문 대문자·숫자 4자까지. 새로 만드는 화면부터 적용되고, 이미 붙은 ID는 바뀌지 않습니다. 비우면
            S를 씁니다.
          </small>
        </label>
      </div>
      <div className="flow-stat-chips">
        <span>요소 {counts.elements}</span>
        <span>설명 붙은 요소 {counts.annotations}</span>
        {counts.missing > 0 && <span>설명 없는 요소 {counts.missing}</span>}
      </div>
      <div className="flow-inspector-buttons">
        <button disabled={!previous} onClick={() => onMove(-1)}>
          ‹ 앞으로
        </button>
        <button disabled={!following} onClick={() => onMove(1)}>
          뒤로 ›
        </button>
        <button onClick={onDuplicate}>화면 복제</button>
        <button className="flow-danger-outline" disabled={!canDelete} onClick={onDelete}>
          화면 삭제
        </button>
      </div>
      <p className="flow-multi-hint">
        요소를 넣으려면 가운데 왼쪽 '요소 추가'를 누르세요. 화면 안의 요소를 누르면 여기가 요소 속성으로
        바뀝니다.
      </p>
    </div>
  );
}
