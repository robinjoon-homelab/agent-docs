import { useState } from 'react';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import {
  availablePaintStacks,
  insertPaintPart,
  precisionSelection,
  precisionSiblings,
  type PrecisionEdit,
  type PrecisionGroup,
  type PrecisionPart,
  type PrecisionPath,
} from '../../../../../../domain/flow/precisionDesign';
import { asObject, asObjects, asText } from '../../../../../../domain/flow/designValue';
import { randomBlockId } from '../../../editor/ids';
import { DesignPrecisionFields, type ColorToken } from './DesignPrecisionFields';
import './DesignPrecisionInspector.css';

const names: Readonly<Record<string, string>> = {
  fills: '채움',
  strokes: '획',
  effects: '효과',
  paint: '획의 채움',
  stops: '색상 정지점',
  contours: '윤곽',
  vertices: '꼭짓점',
  segments: '선분',
  regions: '면',
  runs: '글자 구간',
  paragraphs: '문단',
  textFills: '글자 채움',
  solid: '단색',
  linear: '선형 그라데이션',
  radial: '방사형 그라데이션',
  angular: '각도 그라데이션',
  diamond: '마름모 그라데이션',
  dropShadow: '바깥 그림자',
  innerShadow: '안쪽 그림자',
  layerBlur: '레이어 블러',
  backgroundBlur: '배경 블러',
};
function partKey(part: PrecisionPart): string {
  return JSON.stringify([part.collection, part.id]);
}
function selectorLabel(group: PrecisionGroup, member: DesignObject, index: number) {
  const kind = asText(member.kind);
  const name = asText(member.name) ?? asText(member.text) ?? (kind ? names[kind] : undefined);
  return [names[group.collection] ?? group.collection, index + 1, name?.slice(0, 28), asText(member.id)]
    .filter(Boolean)
    .join(' · ');
}

function PartChooser({
  groups,
  step,
  onSelect,
  label,
}: {
  groups: readonly PrecisionGroup[];
  step?: PrecisionPart;
  onSelect: (p?: PrecisionPart) => void;
  label: string;
}) {
  return (
    <label className="design-property">
      <span>{label}</span>
      <select
        value={step ? partKey(step) : ''}
        onChange={(e) => {
          if (!e.target.value) {
            onSelect();
            return;
          }
          for (const group of groups) {
            for (const item of group.members) {
              const id = asText(item.id);
              if (!id) continue;
              const option = { collection: group.collection, id };
              if (partKey(option) === e.target.value) {
                onSelect(option);
                return;
              }
            }
          }
        }}
      >
        <option value="">현재 항목</option>
        {groups.map((g) => (
          <optgroup key={g.collection} label={names[g.collection] ?? g.collection}>
            {g.members.map((item, index) => {
              const id = asText(item.id);
              if (!id) return null;
              return (
                <option key={id} value={partKey({ collection: g.collection, id })}>
                  {selectorLabel(g, item, index)}
                </option>
              );
            })}
          </optgroup>
        ))}
      </select>
    </label>
  );
}

function LayerOrder({
  node,
  parts,
  onChange,
  onRemoved,
}: {
  node: DesignObject;
  parts: PrecisionPath;
  onChange: (e: PrecisionEdit) => void;
  onRemoved: () => void;
}) {
  const part = parts.at(-1);
  if (!part || !['fills', 'strokes', 'effects', 'textFills'].includes(part.collection)) return null;
  const siblings = precisionSiblings(node, parts);
  const i = siblings.findIndex((s) => s.id === part.id);
  if (i < 0) return null;
  function move(dir: number) {
    const id = asText(siblings[i + dir]?.id);
    if (!id) return;
    onChange({ op: 'detailMove', parts, at: dir < 0 ? { before: id } : { after: id } });
  }
  return (
    <div className="design-buttons">
      <button type="button" className="btn btn-small" disabled={i === 0} onClick={() => move(-1)}>
        순서 앞당기기
      </button>
      <button
        type="button"
        className="btn btn-small"
        disabled={i === siblings.length - 1}
        onClick={() => move(1)}
      >
        순서 뒤로
      </button>
      <button
        type="button"
        className="btn btn-small"
        onClick={() => {
          onChange({ op: 'detailRemove', parts });
          onRemoved();
        }}
      >
        이 항목 제거
      </button>
    </div>
  );
}

