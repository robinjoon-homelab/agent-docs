import { DesignDeleteConfirmation } from './DesignDeleteConfirmation';
import { useState } from 'react';
import type { FlowFrame, FlowScreenBlock, FlowSettingsBlock } from '../../../../../../domain/document/block';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { FRAMES } from '../../../../../../domain/flow/flow';
import { object, records, sourceBlock, stringValue } from '../../../../../../domain/flow/editDesign';
import {
  createDesignBlock,
  nextDesignNumber,
  setDesignFrame,
} from '../../../../../../domain/flow/editDesignStructure';
import {
  canSetNote,
  deleteDesignScreen,
  duplicateDesignScreen,
  moveDesignScreen,
  setScreenPreset,
} from '../../../../../../domain/flow/editDesignScreens';
import { randomBlockId } from '../../../editor/ids';
import { FlowScreenFields } from '../FlowScreenInspector';
import type { DesignLocation } from './designLocation';
import type { DesignMutation } from './useDesignEditHistory';
import { DesignProperty } from './DesignProperty';

type ScreenProps = {
  blocks: readonly FlowDesignBlock[];
  screen: FlowDesignBlock;
  state?: FlowDesignBlock;
  mutate: DesignMutation;
};
const isPreset = (value: unknown): value is FlowFrame =>
  typeof value === 'string' && Object.hasOwn(FRAMES, value);
function frameOf(screen: FlowDesignBlock): FlowScreenBlock['frame'] {
  const frame = screen.design.frame;
  if (isPreset(frame) || frame === 'note') return frame;
  return object(frame) ? 'custom' : null;
}

function sizeOf(blocks: readonly FlowDesignBlock[], screen: FlowDesignBlock, state?: FlowDesignBlock) {
  const settings = blocks.find((b) => b.design.kind === 'settings');
  const raw = screen.design.frame ?? settings?.design.frame;
  const actual = object(raw),
    override = object(state?.design.frame);
  const preset = isPreset(raw) ? FRAMES[raw] : FRAMES.desktop;
  return {
    width: Number(override?.width ?? actual?.width ?? preset[1]),
    height: Number(override?.height ?? actual?.height ?? preset[2]),
  };
}

export function addNamedDesignScreen(
  mutate: DesignMutation,
  go: (loc: DesignLocation) => void,
  screen: FlowDesignBlock,
) {
  const id = randomBlockId();
  const error = mutate((bs) => createDesignBlock(bs, 'screen', '새 화면', id, stringValue(screen.design.id)));
  if (!error) go({ screen: id });
}

