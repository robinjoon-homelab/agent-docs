import { useState } from 'react';
import type { DesignObject, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { object, records, setDesignAction, stringValue } from '../../../../../../domain/flow/editDesign';
import type { DesignMutation } from './DesignNodeInspector';
const modes = [
  ['same', '같은 화면'],
  ['page', '페이지'],
  ['sheet', '시트'],
  ['sheetClose', '시트 닫기'],
  ['popup', '팝업'],
  ['newWindow', '새 창'],
  ['error', '오류'],
] as const;
export function DesignActions({
  blocks,
  annotation,
  mutate,
}: {
  blocks: readonly FlowDesignBlock[];
  annotation: FlowDesignBlock;
  mutate: DesignMutation;
}) {
  const screens = blocks.filter((b) => b.design.kind === 'screen');
  const [when, setWhen] = useState('클릭'),
    [dest, setDest] = useState(stringValue(screens[0]?.design.id)),
    [state, setState] = useState(''),
    [mode, setMode] = useState('same'),
    [message, setMessage] = useState('');
  const states = blocks.filter((b) => b.design.kind === 'state' && b.design.screen === dest),
    actions = records(annotation.design.actions);
  const name = (id: unknown) =>
    stringValue(blocks.find((b) => b.design.id === id)?.design.name, typeof id === 'string' ? id : '');
  return (
    <details className="design-inspector-group">
      <summary>이동 편집 ({actions.length})</summary>
      {actions.map((a, i) => {
        const to = object(a.to);
        return (
          <div className="design-action-row" key={i}>
            <span>
              {stringValue(a.when)} · {name(to?.screen)} {name(to?.state)}
            </span>
            <button
              type="button"
              className="btn btn-small"
              aria-label={stringValue(a.when) + ' 이동 삭제'}
              onClick={() =>
                mutate((bs) =>
                  setDesignAction(bs, stringValue(annotation.design.id), { remove: i, expected: a }),
                )
              }
            >
              삭제
            </button>
          </div>
        );
      })}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (!dest || !when.trim()) return;
          const action: DesignObject = {
            when: when.trim(),
            to: { screen: dest, ...(state ? { state } : {}) },
            mode,
          };
          const error = mutate((bs) =>
            setDesignAction(bs, stringValue(annotation.design.id), { add: action }),
          );
          setMessage(error ?? '이동을 추가했습니다.');
        }}
      >
        <label className="design-field">
          이동 조건
          <input required value={when} onChange={(e) => setWhen(e.target.value)} />
        </label>
        <label className="design-field">
          목적 화면
          <select
            value={dest}
            onChange={(e) => {
              setDest(e.target.value);
              setState('');
            }}
          >
            {screens.map((b) => (
              <option key={b.id} value={stringValue(b.design.id)}>
                {stringValue(b.design.name)}
              </option>
            ))}
          </select>
        </label>
        <label className="design-field">
          목적 상태
          <select value={state} onChange={(e) => setState(e.target.value)}>
            <option value="">기본 상태</option>
            {states.map((b) => (
              <option key={b.id} value={stringValue(b.design.id)}>
                {stringValue(b.design.name)}
              </option>
            ))}
          </select>
        </label>
        <label className="design-field">
          여는 방식
          <select value={mode} onChange={(e) => setMode(e.target.value)}>
            {modes.map(([id, label]) => (
              <option key={id} value={id}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <button type="submit" className="btn btn-small">
          이동 추가
        </button>
        {message && <p role="status">{message}</p>}
      </form>
    </details>
  );
}