function ChildSelector({ node, onSelect }: { node: DesignObject; onSelect: (id: string) => void }) {
  const source = asObject(node.source);
  const candidates =
    node.kind === 'boolean' ? asObjects(node.operands) : [source, ...asObjects(node.children)];
  return (
    <div className="design-buttons">
      {candidates.map((c, i) => {
        const id = asText(c.id);
        if (!id) return null;
        const role =
          node.kind === 'boolean'
            ? '피연산자 ' + String(i + 1)
            : i === 0
              ? '마스크 원본'
              : '마스크 내용 ' + String(i);
        return (
          <button type="button" key={id} className="btn btn-small" onClick={() => onSelect(id)}>
            {role} · {asText(c.name) ?? asText(c.alias) ?? id}
          </button>
        );
      })}
    </div>
  );
}

export function DesignPrecisionInspector({
  node,
  sourceNode = node,
  onChange,
  onSelectChild,
  colorTokens = [],
}: {
  node: DesignObject;
  sourceNode?: DesignObject;
  onChange: (change: PrecisionEdit) => void;
  onSelectChild?: (id: string) => void;
  colorTokens?: readonly ColorToken[];
}) {
  const [parts, setParts] = useState<PrecisionPath>([]);
  const selection = precisionSelection(node, parts);
  const [open, setOpen] = useState(false);
  const controls = [];
  if (open) {
    for (let depth = 0; depth <= parts.length; depth++) {
      const parent = parts.slice(0, depth);
      const groups = precisionSelection(node, parent)?.groups;
      if (!groups?.length) break;
      controls.push(
        <PartChooser
          key={JSON.stringify(parent)}
          groups={groups}
          step={parts[depth]}
          label={depth === 0 ? '세부 항목' : '하위 항목 ' + String(depth)}
          onSelect={(p) => setParts(p ? [...parent, p] : parent)}
        />,
      );
    }
  }
  const stacks = availablePaintStacks(node);
  return (
    <details className="design-precision" open={open} onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary>정밀 속성</summary>
      {open && (
        <>
          {controls}
          {selection ? (
            <div key={JSON.stringify(parts)} className="design-precision-inputs">
              <DesignPrecisionFields
                node={node}
                parts={parts}
                colorTokens={colorTokens}
                onChange={onChange}
              />
              <LayerOrder
                node={node}
                parts={parts}
                onChange={onChange}
                onRemoved={() => setParts(parts.slice(0, -1))}
              />
            </div>
          ) : (
            <button type="button" className="btn btn-small" onClick={() => setParts([])}>
              선택한 항목이 없어졌습니다. 레이어로 돌아가기
            </button>
          )}
          {!parts.length && onSelectChild && (node.kind === 'mask' || node.kind === 'boolean') && (
            <ChildSelector node={sourceNode} onSelect={onSelectChild} />
          )}
          {!parts.length && (
            <div className="design-buttons">
              {stacks.map((stack) => (
                <button
                  key={stack}
                  type="button"
                  className="btn btn-small"
                  onClick={() => onChange(insertPaintPart(stack, 'default', randomBlockId()))}
                >
                  {stack === 'effects' ? '그림자' : names[stack]} 추가
                </button>
              ))}
              {stacks.includes('fills') && (
                <button
                  type="button"
                  className="btn btn-small"
                  onClick={() => onChange(insertPaintPart('fills', 'linear', randomBlockId()))}
                >
                  그라데이션 추가
                </button>
              )}
            </div>
          )}
        </>
      )}
    </details>
  );
}
