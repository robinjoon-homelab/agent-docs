import { useState } from 'react';
import type {
  DesignJson,
  DesignObject,
  FlowDesignBlock,
} from '../../../../../../domain/document/designBlock';
import type { DesignAddress } from '../../../../../../domain/flow/designAddress';
import {
  object,
  records,
  setDesignAction,
  stringValue,
  type DesignActionEdit,
  type DesignEditScope,
} from '../../../../../../domain/flow/editDesign';
import { editScopedAnnotation, patchAnnotation } from '../../../../../../domain/flow/editDesignProperties';
import type { DesignMutation } from './useDesignEditHistory';
import { ACTION_MODES } from '../FlowElementOptions';
import { randomBlockId } from '../../../editor/ids';
import { describeDestination } from './DesignOverview';

export function DesignActions({
  blocks,
  annotation,
  mutate,
  scope,
  target,
  onFollow,
}: {
  blocks: readonly FlowDesignBlock[];
  annotation: FlowDesignBlock;
  mutate: DesignMutation;
  scope: DesignEditScope;
  target: DesignAddress;
  onFollow?: (to: DesignJson | undefined) => void;
}) {
  const [stateName, setStateName] = useState('');
  const screens = blocks.filter((b) => b.design.kind === 'screen'),
    actions = records(annotation.design.actions);
  const apply = (change: DesignActionEdit) =>
    mutate((bs) =>
      editScopedAnnotation(bs, scope, target, annotation.id, randomBlockId, (next, id) =>
        setDesignAction(next, id, change),
      ),
    );
  const states = Array.isArray(annotation.design.legacyStateNames)
    ? annotation.design.legacyStateNames.filter((v): v is string => typeof v === 'string')
    : [];
  function editStates(names: string[]) {
    mutate((bs) =>
      patchAnnotation(bs, scope, target, annotation.id, { legacyStateNames: names }, randomBlockId),
    );
  }
  return (
    <>
      <div className="flow-behavior-editor">
        <h3>누르면 가는 화면</h3>
        {!actions.length && (
          <p>
            {annotation.design.enabled === false
              ? '이동이 없습니다. 번호와 설명을 끈 요소입니다.'
              : '이동이 없습니다. 보기 모드에서 누르면 설명이 열립니다.'}
          </p>
        )}
        {actions.map((action, i) => {
          const to = object(action.to);
          const dest = stringValue(to?.screen, stringValue(object(to?.screen)?.unresolvedName));
          const state = stringValue(to?.state, stringValue(object(to?.state)?.unresolvedName));
          const targets = blocks.filter((b) => b.design.kind === 'state' && b.design.screen === dest);
          const display = describeDestination(blocks, action.to);
          function replace(patch: DesignObject) {
            return apply({ replace: i, expected: action, action: { ...action, ...patch } });
          }
          function destination(screen: string, state?: string) {
            const update = { ...action };
            delete update.to;
            if (screen) update.to = { screen, ...(state ? { state } : {}) };
            return apply({ replace: i, expected: action, action: update });
          }
          return (
            <div className="flow-action-editor" key={i}>
              <div>
                <span className="flow-action-badge">{i === 0 ? '첫 이동' : '이동 ' + String(i + 1)}</span>
                <input
                  aria-label="조건"
                  value={stringValue(action.when)}
                  placeholder="조건 (예: 저장 성공)"
                  onChange={(e) => replace({ when: e.target.value })}
                />
                <button
                  type="button"
                  aria-label="이 이동 삭제"
                  onClick={() => apply({ remove: i, expected: action })}
                >
                  ×
                </button>
              </div>
              <div>
                <select aria-label="가는 화면" value={dest} onChange={(e) => destination(e.target.value)}>
                  <option value="">이동 없음</option>
                  {dest && !screens.some((s) => s.design.id === dest) && (
                    <option value={dest}>{dest} (삭제됨)</option>
                  )}
                  {screens.map((b) => (
                    <option key={b.id} value={stringValue(b.design.id)}>
                      {stringValue(b.design.code)} {stringValue(b.design.name)}
                    </option>
                  ))}
                </select>
                <select
                  aria-label="여는 방식"
                  value={stringValue(action.mode, 'same')}
                  onChange={(e) => replace({ mode: e.target.value })}
                >
                  {Object.entries(ACTION_MODES).map(([id, name]) => (
                    <option key={id} value={id}>
                      {name}
                    </option>
                  ))}
                </select>
              </div>
              {(targets.length > 0 || state) && (
                <label>
                  목적 상태
                  <select value={state} onChange={(e) => destination(dest, e.target.value)}>
                    <option value="">기본 상태</option>
                    {state && !targets.some((b) => b.design.id === state) && (
                      <option value={state}>{state} (삭제됨)</option>
                    )}
                    {targets.map((b) => (
                      <option key={b.id} value={stringValue(b.design.id)}>
                        {stringValue(b.design.name)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {to && !display.valid && (
                <small className="flow-missing-target">
                  삭제된 화면 또는 상태입니다. 다른 대상을 고르세요.
                </small>
              )}
              {onFollow && display.valid && (
                <button type="button" className="design-text-link" onClick={() => onFollow(action.to)}>
                  {display.label} 보기
                </button>
              )}
            </div>
          );
        })}
        <button
          type="button"
          className="flow-add-action"
          onClick={() => apply({ add: { when: '누르면', mode: 'same' } })}
        >
          + 이동 추가
        </button>
        <small>첫 줄은 요소를 눌렀을 때의 이동입니다. 다른 조건은 요소 설명에서 선택할 수 있습니다.</small>
      </div>
      <div className="flow-behavior-editor">
        <h3>상태</h3>
        <div className="flow-state-list">
          {states.length ? (
            states.map((s, i) => (
              <span className="flow-state-chip" key={i}>
                {s}
                <button
                  type="button"
                  aria-label={s + ' 상태 삭제'}
                  onClick={() => editStates(states.filter((_, j) => i !== j))}
                >
                  ×
                </button>
              </span>
            ))
          ) : (
            <span>없음</span>
          )}
        </div>
        <div className="flow-new-state">
          <input
            aria-label="새 상태"
            placeholder="예: 비활성, 로딩, 오류"
            value={stateName}
            onChange={(e) => setStateName(e.target.value)}
          />
          <button
            type="button"
            disabled={!stateName.trim()}
            onClick={() => {
              editStates([...states, stateName.trim()]);
              setStateName('');
            }}
          >
            추가
          </button>
        </div>
      </div>
    </>
  );
}