function patchScreen(bs: readonly FlowDesignBlock[], id: string, patch: Partial<FlowScreenBlock>) {
  const screen = sourceBlock(bs, id, 'screen');
  if (!screen) throw Error('화면을 찾을 수 없습니다.');
  if (patch.frame !== undefined)
    return setScreenPreset(bs, id, patch.frame ?? '', {
      width: patch.frameW ?? 600,
      height: patch.frameH ?? 720,
    });
  if (patch.frameW != null || patch.frameH != null) {
    const size = sizeOf(bs, screen);
    return setDesignFrame(bs, id, undefined, patch.frameW ?? size.width, patch.frameH ?? size.height);
  }
  const design = { ...screen.design };
  if (patch.name !== undefined) design.name = patch.name;
  if (patch.entry !== undefined) design.entry = patch.entry;
  if (patch.desc !== undefined) design.description = patch.desc;
  return bs.map((b) => (b === screen ? { ...b, design } : b));
}
export function DesignFramePicker({ blocks, screen, state, mutate }: ScreenProps) {
  const settings = blocks.find((b) => b.design.kind === 'settings');
  const base = settings?.design.frame;
  const size = sizeOf(blocks, screen, state);
  const sc = stringValue(screen.design.id);
  const override = object(state?.design.frame);
  const frame = state
    ? override
      ? (Object.entries(FRAMES).find(([, v]) => v[1] === override.width && v[2] === override.height)?.[0] ??
        'custom')
      : ''
    : frameOf(screen);
  return (
    <label className="flow-frame-select">
      기기 틀
      <select
        value={frame ?? ''}
        onChange={(e) => {
          const preset = e.target.value as FlowFrame | 'custom' | 'note' | '';
          mutate((bs) => {
            if (!state)
              return setScreenPreset(
                bs,
                sc,
                preset,
                preset === 'custom' ? size : { width: 600, height: 720 },
              );
            const current = sourceBlock(bs, stringValue(state.design.id), 'state');
            if (current?.design.screen !== sc) throw Error('현재 상태를 찾을 수 없습니다.');
            if (!preset) {
              const design = { ...current.design };
              delete design.frame;
              return bs.map((b) => (b === current ? { ...b, design } : b));
            }
            const frameSize = isPreset(preset)
              ? { width: FRAMES[preset][1], height: FRAMES[preset][2] }
              : size;
            return setDesignFrame(bs, sc, stringValue(state.design.id), frameSize.width, frameSize.height);
          });
        }}
      >
        <option value="">
          {state ? '기본 화면 따름' : '문서 기본값 (' + (isPreset(base) ? FRAMES[base][0] : '데스크톱') + ')'}
        </option>
        {Object.entries(FRAMES).map(([key, [name, w, h]]) => (
          <option key={key} value={key}>
            {name} {w}×{h}
          </option>
        ))}
        <option value="custom">직접 입력</option>
        {!state && (
          <option value="note" disabled={!canSetNote(blocks, screen)}>
            글 페이지
          </option>
        )}
      </select>
    </label>
  );
}

export function DesignNoteEditor({ screen, mutate }: Pick<ScreenProps, 'screen' | 'mutate'>) {
  return (
    <article className="flow-note">
      <textarea
        className="flow-note-editor"
        aria-label="글 페이지 본문"
        maxLength={20000}
        value={stringValue(screen.design.description)}
        onChange={(e) =>
          mutate((bs) => patchScreen(bs, stringValue(screen.design.id), { desc: e.target.value }))
        }
      />
      <p className="flow-frame-caption">글 페이지 · {stringValue(screen.design.description).length}자</p>
    </article>
  );
}

