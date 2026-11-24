import { useState } from 'react';
import type {
  DesignJson,
  DesignObject,
  FlowDesignBlock,
} from '../../../../../../domain/document/designBlock';
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
import { DesignActions } from './DesignActions';
import { DesignInspectorGeometry } from './DesignInspectorGeometry';
import { FlowAppearanceFields } from '../FlowAppearanceFields';
import { randomBlockId } from '../../../editor/ids';
import { richPlain } from '../../../../../../domain/flow/richText';
import { KINDS } from '../../../../../../domain/flow/flow';
import {
  wireAppearance,
  wireKind,
  editWireAppearance,
  replaceDesignImageAsset,
  patchAnnotation,
} from '../../../../../../domain/flow/editDesignProperties';
import { describeDestination, designKind } from './DesignOverview';

import type { DesignMutation } from './useDesignEditHistory';

export function DesignNodeInspector({
  blocks,
  screen,
  state,
  rows,
  row,
  inputs,
  mutate,
  select,
  annotation,
  number,
  onCreateAnnotation,
  onDelete,
  onDuplicate,
  onLayer,
  uploadImage,
  onFollow,
  frame,
}: {
  blocks: readonly FlowDesignBlock[];
  screen: string;
  state?: string;
  rows: readonly DesignRow[];
  row: DesignRow;
  inputs: ReadonlyMap<string, DesignObject>;
  mutate: DesignMutation;
  select: (a: DesignAddress) => void;
  annotation?: FlowDesignBlock;
  number?: number;
  onCreateAnnotation?: () => void;
  onDuplicate?: () => void;
  onDelete?: () => void;
  onLayer?: (front: boolean) => void;
  uploadImage?: (file: File) => Promise<string>;
  onFollow?: (to: DesignJson | undefined) => void;
  frame?: { width: number; height: number };
}) {
  const [original, setOriginal] = useState(false);
  const [uploadError, setUploadError] = useState(''),
    [uploading, setUploading] = useState(false);
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
  const geometry = mounted && !sourceScope ? row.occurrence.node : view;
  const kind = wireKind(source),
    appearance = kind ? wireAppearance(kind, values ?? {}, stringValue(source.name, row.label)) : undefined;
  const scopeKey = [designAddressKey(a), state, sourceScope ? 'source' : 'use'].join('/');
  const baseName = stringValue(view.name, stringValue(view.alias, row.label));
  const updateAnnotation = (fields: DesignObject) =>
    annotation && mutate((bs) => patchAnnotation(bs, scope, a, annotation.id, fields, randomBlockId));
  const patchFields = (fields: readonly (readonly [string, DesignJson])[]) =>
    mutate((bs) =>
      fields.reduce<FlowDesignBlock[]>(
        (next, [path, value]) => editDesignNode(next, scope, a, { kind: 'leaf', path, value }),
        [...bs],
      ),
    );
  const desc = stringValue(annotation?.design.description);
  const enabled = !!annotation && annotation.design.enabled !== false;
  const gone = records(annotation?.design.actions).some(
    (action) => action.to && !describeDestination(blocks, action.to).valid,
  );
  const px = object(object(geometry.position)?.x)?.start,
    width = object(object(geometry.size)?.x)?.px;
  const root = object(sourceBlock(blocks, screen, 'screen')?.design.root);
  const onCanvas = parent?.address.owner === screen && parent.address.node === root?.id;
  const outside =
    frame && onCanvas && typeof px === 'number' && typeof width === 'number' && px + width > frame.width;
  const warning = enabled && !desc.trim() ? '설명 없음' : gone ? '없는 화면' : outside ? '틀 밖' : '';
  return (
    <section className="flow-inspector design-inspector-group">
      <div className="flow-detail-title">
        {enabled && number != null && number > 0 && <span className="flow-number">{number}</span>}
        <h2>요소 속성</h2>
        <span className="flow-kind-chip">
          {kind ? KINDS[kind] : designKind(row, stringValue(source.component))}
        </span>
        {warning && (
          <span className="flow-kind-chip flow-warn" role="status">
            {warning}
          </span>
        )}
      </div>
      {appearance && frame && (
        <FlowAppearanceFields
          element={appearance}
          onPatch={(patch) => {
            mutate((bs) => editWireAppearance(bs, scope, a, appearance, patch, frame, randomBlockId));
          }}
          onChangeKind={
            sourceScope || (!a.via?.length && a.owner === (state ?? screen))
              ? (k) => {
                  mutate((bs) =>
                    editWireAppearance(bs, scope, a, appearance, { kind: k }, frame, randomBlockId),
                  );
                }
              : undefined
          }
          onUploadImage={uploadImage ? async (file) => 'asset:' + (await uploadImage(file)) : undefined}
        />
      )}
      {!appearance && (
        <DesignProperty
          label="요소 이름"
          value={baseName}
          onCommit={(v) =>
            mutate((bs) => {
              const next = editDesignNode(bs, scope, a, { kind: 'leaf', path: 'name', value: v });
              return annotation && !sourceScope && v.trim()
                ? patchAnnotation(next, scope, a, annotation.id, { name: v }, randomBlockId)
                : next;
            })
          }
        />
      )}
      {owner?.design.kind === 'component' && (
        <label className="design-checkbox">
          <input type="checkbox" checked={original} onChange={(e) => setOriginal(e.target.checked)} />
          컴포넌트 원본 편집
        </label>
      )}
      <div key={scopeKey}>
        {mounted &&
          !appearance &&
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
        {!mounted && view.kind === 'image' && (
          <>
            <DesignProperty
              label="대체 텍스트"
              value={stringValue(view.alt)}
              onCommit={(v) => write('alt', v)}
            />
            {uploadImage && (
              <label className="flow-upload-button">
                {uploading ? '올리는 중…' : '이미지 올리기'}
                <input
                  hidden
                  type="file"
                  accept="image/*"
                  disabled={uploading}
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    e.target.value = '';
                    if (!file) return;
                    const previous = view.asset;
                    setUploading(true);
                    setUploadError('');
                    void uploadImage(file)
                      .then((asset) => {
                        const error = mutate((bs) => replaceDesignImageAsset(bs, scope, a, previous, asset));
                        if (error) setUploadError(error);
                      })
                      .catch((error: unknown) =>
                        setUploadError(
                          error instanceof Error ? error.message : '이미지를 올리지 못했습니다.',
                        ),
                      )
                      .finally(() => setUploading(false));
                  }}
                />
              </label>
            )}
            {uploadError && <p role="alert">{uploadError}</p>}
          </>
        )}
        <DesignInspectorGeometry
          geometry={geometry}
          frame={frame}
          write={patchFields}
          onLayer={onLayer}
          rectActions={!!kind && onCanvas}
        />
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
        <label className="flow-annotate">
          <input
            type="checkbox"
            checked={enabled}
            onChange={(e) =>
              annotation ? updateAnnotation({ enabled: e.target.checked }) : onCreateAnnotation?.()
            }
          />
          <span>
            <strong>번호와 설명 붙이기</strong>
            <small>보기 화면에서 번호와 (i) 설명을 표시합니다.</small>
          </span>
        </label>
        {enabled && (
          <div className="flow-description-editor">
            <label>
              설명 (보기 모드의 (i) 내용)
              <textarea
                rows={5}
                value={desc}
                placeholder="요소 동작과 조건을 적으세요."
                onChange={(e) => updateAnnotation({ description: e.target.value })}
              />
            </label>
            {desc.trim() ? (
              <div className="flow-description-preview">
                <strong>목록 미리보기</strong>
                {Array.from(richPlain(desc)).slice(0, 60).join('')}
                {Array.from(richPlain(desc)).length > 60 ? '…' : ''}
              </div>
            ) : (
              <p className="flow-empty-description">설명이 비어 있습니다.</p>
            )}
          </div>
        )}
        {annotation && (
          <DesignActions
            blocks={blocks}
            annotation={annotation}
            mutate={mutate}
            scope={scope}
            target={a}
            onFollow={onFollow}
          />
        )}
        {(onDuplicate || onDelete) && (
          <div className="flow-inspector-buttons flow-element-footer">
            {onDuplicate && (
              <button type="button" onClick={onDuplicate}>
                복제
              </button>
            )}
            {onDelete && (
              <button type="button" className="flow-danger-outline" onClick={onDelete}>
                요소 삭제
              </button>
            )}
          </div>
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
