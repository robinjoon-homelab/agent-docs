import { useState, useSyncExternalStore, useRef, useMemo, useEffect, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router';
import type { DocumentEditorSession } from '../../../../../../application/document/editorSession';
import { useServices } from '../../../../../../config/ServicesContext';
import type { Anchor } from '../../../../../../domain/thread/thread';
import type { DesignJson, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { equalDesignValues } from '../../../../../../domain/document/designBlock';
import { type DesignAddress, designAddressKey } from '../../../../../../domain/flow/designAddress';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import {
  object,
  readAddress,
  records,
  sameAddress,
  setDesignInfo,
  stringValue,
} from '../../../../../../domain/flow/editDesign';
import { restoreDesignEdit } from '../../../../../../domain/flow/editDesignUndo';
import { setDesignFrame } from '../../../../../../domain/flow/editDesignStructure';
import { CopyButton } from '../../../common/CopyButton';
import { FlowRichText } from '../FlowRichText';
import { DesignCanvas } from './DesignCanvas';
import { DesignCreateDialog, type CreateKind } from './DesignCreateDialog';
import { DesignNodeInspector, DesignTokens, type DesignMutation } from './DesignNodeInspector';
import { useDesignDrag } from './useDesignDrag';
import { DesignTree } from './DesignTree';
import { DesignArrange } from './DesignArrange';
import { DesignActions } from './DesignActions';
import { DesignProperty } from './DesignProperty';
import {
  designQuery,
  designRows,
  lookupRow,
  resolveDesignLocation,
  type DesignLocation,
} from './designLocation';
import './designDocument.css';

interface Props {
  session: DocumentEditorSession;
  editable: boolean;
  onInput: () => void;
  modeTarget?: HTMLElement | null;
  onEditMode?: () => void;
  commentRail?: (scope?: string) => ReactNode;
  onRequestComment?: (anchor: Anchor) => void;
  pendingComment?: Anchor | null;
}
type EditRecord = { before: readonly FlowDesignBlock[]; after: readonly FlowDesignBlock[] };
const sameBlocks = (a: readonly FlowDesignBlock[], b: readonly FlowDesignBlock[]) =>
  a.length === b.length && a.every((x, i) => x.id === b[i]?.id && equalDesignValues(x.design, b[i].design));
function historyOf(value: unknown): { previous: string[]; following: string[] } {
  const blank = { previous: [], following: [] };
  if (!value || typeof value !== 'object' || !('designNav' in value)) return blank;
  const v = value.designNav;
  if (
    !v ||
    typeof v !== 'object' ||
    !('previous' in v) ||
    !('following' in v) ||
    !Array.isArray(v.previous) ||
    !Array.isArray(v.following)
  )
    return blank;
  return {
    previous: v.previous.filter((x: unknown): x is string => typeof x === 'string'),
    following: v.following.filter((x: unknown): x is string => typeof x === 'string'),
  };
}

export function DesignFlowDocument({
  session,
  editable,
  onInput,
  modeTarget,
  onEditMode,
  commentRail,
  onRequestComment,
  pendingComment,
}: Props) {
  const { assets } = useServices();
  const s = useSyncExternalStore(session.subscribe, session.getState),
    location = useLocation(),
    navigate = useNavigate();
  const rawBlocks = session.currentBlocks();
  const blocks = useMemo(
    () => rawBlocks.filter((b): b is FlowDesignBlock => b.type === 'flowDesign'),
    [rawBlocks],
  );
  const { screen, state, states, screens, address, invalidState, invalidNode } = resolveDesignLocation(
    blocks,
    location.search,
  );
  const initialEdit = (location.state as { flowEdit?: boolean } | null)?.flowEdit === true;
  useEffect(() => {
    if (initialEdit)
      void navigate(
        { pathname: location.pathname, search: location.search, hash: location.hash },
        { replace: true, state: null },
      );
  }, [initialEdit, navigate, location.pathname, location.search, location.hash]);
  const [editing, setEditing] = useState(initialEdit && editable),
    [dialog, setDialog] = useState<CreateKind | null>(null),
    [message, setMessage] = useState('');
  const [historySizes, setHistorySizes] = useState({ undo: 0, redo: 0 });
  const journal = useRef<{ undo: EditRecord[]; redo: EditRecord[] }>({ undo: [], redo: [] });
  const edit = editable && editing,
    canWrite = edit && session.isWritable() && s.status !== 'conflict';
  const navHistory = historyOf(location.state);
  const viewport = new URLSearchParams(location.search).get('flowView') === 'full';
  const sc = stringValue(screen?.design.id),
    st = typeof state?.design.id === 'string' ? state.design.id : undefined;
  const scene = resolveDesignScene(blocks, sc, st),
    allRows = designRows(scene.root, blocks),
    rows = edit ? allRows : allRows.filter((r) => r.visible),
    row = lookupRow(rows, address);
  const [requestedTab, setTab] = useState<'details' | 'comments'>('details'),
    [allComments, setAllComments] = useState(false);
  const tab = pendingComment ? 'comments' : requestedTab;
  function go(focus: DesignLocation) {
    const search = designQuery(location.search, focus);
    if (search === location.search) return;
    void navigate(
      { pathname: location.pathname, search },
      { state: { designNav: { previous: [...navHistory.previous, location.search], following: [] } } },
    );
  }
  function travel(back: boolean) {
    const { previous, following } = navHistory;
    const target = back ? previous.at(-1) : following.at(-1);
    if (target === undefined) return;
    const nav = back
      ? { previous: previous.slice(0, -1), following: [...following, location.search] }
      : { previous: [...previous, location.search], following: following.slice(0, -1) };
    void navigate(
      { pathname: location.pathname, search: target },
      { replace: true, state: { designNav: nav } },
    );
  }
  function write(next: readonly FlowDesignBlock[]) {
    onInput();
    session.markEdited(next);
    void session.saveContent(next);
    setHistorySizes({ undo: journal.current.undo.length, redo: journal.current.redo.length });
    setMessage('');
  }
  const mutate: DesignMutation = (change) => {
    if (!canWrite || !session.isWritable()) {
      const reason = '현재 편집할 수 없습니다.';
      setMessage(reason);
      return reason;
    }
    try {
      const current = session.currentBlocks();
      const model = current.filter((b): b is FlowDesignBlock => b.type === 'flowDesign');
      if (model.length !== current.length) throw Error('문서 형식이 바뀌었습니다. 다시 열어 주세요.');
      const next = change(model);
      if (sameBlocks(model, next)) return null;
      journal.current.undo = [...journal.current.undo.slice(-59), { before: model, after: next }];
      journal.current.redo = [];
      write(next);
      return null;
    } catch (error) {
      const msg = error instanceof Error ? error.message : '변경을 적용하지 못했습니다.';
      setMessage(msg);
      return msg;
    }
  };
  const undo = (back: boolean) => {
    if (!canWrite || !session.isWritable()) return;
    const from = back ? journal.current.undo : journal.current.redo,
      to = back ? journal.current.redo : journal.current.undo,
      entry = from.at(-1);
    if (!entry) return;
    const current = session.currentBlocks().filter((b): b is FlowDesignBlock => b.type === 'flowDesign');
    try {
      const next = restoreDesignEdit(
        back ? entry.after : entry.before,
        back ? entry.before : entry.after,
        current,
      );
      from.pop();
      to.push(entry);
      write(next);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '실행 취소를 적용하지 못했습니다.');
    }
  };
  const select = (a: DesignAddress) => {
    if (screen) go({ screen: screen.id, state: state?.id, address: a });
  };
  const dragEvents = useDesignDrag(canWrite, rows, { screen: sc, state: st }, mutate, select);
  const annotations = blocks
    .filter(
      (b) =>
        b.design.kind === 'annotation' &&
        b.design.screen === sc &&
        (!b.design.state || b.design.state === st) &&
        b.design.enabled !== false,
    )
    .filter((b) => lookupRow(rows, readAddress(b.design.target))?.visible === true);
  const visibleAnnotations = annotations.filter(
    (b) =>
      b.design.state ||
      !annotations.some(
        (o) => o.design.state && equalDesignValues(b.design.target ?? null, o.design.target ?? null),
      ),
  );
  const annotation = address
    ? visibleAnnotations.find((b) => {
        const t = readAddress(b.design.target);
        return t && sameAddress(t, address);
      })
    : undefined;
  const anchorBlock = annotation ?? state ?? screen;
  function commentOnSelection() {
    if (!onRequestComment || !anchorBlock) return;
    setTab('comments');
    onRequestComment({
      kind: 'block',
      blockId: anchorBlock.id,
      ...(annotation && !annotation.design.state && state ? { viewedStateId: state.id } : {}),
    });
  }
  function follow(to: DesignJson | undefined): 'missing' | 'same' | 'moved' {
    const target = object(to);
    const dest = screens.find((b) => b.design.id === target?.screen);
    if (!dest) return 'missing';
    const destState =
      target?.state === undefined
        ? undefined
        : blocks.find(
            (b) =>
              b.design.kind === 'state' && b.design.id === target.state && b.design.screen === dest.design.id,
          );
    if (target?.state !== undefined && !destState) return 'missing';
    if (dest.id === screen?.id && destState?.id === state?.id) return 'same';
    go({ screen: dest.id, state: destState?.id });
    return 'moved';
  }
  const activate = (a: DesignAddress) => {
    const ann = visibleAnnotations.find((b) => {
      const t = readAddress(b.design.target);
      return t && sameAddress(t, a);
    });
    const action = records(ann?.design.actions)[0];
    if (action && follow(action.to) === 'moved') return;
    select(a);
    setTab('details');
  };
  const rootAddr = scene.root?.address;
  const insertParent =
    row &&
    (row.occurrence.node.kind === 'box' || row.occurrence.node.kind === 'group') &&
    sameAddress(row.address, row.occurrence.address)
      ? row.address
      : rootAddr;
  const controls = (
    <div className="flow-segmented" aria-label="디자인 문서 모드">
      <button
        type="button"
        aria-pressed={!edit}
        onClick={() => {
          setEditing(false);
          setDialog(null);
        }}
      >
        보기
      </button>
      <button
        type="button"
        aria-pressed={edit}
        onClick={() => {
          setEditing(true);
          onEditMode?.();
        }}
      >
        편집
      </button>
    </div>
  );
  const href = (focus: DesignLocation) =>
    new URL(location.pathname + designQuery('', focus), window.location.origin).href;
  const validScope = !!screen && !invalidState;
  const baseLink = screen ? { screen: screen.id, state: state?.id } : null;
  const [scale, setScale] = useState(100);
  const canvas =
    validScope &&
    (screen.design.frame === 'note' ? (
      <article className="design-note">
        <h2>{stringValue(screen.design.name)}</h2>
        <FlowRichText
          assetUrl={(id) => assets.contentUrl(id)}
          text={stringValue(screen.design.description)}
        />
      </article>
    ) : (
      <DesignCanvas
        blocks={blocks}
        scene={scene}
        screen={sc}
        state={st}
        selection={row?.address}
        scale={scale / 100}
        mode={viewport ? 'full' : 'viewport'}
        showDiagnostics={!viewport}
        onSelect={edit ? select : undefined}
        onActivate={edit ? undefined : activate}
      />
    ));
  if (viewport)
    return (
      <section className="design-full">
        {validScope ? canvas : <p role="alert">이 화면 또는 상태를 찾을 수 없습니다.</p>}
      </section>
    );
  const nameOf = (b: FlowDesignBlock) => stringValue(b.design.name, stringValue(b.design.id));
  const create = (kind: CreateKind) => {
    setMessage('');
    setDialog(kind);
  };
  const scopeName = state ? nameOf(state) : '기본 상태';
  return (
    <section className="design-document" aria-label="화면 디자인" data-save-state={s.status}>
      {editable && (modeTarget ? createPortal(controls, modeTarget) : controls)}
      <div className="design-toolbar">
        <nav aria-label="디자인 이동">
          <button
            className="btn btn-small"
            disabled={!navHistory.previous.length}
            onClick={() => travel(true)}
          >
            뒤로
          </button>
          <button
            className="btn btn-small"
            disabled={!navHistory.following.length}
            onClick={() => travel(false)}
          >
            앞으로
          </button>
        </nav>
        <label>
          화면{' '}
          <select value={screen?.id ?? ''} onChange={(e) => go({ screen: e.target.value })}>
            {!screen && <option value="">화면 선택</option>}
            {screens.map((b) => (
              <option value={b.id} key={b.id}>
                {stringValue(b.design.code)} · {nameOf(b)}
              </option>
            ))}
          </select>
        </label>
        {screen?.design.frame !== 'note' && screen && (
          <label>
            상태{' '}
            <select
              value={state?.id ?? ''}
              onChange={(e) => go({ screen: screen.id, state: e.target.value || undefined })}
            >
              <option value="">기본 상태</option>
              {states.map((b) => (
                <option key={b.id} value={b.id}>
                  {nameOf(b)}
                </option>
              ))}
            </select>
          </label>
        )}
        {screen && <CopyButton text={href({ screen: screen.id })} label="화면 링크 복사" />}
        {baseLink && state && <CopyButton text={href(baseLink)} label="상태 링크 복사" />}
        {baseLink && row && (
          <CopyButton text={href({ ...baseLink, address: row.address })} label="요소 링크 복사" />
        )}
        {canWrite && (
          <>
            <button className="btn btn-small" onClick={() => create('screen')}>
              화면 추가
            </button>
            {validScope && screen.design.frame !== 'note' && (
              <button className="btn btn-small" onClick={() => create('state')}>
                상태 추가
              </button>
            )}
            <button className="btn btn-small" onClick={() => create('import')}>
              마크업 가져오기
            </button>
          </>
        )}
      </div>
      {message && (
        <p role="alert" className="design-warning">
          {message}
        </p>
      )}
      {!validScope ? (
        <p role="alert" className="design-warning">
          링크의 화면 또는 상태를 찾을 수 없습니다.
        </p>
      ) : (
        <>
          {(invalidNode || (address && !row)) && (
            <p role="status" className="design-warning">
              선택한 요소가 이 상태에 없거나 링크가 올바르지 않습니다.
            </p>
          )}
          <div className="design-layout">
            <aside className="design-tree">
              <DesignTree
                rows={rows}
                selected={row?.address}
                editable={canWrite}
                scope={{ screen: sc, state: st }}
                mutate={mutate}
                select={(a) => {
                  select(a);
                  setTab('details');
                }}
              />
              {canWrite && (
                <div className="design-buttons">
                  <button className="btn btn-small" onClick={() => create('component')}>
                    컴포넌트 만들기
                  </button>
                  {insertParent && (
                    <>
                      <button className="btn btn-small" onClick={() => create('box')}>
                        상자 추가
                      </button>
                      <button className="btn btn-small" onClick={() => create('text')}>
                        텍스트 추가
                      </button>
                      {blocks.some((b) => b.design.kind === 'component') && (
                        <button className="btn btn-small" onClick={() => create('instance')}>
                          컴포넌트 배치
                        </button>
                      )}
                    </>
                  )}
                </div>
              )}
            </aside>
            <div className="design-stage" {...dragEvents}>
              <div className="design-stage-tools">
                <label>
                  배율{' '}
                  <select value={scale} onChange={(e) => setScale(Number(e.target.value))}>
                    {[25, 50, 75, 100, 150, 200].map((v) => (
                      <option key={v} value={v}>
                        {v}%
                      </option>
                    ))}
                  </select>
                </label>
                {baseLink && (
                  <a
                    href={href({ ...baseLink, address: row?.address }) + '&flowView=full'}
                    target="_blank"
                    rel="noreferrer"
                  >
                    단독 보기
                  </a>
                )}
              </div>
              {canvas}
            </div>
            <aside className="design-properties">
              <div className="design-buttons">
                <button
                  className="btn btn-small"
                  aria-pressed={tab === 'details'}
                  onClick={() => setTab('details')}
                >
                  {edit ? '속성' : '설명'}
                </button>
                {commentRail && (
                  <button
                    className="btn btn-small"
                    aria-pressed={tab === 'comments'}
                    onClick={() => setTab('comments')}
                  >
                    댓글
                  </button>
                )}
              </div>
              {tab === 'comments' && anchorBlock ? (
                <>
                  <label className="design-checkbox">
                    <input
                      type="checkbox"
                      checked={allComments || pendingComment?.kind === 'document'}
                      onChange={(e) => setAllComments(e.target.checked)}
                    />
                    문서의 모든 댓글
                  </label>
                  {commentRail?.(
                    allComments || pendingComment?.kind === 'document' ? undefined : anchorBlock.id,
                  )}
                  {onRequestComment && (
                    <button className="btn btn-small" onClick={commentOnSelection}>
                      댓글 달기
                    </button>
                  )}
                </>
              ) : (
                <>
                  <h3>{nameOf(screen)}</h3>
                  <span className="muted">{scopeName}</span>
                  {canWrite && (
                    <>
                      <div className="design-buttons">
                        <button
                          className="btn btn-small"
                          disabled={!historySizes.undo}
                          onClick={() => undo(true)}
                        >
                          실행 취소
                        </button>
                        <button
                          className="btn btn-small"
                          disabled={!historySizes.redo}
                          onClick={() => undo(false)}
                        >
                          다시 실행
                        </button>
                      </div>
                      <DesignProperty
                        key={(state ?? screen).id + 'name'}
                        label={state ? '상태 이름' : '화면 이름'}
                        value={nameOf(state ?? screen)}
                        onCommit={(v) => mutate((bs) => setDesignInfo(bs, st ?? sc, 'name', v))}
                      />
                      {screen.design.frame !== 'note' && (
                        <div className="design-frame-props">
                          {(['width', 'height'] as const).map((k) => (
                            <DesignProperty
                              key={(state ?? screen).id + k}
                              label={k === 'width' ? '화면 너비 (px)' : '화면 높이 (px)'}
                              value={String(scene.frame[k])}
                              type="number"
                              onCommit={(v) =>
                                mutate((bs) =>
                                  setDesignFrame(
                                    bs,
                                    sc,
                                    st,
                                    k === 'width' ? Number(v) : scene.frame.width,
                                    k === 'height' ? Number(v) : scene.frame.height,
                                    k,
                                  ),
                                )
                              }
                            />
                          ))}
                        </div>
                      )}
                      <DesignProperty
                        key={(state ?? screen).id + 'desc'}
                        label="설명"
                        type="long"
                        value={stringValue((state ?? screen).design.description)}
                        onCommit={(v) => mutate((bs) => setDesignInfo(bs, st ?? sc, 'description', v))}
                      />
                      {row && (
                        <DesignNodeInspector
                          key={designAddressKey(row.address) + '/' + (st ?? '')}
                          blocks={blocks}
                          row={row}
                          screen={sc}
                          state={st}
                          rows={rows}
                          inputs={scene.inputs}
                          mutate={mutate}
                          select={select}
                        />
                      )}
                      {!state && (
                        <DesignArrange
                          blocks={blocks}
                          parent={
                            insertParent?.owner === sc && !insertParent.via?.length ? insertParent : rootAddr
                          }
                          mutate={mutate}
                          onSelect={select}
                        />
                      )}
                      <DesignTokens blocks={blocks} mutate={mutate} />
                    </>
                  )}
                  {!edit && (
                    <FlowRichText
                      assetUrl={(id) => assets.contentUrl(id)}
                      text={stringValue((state ?? screen).design.description)}
                    />
                  )}
                  {annotation ? (
                    <section className="design-inspector-group">
                      <h3>{nameOf(annotation)}</h3>
                      {canWrite ? (
                        <DesignProperty
                          key={annotation.id}
                          type="long"
                          label="요소 설명"
                          value={stringValue(annotation.design.description)}
                          onCommit={(v) =>
                            mutate((bs) =>
                              setDesignInfo(bs, stringValue(annotation.design.id), 'description', v),
                            )
                          }
                        />
                      ) : (
                        <FlowRichText
                          assetUrl={(id) => assets.contentUrl(id)}
                          text={stringValue(annotation.design.description)}
                        />
                      )}
                      {canWrite && (
                        <DesignActions
                          key={annotation.id}
                          blocks={blocks}
                          annotation={annotation}
                          mutate={mutate}
                        />
                      )}
                      {records(annotation.design.actions).map((action, i) => (
                        <button
                          type="button"
                          className="btn btn-small"
                          key={i}
                          onClick={() => {
                            if (follow(action.to) === 'missing')
                              setMessage('연결된 화면이나 상태를 찾을 수 없습니다.');
                          }}
                        >
                          {stringValue(action.when, '이동')}
                        </button>
                      ))}
                    </section>
                  ) : (
                    canWrite &&
                    row && (
                      <button className="btn btn-small" onClick={() => create('annotation')}>
                        요소에 설명 붙이기
                      </button>
                    )
                  )}
                  {visibleAnnotations.length > 0 && (
                    <section className="design-inspector-group">
                      <h3>주요 요소</h3>
                      <ol>
                        {visibleAnnotations.map((b) => (
                          <li key={b.id}>
                            <button
                              className="design-text-link"
                              onClick={() => {
                                const a = readAddress(b.design.target);
                                if (a) select(a);
                              }}
                            >
                              {nameOf(b)}
                            </button>
                          </li>
                        ))}
                      </ol>
                    </section>
                  )}
                  {onRequestComment && anchorBlock && (
                    <button className="btn btn-small" onClick={commentOnSelection}>
                      {annotation ? '요소' : state ? '상태' : '화면'}에 댓글 달기
                    </button>
                  )}
                </>
              )}
              {scene.warnings.length > 0 && (
                <details>
                  <summary>진단 {scene.warnings.length}건</summary>
                  <ul>
                    {scene.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                </details>
              )}
            </aside>
          </div>
        </>
      )}
      {canWrite && dialog && screen && (
        <DesignCreateDialog
          key={dialog}
          kind={dialog}
          close={() => setDialog(null)}
          blocks={blocks}
          mutate={mutate}
          go={go}
          screen={screen}
          state={state}
          parent={insertParent}
          selected={row?.address}
        />
      )}
    </section>
  );
}
