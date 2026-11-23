import { useState } from 'react';
import type { DesignObject, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import {
  editDesignNode,
  editDesignPrecision,
  findDesignNode,
  object,
  records,
  reorderDesignChild,
  sameAddress,
  setDesignToken,
  sourceBlock,
  stringValue,
} from '../../../../../../domain/flow/editDesign';
import { DesignPrecisionInspector } from './DesignPrecisionInspector';
import { DesignProperty } from './DesignProperty';
import { type DesignRow, lookupRow } from './designLocation';
export type DesignMutation = (
  change: (blocks: readonly FlowDesignBlock[]) => FlowDesignBlock[],
) => string | null;

export function DesignNodeInspector({
  blocks,
  screen,
  state,
  rows,
  row,
  inputs,
  mutate,
  select,
}: {
  blocks: readonly FlowDesignBlock[];
  screen: string;
  state?: string;
  rows: readonly DesignRow[];
  row: DesignRow;
  inputs: ReadonlyMap<string, DesignObject>;
  mutate: DesignMutation;
  select: (a: DesignAddress) => void;
}) {
  const [original, setOriginal] = useState(false);
  const a = row.address,
    owner = sourceBlock(blocks, a.owner),
    source = findDesignNode(blocks, a);
  if (!source) return null;
  const mounted = source.kind === 'instance',
    view = original || mounted ? source : row.occurrence.node;
  const sourceScope = original && owner?.design.kind === 'component',
    scope = { screen, state, original: sourceScope };
  const prop = (name: string) =>
    records(sourceBlock(blocks, stringValue(source.component), 'component')?.design.props).find(
      (p) => p.id === name,
    );
  const values = sourceScope
    ? object(source.inputs)
    : (inputs.get(designAddressKey(a)) ?? object(source.inputs));
  const current = (name: string) => stringValue(values?.[name], stringValue(prop(name)?.initial));
  const write = (path: string, value: string | number) =>
    mutate((bs) => editDesignNode(bs, scope, a, { kind: 'leaf', path, value }));
  const layout = object(view.layout),
    style = object(view.style),
    overflow = style?.overflow;
  const parent = lookupRow(rows, row.parent);
  const siblings = parent ? rows.filter((r) => r.parent && sameAddress(r.parent, parent.address)) : [];
  const index = siblings.indexOf(row);
  const reorder = (offset: number) => {
    const neighbor = siblings[index + offset];
    if (parent && neighbor)
      mutate((bs) => reorderDesignChild(bs, scope, parent.address, a, neighbor.address, offset < 0));
  };
  const writableOrder =
    parent &&
    ['row', 'column', 'grid'].includes(stringValue(object(parent.occurrence.node.layout)?.mode)) &&
    (scope.state || sourceScope || (!a.via?.length && a.owner === screen));
  const allowLeafColor = !mounted && !style?.fills && (typeof style?.fill === 'string' || !style?.fill);
  const layoutValues: { label: string; path: string; value: number; nonnegative: boolean }[] = [];
  const geometry = mounted && !sourceScope ? row.occurrence.node : view;
  for (const axis of ['x', 'y'] as const) {
    const size = object(object(geometry.size)?.[axis]);
    if (size?.mode === 'fixed' && typeof size.px === 'number')
      layoutValues.push({
        label: axis === 'x' ? '너비 (px)' : '높이 (px)',
        path: 'size.' + axis + '.px',
        value: size.px,
        nonnegative: true,
      });
    const position = object(geometry.position);
    if (position?.mode === 'absolute' && geometry.locked !== true)
      for (const edge of ['start', 'end'] as const) {
        const value = object(position[axis])?.[edge];
        if (typeof value === 'number')
          layoutValues.push({
            label:
              (axis === 'x' ? (edge === 'start' ? '왼쪽' : '오른쪽') : edge === 'start' ? '위' : '아래') +
              ' 위치 (px)',
            path: 'position.' + axis + '.' + edge,
            value,
            nonnegative: false,
          });
      }
  }
  const scopeKey = [designAddressKey(a), state, sourceScope ? 'source' : 'use'].join('/');
  return (
    <section className="design-inspector-group">
      <h3>{row.label}</h3>
      <small className="muted">
        {a.owner} / {a.node}
      </small>
      {owner?.design.kind === 'component' && (
        <label className="design-checkbox">
          <input type="checkbox" checked={original} onChange={(e) => setOriginal(e.target.checked)} />
          컴포넌트 원본 편집
        </label>
      )}
      <div key={scopeKey}>
        {mounted &&
          records(sourceBlock(blocks, stringValue(source.component), 'component')?.design.props)
            .filter((p) => p.type === 'text')
            .map((p) => (
              <DesignProperty
                key={stringValue(p.id)}
                label={stringValue(p.name, stringValue(p.id))}
                value={current(stringValue(p.id))}
                onCommit={(value) =>
                  mutate((bs) =>
                    editDesignNode(bs, scope, a, { kind: 'input', prop: stringValue(p.id), value }),
                  )
                }
              />
            ))}
        {!mounted && typeof view.text === 'string' && (
          <DesignProperty label="텍스트" value={view.text} onCommit={(v) => write('text', v)} />
        )}
        {allowLeafColor && (
          <DesignProperty
            label="채움 색"
            value={stringValue(style?.fill)}
            onCommit={(v) =>
              mutate((bs) => {
                if (!/^#(?:[\da-f]{3,4}|[\da-f]{6}|[\da-f]{8})$/i.test(v))
                  throw Error('16진수 색을 입력하세요.');
                return editDesignNode(bs, scope, a, { kind: 'leaf', path: 'style.fill', value: v });
              })
            }
          />
        )}
        {!mounted && ['row', 'column', 'grid'].includes(stringValue(layout?.mode)) && (
          <DesignProperty
            type="number"
            label="자식 간격 (px)"
            value={String(typeof layout?.gap === 'number' ? layout.gap : 0)}
            onCommit={(v) =>
              mutate((bs) => {
                if (!v.trim() || !Number.isFinite(Number(v)) || Number(v) < 0)
                  throw Error('0 이상의 간격을 입력하세요.');
                return editDesignNode(bs, scope, a, { kind: 'leaf', path: 'layout.gap', value: Number(v) });
              })
            }
          />
        )}
        {!mounted &&
          view.kind === 'box' &&
          (['auto', 'scroll'].includes(stringValue(object(overflow)?.y, stringValue(overflow))) ||
            object(view.scroll)?.y !== undefined) && (
            <label className="design-checkbox">
              <input
                type="checkbox"
                checked={object(view.scroll)?.y === 'end'}
                onChange={(e) => write('scroll.y', e.target.checked ? 'end' : 0)}
              />
              세로 스크롤 끝에서 열기
            </label>
          )}
        {!!layoutValues.length && (
          <details>
            <summary>크기와 위치</summary>
            {layoutValues.map((f) => (
              <DesignProperty
                key={f.path}
                type="number"
                label={f.label}
                value={String(f.value)}
                onCommit={(v) =>
                  mutate((bs) => {
                    const n = Number(v);
                    if (!v.trim() || !Number.isFinite(n) || (f.nonnegative && n < 0))
                      throw Error('유효한 값을 입력하세요.');
                    return editDesignNode(bs, scope, a, { kind: 'leaf', path: f.path, value: n });
                  })
                }
              />
            ))}
          </details>
        )}
        {writableOrder && (
          <div className="design-buttons">
            <button type="button" className="btn btn-small" disabled={index <= 0} onClick={() => reorder(-1)}>
              앞으로 이동
            </button>
            <button
              type="button"
              className="btn btn-small"
              disabled={index < 0 || index >= siblings.length - 1}
              onClick={() => reorder(1)}
            >
              뒤로 이동
            </button>
          </div>
        )}
        {!mounted && (
          <DesignPrecisionInspector
            key={scopeKey}
            node={view}
            sourceNode={source}
            colorTokens={blocks
              .filter((b) => b.design.kind === 'tokens')
              .flatMap((b) =>
                records(b.design.entries)
                  .filter((t) => t.type === 'color')
                  .map((t) => ({ id: stringValue(t.id), name: stringValue(t.name) })),
              )}
            onChange={(change) => mutate((bs) => editDesignPrecision(bs, scope, a, change))}
            onSelectChild={(id) => {
              const child = rows.find((r) => r.parent && sameAddress(r.parent, a) && r.address.node === id);
              if (child) select(child.address);
            }}
          />
        )}
      </div>
    </section>
  );
}
export function DesignTokens({
  blocks,
  mutate,
}: {
  blocks: readonly FlowDesignBlock[];
  mutate: DesignMutation;
}) {
  const colors = blocks
    .filter((b) => b.design.kind === 'tokens')
    .flatMap((b) =>
      records(b.design.entries)
        .filter((t) => t.type === 'color' && typeof t.value === 'string')
        .map((t) => ({ owner: stringValue(b.design.id), t })),
    );
  if (!colors.length) return null;
  return (
    <details className="design-inspector-group">
      <summary>문서 공통 색 ({colors.length})</summary>
      {colors.map(({ owner, t }) => (
        <DesignProperty
          key={owner + '/' + stringValue(t.id)}
          label={stringValue(t.name, stringValue(t.id))}
          value={stringValue(t.value)}
          onCommit={(color) => mutate((bs) => setDesignToken(bs, owner, stringValue(t.id), color))}
        />
      ))}
    </details>
  );
}