export function DesignScreenProperties({
  blocks,
  screen,
  state,
  mutate,
  go,
  threadCount,
  elementCount,
  annotationCount,
  missingCount,
}: ScreenProps & {
  go: (loc: DesignLocation) => void;
  threadCount: number;
  elementCount: number;
  annotationCount: number;
  missingCount: number;
}) {
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const id = stringValue(screen.design.id);
  const screens = blocks.filter((b) => b.design.kind === 'screen');
  const settings = blocks.find((b) => b.design.kind === 'settings');
  if (!settings) return null;
  const prefix = stringValue(settings.design.idPrefix);
  const defaultFrame = isPreset(settings.design.frame) ? settings.design.frame : 'desktop';
  const size = sizeOf(blocks, screen, state);
  const index = screens.findIndex((b) => b.id === screen.id);
  function setSettings(p: Partial<FlowSettingsBlock>) {
    mutate((bs) =>
      bs.map((b) =>
        b.design.kind === 'settings'
          ? {
              ...b,
              design: {
                ...b.design,
                ...(p.frame ? { frame: p.frame } : {}),
                ...(p.idPrefix !== undefined ? { idPrefix: p.idPrefix } : {}),
              },
            }
          : b,
      ),
    );
  }
  function navigateAfter(
    change: (bs: readonly FlowDesignBlock[]) => { blocks: FlowDesignBlock[]; screen: string },
  ) {
    let next: string | undefined;
    const error = mutate((bs) => {
      const result = change(bs);
      next = result.screen;
      return result.blocks;
    });
    if (!error && next) {
      setConfirmation(null);
      go({ screen: next });
    }
  }
  if (state) {
    const set = (field: 'description' | 'name', value: string) =>
      mutate((bs) => {
        const model = sourceBlock(bs, stringValue(state.design.id), 'state');
        if (model?.design.screen !== id) throw Error('현재 화면의 상태를 찾을 수 없습니다.');
        if (field === 'name' && !value.trim()) throw Error('상태 이름을 입력하세요.');
        return bs.map((b) => (b === model ? { ...b, design: { ...b.design, [field]: value } } : b));
      });
    return (
      <div className="flow-inspector">
        <div className="flow-section-eyebrow">
          <span className="flow-screen-id">{stringValue(screen.design.code)}</span>
          <h2>상태 속성</h2>
        </div>
        <DesignProperty
          label="상태 이름"
          value={stringValue(state.design.name)}
          onCommit={(v) => set('name', v)}
        />
        {(['width', 'height'] as const).map((axis) => (
          <DesignProperty
            key={axis}
            label={axis === 'width' ? '화면 너비 (px)' : '화면 높이 (px)'}
            type="number"
            value={String(size[axis])}
            onCommit={(v) =>
              mutate((bs) =>
                setDesignFrame(
                  bs,
                  id,
                  stringValue(state.design.id),
                  axis === 'width' ? Number(v) : size.width,
                  axis === 'height' ? Number(v) : size.height,
                  axis,
                ),
              )
            }
          />
        ))}
        <DesignProperty
          type="long"
          label="설명"
          value={stringValue(state.design.description)}
          onCommit={(v) => set('description', v)}
        />
        <button className="btn btn-small" onClick={() => go({ screen: screen.id })}>
          기본 화면 속성
        </button>
      </div>
    );
  }
  const inbound = blocks.filter(
    (b) =>
      b.design.kind === 'annotation' &&
      b.design.screen !== id &&
      records(b.design.actions).some((a) => object(a.to)?.screen === id),
  ).length;
  return (
    <>
      {confirmation === id && (
        <DesignDeleteConfirmation
          label="화면 삭제 확인"
          title={`${stringValue(screen.design.code)} ${stringValue(screen.design.name)} 화면을 삭제할까요?`}
          notice={`이 화면에 달린 댓글 ${threadCount}개는 댓글 탭의 “삭제된 화면”으로 옮겨지고, 누르면 삭제 전 화면을 볼 수 있습니다.${inbound > 0 ? ' 이 화면으로 가는 요소 ' + String(inbound) + '개에는 “없는 화면” 경고가 붙습니다.' : ''}`}
          onCancel={() => setConfirmation(null)}
          onConfirm={() => navigateAfter((bs) => deleteDesignScreen(bs, id))}
        />
      )}
      <FlowScreenFields
        screen={{
          screenId: stringValue(screen.design.code),
          name: stringValue(screen.design.name),
          entry: stringValue(screen.design.entry),
          desc: stringValue(screen.design.description),
          frame: frameOf(screen),
          frameW: size.width,
          frameH: size.height,
        }}
        settings={{ idPrefix: prefix, frame: defaultFrame }}
        nextCode={(prefix || 'S') + '-' + String(nextDesignNumber(blocks)).padStart(2, '0')}
        previous={index > 0}
        following={index < screens.length - 1}
        canDelete={screens.length > 1}
        canMakeNote={canSetNote(blocks, screen)}
        counts={{ elements: elementCount, annotations: annotationCount, missing: missingCount }}
        onScreen={(p) => {
          mutate((bs) => patchScreen(bs, id, p));
        }}
        onSettings={setSettings}
        onMove={(direction) => mutate((bs) => moveDesignScreen(bs, id, direction < 0 ? -1 : 1))}
        onDuplicate={() => navigateAfter((bs) => duplicateDesignScreen(bs, id, randomBlockId))}
        onDelete={() => setConfirmation(id)}
      />
    </>
  );
}
