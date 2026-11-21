import { useState } from 'react';
import type { FlowElementBlock, FlowKind, FlowAction } from '../../../../../domain/document/block';
import type { Flow } from '../../../../../domain/flow/flow';
import { KINDS, newElement } from '../../../../../domain/flow/flow';
import { richPlain } from '../../../../../domain/flow/richText';
import { ACTION_MODES } from './FlowElementOptions';
const LOOKS: Partial<Record<FlowKind, Record<string, string>>> = {
  title: { large: '큰 제목', small: '작은 제목' },
  button: { primary: '진한 버튼', secondary: '테두리 버튼', disabled: '비활성' },
  notice: { info: '안내', error: '오류', ok: '성공' },
  field: { normal: '기본', inactive: '비활성' },
  select: { normal: '기본', inactive: '비활성' },
  area: { normal: '기본', inactive: '비활성' },
  box: {
    white: '흰 판',
    sheet: '아래 시트 판',
    gray: '회색 자리',
    dim: '어두운 덮개',
    outline: '점선 테두리',
    handle: '시트 손잡이',
  },
};
export function FlowElementInspector({
  element: e,
  flow,
  width,
  height,
  onPatch,
  onLayer,
  onDuplicate,
  onDelete,
  number,
  onUploadImage,
}: {
  element: FlowElementBlock;
  flow: Flow;
  width: number;
  height: number;
  onPatch: (patch: Partial<FlowElementBlock>) => void;
  onLayer: (front: boolean) => void;
  onDuplicate: () => void;
  onDelete: () => void;
  number: number;
  /** Uploads the file to the space and returns `asset:<id>`, or throws a message to show. */
  onUploadImage?: (file: File) => Promise<string>;
}) {
  const [stateName, setStateName] = useState('');
  const [uploadError, setUploadError] = useState('');
  const [uploading, setUploading] = useState(false);
  const action = (index: number, patch: Partial<FlowAction>) =>
    onPatch({ actions: e.actions.map((a, i) => (i === index ? { ...a, ...patch } : a)) });
  return (
    <div className="flow-inspector">
      <div className="flow-detail-title">
        {e.annotated && <span className="flow-number">{number}</span>}
        <h2>요소 속성</h2>
        <span className="flow-kind-chip">{KINDS[e.kind]}</span>
      </div>
      <div className="flow-two-columns">
        <label>
          종류
          <select
            value={e.kind}
            onChange={(event) => {
              const kind = event.target.value as FlowKind;
              const defaults = newElement(e.id, kind, width, height);
              onPatch({ kind, look: defaults.look, items: defaults.items, date: null });
            }}
          >
            {Object.entries(KINDS).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label>
          요소 이름
          <input
            value={e.label}
            maxLength={100}
            onChange={(event) => onPatch({ label: event.target.value })}
          />
        </label>
      </div>{' '}
      {e.kind === 'list' ? (
        <label>
          목록 항목 (한 줄에 하나)
          <textarea
            rows={4}
            value={e.items?.join('\n') ?? ''}
            onChange={(event) =>
              onPatch({
                items: event.target.value
                  .split('\n')
                  .slice(0, 100)
                  .map((v) => v.slice(0, 200)),
              })
            }
          />
        </label>
      ) : (
        e.kind !== 'box' && (
          <label>
            {e.kind === 'button'
              ? '버튼 글자'
              : e.kind === 'link'
                ? '링크 글자'
                : e.kind === 'title'
                  ? '제목 글자'
                  : e.kind === 'field' || e.kind === 'select'
                    ? '보이는 값'
                    : '보이는 글자'}
            <input
              value={e.value}
              maxLength={500}
              onChange={(event) => onPatch({ value: event.target.value })}
            />
          </label>
        )
      )}
      {e.kind === 'image' && onUploadImage && (
        <div className="flow-image-upload">
          <label className="flow-upload-button">
            {uploading ? '올리는 중…' : '이미지 올리기'}
            <input
              type="file"
              accept="image/*"
              hidden
              disabled={uploading}
              onChange={(event) => {
                const file = event.target.files?.[0];
                event.target.value = '';
                if (!file) return;
                setUploading(true);
                setUploadError('');
                onUploadImage(file)
                  .then((value) => onPatch({ value }))
                  .catch((error: unknown) =>
                    setUploadError(error instanceof Error ? error.message : '이미지를 올리지 못했습니다.'),
                  )
                  .finally(() => setUploading(false));
              }}
            />
          </label>
          <small>
            보이는 글자가 asset:ID이면 그 이미지를 요소 크기에 맞춰 보여 줍니다. 요소 이름이 대체
            텍스트입니다.
          </small>
          {uploadError && <p role="alert">{uploadError}</p>}
        </div>
      )}
      {LOOKS[e.kind] && (
        <label>
          모양
          <select value={e.look ?? ''} onChange={(event) => onPatch({ look: event.target.value })}>
            {Object.entries(LOOKS[e.kind] ?? {}).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
        </label>
      )}
      {e.kind === 'path' && (
        <label>
          날짜 (선택)
          <input
            value={e.date ?? ''}
            maxLength={40}
            onChange={(event) => onPatch({ date: event.target.value || null })}
          />
        </label>
      )}
      <div className="flow-inspector-box">
        <h3>위치와 크기 (px)</h3>
        <div className="flow-geometry">
          {(['x', 'y', 'w', 'h'] as const).map((key) => (
            <label key={key}>
              {{ x: 'X', y: 'Y', w: '가로', h: '세로' }[key]}
              <input
                type="number"
                value={e[key]}
                min={key === 'w' || key === 'h' ? 16 : 0}
                max={10000}
                onChange={(event) =>
                  onPatch({
                    [key]: Math.max(
                      key === 'w' || key === 'h' ? 16 : 0,
                      Math.min(10000, Number(event.target.value)),
                    ),
                  })
                }
              />
            </label>
          ))}
        </div>
        <div className="flow-inspector-buttons">
          <button disabled={e.pinned} onClick={() => onPatch({ x: Math.round((width - e.w) / 2) })}>
            가로 가운데
          </button>
          <button disabled={e.pinned} onClick={() => onPatch({ x: 16, w: width - 32 })}>
            좌우 여백 16 맞춤
          </button>
          <button onClick={() => onLayer(true)}>맨 앞으로</button>
          <button onClick={() => onLayer(false)}>맨 뒤로</button>
        </div>
        <label className="flow-check">
          <input
            type="checkbox"
            checked={e.pinned}
            onChange={(event) => onPatch({ pinned: event.target.checked })}
          />
          위치 잠금 (끌어서 옮기거나 크기를 바꾸지 못하게)
        </label>
        {e.group && (
          <div className="flow-inspector-buttons">
            <span className="flow-state-chip">
              묶음 · 요소{' '}
              {flow.screens.flatMap((s) => s.elements).filter((el) => el.group === e.group).length}개
            </span>
            <button onClick={() => onPatch({ group: null })}>묶음 풀기</button>
          </div>
        )}
        <small className="flow-placement-hint">
          화면 안에서 끌어서 옮기고, 모서리·변의 점으로 크기를 바꿉니다. 4px 격자에 붙고(Alt를 누르면 붙지
          않음), 다른 요소나 화면의 가장자리·가운데와 4px 안으로 맞으면 붙으면서 분홍 안내선이 보입니다.
          화살표 키 1px, Shift+화살표 10px. ⌘C·⌘V 복사·붙여넣기, ⌘D 복제, ⌘Z 실행 취소, Delete 삭제.
        </small>
      </div>
      <label className="flow-annotate">
        <input
          type="checkbox"
          checked={e.annotated}
          onChange={(event) => onPatch({ annotated: event.target.checked })}
        />
        <span>
          <strong>번호와 설명 붙이기</strong>
          <small>끄면 화면 모양만 채우는 요소가 되고, 번호·(i)·설명이 없습니다.</small>
        </span>
      </label>
      {e.annotated && (
        <div className="flow-description-editor">
          <label>
            설명 (보기 모드의 (i) 내용)
            <textarea
              rows={5}
              value={e.desc}
              maxLength={5000}
              placeholder="이 요소가 무엇이고, 누르면 어떻게 되는지, 조건과 예외를 적으세요."
              onChange={(event) => onPatch({ desc: event.target.value })}
            />
          </label>
          <small>
            {e.desc.length}자 · 화면 설명의 요소 목록에는 앞 60자만 보이고 "더보기"로 펼칩니다. 목록(- ),
            표(|), **굵게**, `코드`, [링크](doc:ID)를 쓸 수 있습니다.
          </small>
          {e.desc.trim() ? (
            <div className="flow-description-preview">
              <strong>목록 미리보기</strong>
              {richPlain(e.desc).slice(0, 60)}
              {richPlain(e.desc).length > 60 && (
                <>
                  … <span>더보기</span>
                </>
              )}
            </div>
          ) : (
            <p className="flow-empty-description" role="status">
              설명이 비어 있습니다. 보기 모드에서 (i)를 눌러도 내용이 없습니다. 저장은 됩니다.
            </p>
          )}
        </div>
      )}
      <div className="flow-behavior-editor">
        <h3>누르면 가는 화면</h3>
        {!e.actions.length && (
          <p>
            {e.annotated
              ? '이동이 없습니다. 보기 모드에서 누르면 설명이 열립니다.'
              : '이동이 없습니다. 번호와 설명을 끈 요소라 보기 모드에서 눌러도 반응하지 않습니다.'}
          </p>
        )}
        {e.actions.map((a, index) => (
          <div className="flow-action-editor" key={index}>
            <div>
              <span className="flow-action-badge">{index === 0 ? '첫 이동' : `이동 ${index + 1}`}</span>
              <input
                aria-label="조건"
                maxLength={100}
                value={a.when}
                placeholder="조건 (예: 저장 성공)"
                onChange={(event) => action(index, { when: event.target.value })}
              />
              <button
                aria-label="이 이동 삭제"
                onClick={() => onPatch({ actions: e.actions.filter((_, i) => i !== index) })}
              >
                ×
              </button>
            </div>
            <div>
              <select
                aria-label="가는 화면"
                value={a.to ?? ''}
                onChange={(event) => action(index, { to: event.target.value || null })}
              >
                <option value="">이동 없음</option>
                {a.to && !flow.screens.some((s) => s.screen.screenId === a.to) && (
                  <option value={a.to}>{a.to} (삭제됨)</option>
                )}
                {flow.screens.map((s) => (
                  <option key={s.screen.id} value={s.screen.screenId}>
                    {s.screen.screenId} {s.screen.name}
                  </option>
                ))}
              </select>
              <select
                aria-label="여는 방식"
                value={a.mode}
                onChange={(event) => action(index, { mode: event.target.value as FlowAction['mode'] })}
              >
                {Object.entries(ACTION_MODES).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            {a.to && !flow.screens.some((s) => s.screen.screenId === a.to) && (
              <small className="flow-missing-target">삭제된 화면을 가리킵니다. 다른 화면을 고르세요.</small>
            )}
          </div>
        ))}
        <button
          className="flow-add-action"
          disabled={e.actions.length >= 20}
          onClick={() => onPatch({ actions: [...e.actions, { when: '누르면', to: null, mode: 'same' }] })}
        >
          + 이동 추가
        </button>
        <small>첫 줄이 요소를 눌렀을 때 가는 화면입니다. 나머지는 조건별 결과로 설명에 함께 보입니다.</small>
      </div>
      <div className="flow-behavior-editor">
        <h3>상태</h3>
        <div className="flow-state-list">
          {e.states.length ? (
            e.states.map((state, index) => (
              <span className="flow-state-chip" key={index}>
                {state}
                <button
                  aria-label={`${state} 상태 삭제`}
                  onClick={() => onPatch({ states: e.states.filter((_, i) => i !== index) })}
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
            maxLength={50}
            onChange={(event) => setStateName(event.target.value)}
          />
          <button
            disabled={!stateName.trim() || e.states.length >= 20}
            onClick={() => {
              onPatch({ states: [...e.states, stateName.trim()] });
              setStateName('');
            }}
          >
            추가
          </button>
        </div>
      </div>
      <div className="flow-inspector-buttons flow-element-footer">
        <button onClick={onDuplicate}>복제</button>
        <button className="flow-danger-outline" onClick={onDelete}>
          요소 삭제
        </button>
      </div>
    </div>
  );
}
