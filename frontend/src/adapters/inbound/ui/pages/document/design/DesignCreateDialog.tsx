import { useState } from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import type { DesignAddress } from '../../../../../../domain/flow/designAddress';
import { randomBlockId } from '../../../editor/ids';
import {
  addDesignAnnotation,
  addDesignChild,
  createDesignBlock,
  makeDesignChild,
} from '../../../../../../domain/flow/editDesignStructure';
import { stringValue } from '../../../../../../domain/flow/editDesign';
import { Dialog } from '../../../common/Dialog';
import type { DesignLocation } from './designLocation';
import type { DesignMutation } from './useDesignEditHistory';
import { importDesignScreen } from './authoring/importDesignScreen';
export type CreateKind =
  'screen' | 'state' | 'component' | 'box' | 'text' | 'instance' | 'annotation' | 'import';
const newDesignId = randomBlockId;
const titles: Record<CreateKind, string> = {
  screen: '화면 추가',
  state: '상태 추가',
  component: '컴포넌트 만들기',
  box: '상자 추가',
  text: '텍스트 추가',
  instance: '컴포넌트 배치',
  annotation: '요소에 설명 붙이기',
  import: '마크업에서 화면 만들기',
};
export function DesignCreateDialog({
  kind,
  close,
  mutate,
  go,
  blocks,
  screen,
  state,
  parent,
  selected,
}: {
  kind: CreateKind;
  close: () => void;
  mutate: DesignMutation;
  go: (l: DesignLocation) => void;
  blocks: readonly FlowDesignBlock[];
  screen: FlowDesignBlock;
  state?: FlowDesignBlock;
  parent?: DesignAddress;
  selected?: DesignAddress;
}) {
  const [name, setName] = useState(''),
    [text, setText] = useState(''),
    [error, setError] = useState('');
  const components = blocks.filter((b) => b.design.kind === 'component');
  const [part, setPart] = useState(stringValue(components[0]?.design.id));
  const submit = () => {
    if (kind !== 'instance' && !name.trim()) {
      setError('이름이나 내용을 입력하세요.');
      return;
    }
    const target: { next?: DesignLocation } = {};

    const failure = mutate((bs) => {
      const id = newDesignId(),
        sc = stringValue(screen.design.id),
        st = typeof state?.design.id === 'string' ? state.design.id : undefined;
      let result: FlowDesignBlock[];
      if (kind === 'screen' || kind === 'state' || kind === 'component') {
        result = createDesignBlock(bs, kind, name, id, sc);
        if (kind === 'screen') target.next = { screen: id };
        if (kind === 'state') target.next = { screen: screen.id, state: id };
      } else if (kind === 'import') {
        const imported = importDesignScreen(text, bs, name, newDesignId);
        result = imported.blocks;
        target.next = { screen: imported.screenBlockId };
      } else if (kind === 'annotation') {
        if (!selected) throw Error('요소를 선택하세요.');
        result = addDesignAnnotation(bs, sc, st, selected, id, name, text);
      } else {
        if (!parent) throw Error('요소를 추가할 상자가 없습니다.');
        const n = makeDesignChild(kind, id, kind === 'instance' ? part : name, bs);
        result = addDesignChild(bs, sc, st, parent, n);
        target.next = { screen: screen.id, state: state?.id, address: { owner: st ?? sc, node: id } };
      }

      return result;
    });
    if (failure) setError(failure);
    if (!failure) {
      if (target.next) go(target.next);
      close();
    }
  };
  return (
    <Dialog open title={titles[kind]} onClose={close}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        {kind === 'instance' ? (
          <label className="design-field">
            컴포넌트
            <select value={part} onChange={(e) => setPart(e.target.value)}>
              {components.map((b) => (
                <option key={b.id} value={stringValue(b.design.id)}>
                  {stringValue(b.design.name)}
                </option>
              ))}
            </select>
          </label>
        ) : (
          <label className="design-field">
            {kind === 'text' ? '내용' : '이름'}
            <input autoFocus value={name} required onChange={(e) => setName(e.target.value)} />
          </label>
        )}
        {(kind === 'import' || kind === 'annotation') && (
          <label className="design-field">
            {kind === 'import' ? 'HTML/CSS/SVG 마크업' : '설명'}
            <textarea
              rows={8}
              required={kind === 'import'}
              value={text}
              onChange={(e) => setText(e.target.value)}
            />
          </label>
        )}
        {error && <p role="alert">{error}</p>}
        <div className="design-buttons">
          <button type="button" className="btn" onClick={close}>
            취소
          </button>
          <button type="submit" className="btn btn-primary">
            추가
          </button>
        </div>
      </form>
    </Dialog>
  );
}
