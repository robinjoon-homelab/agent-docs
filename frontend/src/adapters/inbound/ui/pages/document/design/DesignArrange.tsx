import { useRef, useState } from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import type { Rect } from '../../../../../../domain/flow/flow';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { findDesignNode, object, records, stringValue } from '../../../../../../domain/flow/editDesign';
import {
  arrangeDesignSiblings,
  copyDesignSiblings,
  markDesignSiblings,
  pasteDesignSiblings,
  type Arrange,
  type DesignClipboard,
} from '../../../../../../domain/flow/editDesignSelection';
import { randomBlockId } from '../../../editor/ids';
import type { DesignMutation } from './DesignNodeInspector';

const options: readonly [Arrange, string][] = [
  ['left', '왼쪽 맞춤'],
  ['center', '가로 가운데 맞춤'],
  ['right', '오른쪽 맞춤'],
  ['top', '위쪽 맞춤'],
  ['middle', '세로 가운데 맞춤'],
  ['bottom', '아래쪽 맞춤'],
  ['horizontal', '가로 간격 균등'],
  ['vertical', '세로 간격 균등'],
];
export function DesignArrange({
  blocks,
  parent,
  mutate,
  onSelect,
}: {
  blocks: readonly FlowDesignBlock[];
  parent?: DesignAddress;
  mutate: DesignMutation;
  onSelect: (a: DesignAddress) => void;
}) {
  const [selection, setSelection] = useState<{ scope: string; ids: string[] }>({ scope: '', ids: [] });
  const [copied, setCopied] = useState<DesignClipboard | null>(null);
  const [status, setStatus] = useState(''),
    [operation, setOperation] = useState<Arrange>('left');
  const element = useRef<HTMLDetailsElement>(null);
  if (!parent || parent.via?.length) return null;
  const n = findDesignNode(blocks, parent);
  if (!n || !Array.isArray(n.children)) return null;
  const scope = designAddressKey(parent),
    available = records(n.children).filter((c) => !['slot', 'repeat'].includes(stringValue(c.kind)));
  const ids =
    scope === selection.scope ? selection.ids.filter((id) => available.some((c) => c.id === id)) : [];
  const selected = available.filter((c) => ids.includes(stringValue(c.id)));
  const locked = selected.length > 0 && selected.every((c) => c.locked === true);
  const grouped =
    selected.length > 0 &&
    selected.every((c) => typeof c.group === 'string' && c.group === selected[0]?.group);
  const canAlign =
    selected.length >= 2 &&
    selected.every((c) => {
      const p = object(c.position),
        x = object(p?.x),
        y = object(p?.y);
      return (
        !c.locked &&
        p?.mode === 'absolute' &&
        typeof x?.start === 'number' &&
        x.end === undefined &&
        typeof y?.start === 'number' &&
        y.end === undefined
      );
    });
  const choose = (id: string, checked: boolean) => {
    const current = available.find((c) => c.id === id);
    const groupedIds = available
      .filter((c) => c.id === id || (current?.group && c.group === current.group))
      .map((c) => stringValue(c.id));
    setSelection({
      scope,
      ids: checked ? [...new Set([...ids, ...groupedIds])] : ids.filter((i) => !groupedIds.includes(i)),
    });
    setStatus('');
  };
  const frames = () => {
    const result = new Map<string, Rect>(),
      stage = element.current?.closest('.design-layout')?.querySelector('.design-stage');
    const dom = new Map(
      Array.from(stage?.querySelectorAll<HTMLElement>('[data-design-address]') ?? []).map((el) => [
        el.dataset.designAddress,
        el,
      ]),
    );
    selected.forEach((c) => {
      const id = stringValue(c.id),
        p = object(c.position),
        x = object(p?.x)?.start,
        y = object(p?.y)?.start;
      const box = dom.get(designAddressKey({ owner: parent.owner, node: id }));
      if (typeof x === 'number' && typeof y === 'number' && box?.offsetWidth && box.offsetHeight)
        result.set(id, { x, y, w: box.offsetWidth, h: box.offsetHeight });
    });
    return result;
  };
  const paste = (duplicate: boolean) => {
    let newIds: readonly string[] = [];
    const error = mutate((bs) => {
      const source = duplicate ? copyDesignSiblings(bs, parent, ids) : copied;
      if (!source) throw Error('복사한 요소가 없습니다.');
      const result = pasteDesignSiblings(bs, parent, source, randomBlockId);
      newIds = result.ids;
      return result.blocks;
    });
    if (!error) {
      setSelection({ scope, ids: [...newIds] });
      const first = newIds[0];
      if (first) onSelect({ owner: parent.owner, node: first });
    }
  };
  return (
    <details className="design-inspector-group" ref={element}>
      <summary>여러 레이어 편집</summary>
      <p className="muted">
        {stringValue(n.name, stringValue(n.alias, parent.node))} · {ids.length}개 선택
      </p>
      <div className="design-multi-list">
        {available.map((c) => (
          <label key={stringValue(c.id)} className="design-checkbox">
            <input
              type="checkbox"
              checked={ids.includes(stringValue(c.id))}
              onChange={(e) => choose(stringValue(c.id), e.target.checked)}
            />
            {stringValue(c.name, stringValue(c.alias, stringValue(c.text, stringValue(c.id))))}
          </label>
        ))}
      </div>
      <div className="design-buttons">
        <button
          className="btn btn-small"
          onClick={() =>
            setSelection({ scope, ids: available.filter((c) => !c.locked).map((c) => stringValue(c.id)) })
          }
        >
          모두 선택
        </button>
        <button
          className="btn btn-small"
          onClick={() => setSelection({ scope, ids: [] })}
          disabled={!ids.length}
        >
          선택 해제
        </button>
      </div>
      <div className="design-buttons">
        <button
          className="btn btn-small"
          disabled={!ids.length}
          onClick={() => {
            try {
              setCopied(copyDesignSiblings(blocks, parent, ids));
              setStatus(String(ids.length) + '개를 복사했습니다.');
            } catch (e) {
              setStatus(e instanceof Error ? e.message : '복사하지 못했습니다.');
            }
          }}
        >
          복사
        </button>
        <button className="btn btn-small" disabled={!copied} onClick={() => paste(false)}>
          붙여넣기
        </button>
        <button className="btn btn-small" disabled={!ids.length} onClick={() => paste(true)}>
          복제
        </button>
        <button
          className="btn btn-small"
          disabled={!ids.length}
          onClick={() => mutate((bs) => markDesignSiblings(bs, parent, ids, 'locked', !locked))}
        >
          {locked ? '잠금 해제' : '위치 잠금'}
        </button>
        <button
          className="btn btn-small"
          disabled={grouped ? !ids.length : ids.length < 2}
          onClick={() =>
            mutate((bs) => markDesignSiblings(bs, parent, ids, 'group', grouped ? null : randomBlockId()))
          }
        >
          {grouped ? '묶음 풀기' : '묶기'}
        </button>
      </div>
      <div className="design-buttons">
        <select
          aria-label="정렬 방식"
          value={operation}
          onChange={(e) => {
            const v = options.find(([key]) => key === e.target.value);
            if (v) setOperation(v[0]);
          }}
        >
          {options.map(([op, label]) => (
            <option key={op} value={op}>
              {label}
            </option>
          ))}
        </select>
        <button
          className="btn btn-small"
          disabled={!canAlign}
          onClick={() => mutate((bs) => arrangeDesignSiblings(bs, parent, ids, operation, frames()))}
        >
          정렬 적용
        </button>
      </div>
      {status && <p role="status">{status}</p>}
    </details>
  );
}
