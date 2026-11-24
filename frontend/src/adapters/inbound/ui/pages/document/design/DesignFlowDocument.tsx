import {
  useState,
  useSyncExternalStore,
  useRef,
  useMemo,
  useEffect,
  useImperativeHandle,
  useCallback,
  type Ref,
  type ReactNode,
} from 'react';
import { createPortal } from 'react-dom';
import { useLocation, useNavigate } from 'react-router';
import type { DocumentEditorSession } from '../../../../../../application/document/editorSession';
import { useServices } from '../../../../../../config/ServicesContext';
import type { Anchor, Thread } from '../../../../../../domain/thread/thread';
import type { DesignJson, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { useDesignEditHistory } from './useDesignEditHistory';
import { type DesignAddress, designAddressKey } from '../../../../../../domain/flow/designAddress';
import { useDesignScreenScene } from './useDesignScreenScene';
import {
  findDesignNode,
  object,
  records,
  sameAddress,
  stringValue,
} from '../../../../../../domain/flow/editDesign';
import { CopyButton } from '../../../common/CopyButton';
import { FlowRichText } from '../FlowRichText';
import { DesignCanvas } from './DesignCanvas';
import { DesignCreateDialog, type CreateKind } from './DesignCreateDialog';
import { DesignNodeInspector, DesignTokens } from './DesignNodeInspector';
import { DesignTree } from './DesignTree';
import { designQuery, lookupRow, resolveDesignLocation, type DesignLocation } from './designLocation';
import { DesignViewStage } from './DesignViewStage';
import {
  DesignScreenProperties,
  DesignNoteEditor,
  DesignFramePicker,
  addNamedDesignScreen,
} from './DesignScreenProperties';
import { DesignElementPalette } from './DesignElementPalette';
import {
  DesignEditCanvas,
  useDesignCanvasSelection,
  type DesignCanvasCommands,
  type DesignSelectionMeasurement,
} from './DesignEditCanvas';
import { DesignMultiSelection } from './DesignMultiSelection';
import { connectedAnnotations } from '../../../../../../domain/flow/editCanvasChildren';
import { flowAnchorLocation } from '../../../../../../domain/flow/flowAnchorLocation';
import { uploadFlowImage } from '../uploadFlowImage';
import {
  DesignOverview,
  DesignElementDetails,
  describeDestination,
  designKind,
  type DesignHotspot,
} from './DesignOverview';
import { DesignDeleteConfirmation } from './DesignDeleteConfirmation';
import { DesignSidePanel } from './DesignSidePanel';
import { FlowScreenStrip } from '../FlowScreenStrip';
import { FlowIcon, FlowCommentBadge } from '../FlowIcon';
import { useFlowCommentTargets } from '../useFlowCommentTargets';
import type { FlowHandle } from '../FlowDocument';
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
  ref?: Ref<FlowHandle>;
  threads?: readonly Thread[];
  statsTarget?: HTMLElement | null;
  spaceSlug?: string;
  onStartComment?: (anchor: Anchor, body: string) => Promise<boolean>;
  mentionLogins?: readonly string[];
  reanchoring?: boolean;
  onReanchor?: (anchor: Anchor) => void;
}
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

const emptyThreads: readonly Thread[] = [];
export function DesignFlowDocument({
  session,
  editable,
  onInput,
  modeTarget,
  onEditMode,
  commentRail,
  onRequestComment,
  pendingComment,
  ref,
  threads = emptyThreads,
  statsTarget,
  spaceSlug,
  onStartComment,
  mentionLogins = [],
  reanchoring = false,
  onReanchor,
}: Props) {
  const { assets } = useServices();
  const s = useSyncExternalStore(session.subscribe, session.getState),
    location = useLocation(),
    navigate = useNavigate();
  const rawBlocks = useSyncExternalStore(session.subscribe, () => session.currentBlocks());
  const writable = useSyncExternalStore(session.subscribe, () => session.isWritable());
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
  const edit = editable && editing,
    canWrite = edit && writable && s.status !== 'conflict';
  const navHistory = historyOf(location.state);
  const viewport = new URLSearchParams(location.search).get('flowView') === 'full';
  const sc = stringValue(screen?.design.id),
    st = typeof state?.design.id === 'string' ? state.design.id : undefined;
  const { scene, allRows, scopedAnnotations } = useDesignScreenScene(blocks, sc, st);
  const rows = edit ? allRows : allRows.filter((r) => r.visible),
    row = lookupRow(rows, address);
  const [requestedTab, setTab] = useState<'details' | 'comments'>('details');
  const allowComment = !!onStartComment || !!onRequestComment;
  const uploadImage =
    spaceSlug && canWrite ? (file: File) => uploadFlowImage(assets, spaceSlug, file) : undefined;
  const [confirmation, setConfirmation] = useState<{
    scope: string;
    targets: readonly DesignAddress[];
  } | null>(null);
  function go(focus: DesignLocation) {
    const search = designQuery(location.search, focus);
    if (search === location.search) return;
    setConfirmation(null);
    void navigate(
      { pathname: location.pathname, search },
      screen?.id === focus.screen && state?.id === focus.state
        ? { replace: true, state: { designNav: navHistory }, preventScrollReset: true, flushSync: true }
        : {
            state: {
              designNav: { previous: [...navHistory.previous.slice(-31), location.search], following: [] },
            },
            preventScrollReset: true,
          },
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
  const {
    mutate,
    undo: travelEdits,
    historySizes,
  } = useDesignEditHistory(session, canWrite, onInput, setMessage);
  function undo(back: boolean) {
    const next = travelEdits(back);
    if (!next) return;
    const active = next.find((b) => b.id === screen?.id);
    if (!active) {
      const fallback = next.find((b) => b.design.kind === 'screen');
      if (fallback) go({ screen: fallback.id });
    } else if (state && !next.some((b) => b.id === state.id)) {
      go({ screen: active.id });
    }
  }
  const select = (a: DesignAddress) => {
    if (screen) go({ screen: screen.id, state: state?.id, address: a });
  };
  const selection = useDesignCanvasSelection(sc, st, address, (next) => {
    if (screen) go({ screen: screen.id, state: state?.id, address: next });
  });
  const [measurement, setMeasurement] = useState<DesignSelectionMeasurement>();
  const measureSelection = useCallback((next: DesignSelectionMeasurement | undefined) => {
    setMeasurement((old) =>
      old?.key === next?.key &&
      old?.resizable === next?.resizable &&
      old?.rect.x === next?.rect.x &&
      old?.rect.y === next?.rect.y &&
      old?.rect.w === next?.rect.w &&
      old?.rect.h === next?.rect.h &&
      old?.rect.parent.width === next?.rect.parent.width &&
      old?.rect.parent.height === next?.rect.parent.height &&
      old?.rect.contentInset?.x === next?.rect.contentInset?.x &&
      old?.rect.contentInset?.y === next?.rect.contentInset?.y
        ? old
        : next,
    );
  }, []);
  const canvasCommands = useRef<DesignCanvasCommands>(null);
  const [collapsed, setCollapsed] = useState(false);
  const [highlighted, setHighlighted] = useState<DesignAddress | null>(null);
  const [layersOpen, setLayersOpen] = useState(false);
  const [composerFocus, setComposerFocus] = useState(0);
  const [diagnosticsTarget, setDiagnosticsTarget] = useState<HTMLDivElement | null>(null);
  const targets = useFlowCommentTargets(threads, blocks, s.status === 'saved' ? s.version : undefined);
  const currentScope = JSON.stringify([sc, st, address && designAddressKey(address)]);
  const multi = edit && selection.addresses.length > 1;
  const threadCounts = new Map<string, number>(),
    screenCounts = new Map<string, number>();
  let openCount = 0;
  for (const t of threads) {
    if (t.status === 'resolved' || targets.get(t.id) === 'screenDeleted') continue;
    openCount++;
    if (t.anchor.kind === 'document') continue;
    const id = t.anchor.blockId;
    const owner = targets.parents.get(t.id) ?? flowAnchorLocation(blocks, id)?.screenBlockId;
    if (owner) screenCounts.set(owner, (screenCounts.get(owner) ?? 0) + 1);
    threadCounts.set(id, (threadCounts.get(id) ?? 0) + 1);
  }
  const hotspots: DesignHotspot[] = [];
  for (const r of allRows) {
    if (!r.visible) continue;
    const b = scopedAnnotations.get(designAddressKey(r.address));
    if (!b || b.design.enabled === false) continue;
    const source = findDesignNode(blocks, r.address);
    hotspots.push({
      block: b,
      row: r,
      target: r.address,
      name: stringValue(b.design.name, r.label),
      kind: designKind(r, stringValue(source?.component)),
      comments: threadCounts.get(b.id) ?? 0,
    });
  }
  const markers = hotspots.map((h) => ({
    target: h.target,
    name: h.name,
    comments: h.comments,
    destination: describeDestination(blocks, records(h.block.design.actions)[0]?.to).short,
    warning: edit
      ? !stringValue(h.block.design.description).trim()
        ? '설명 없음'
        : records(h.block.design.actions).some((a) => a.to && !describeDestination(blocks, a.to).valid)
          ? '없는 화면'
          : undefined
      : undefined,
  }));
  const annotation = address ? scopedAnnotations.get(designAddressKey(address)) : undefined;
  const item = address && hotspots.find((h) => sameAddress(h.target, address));
  const number = item ? hotspots.indexOf(item) + 1 : 0;
  const anchorBlock =
    (!multi && annotation?.design.enabled !== false ? annotation : undefined) ?? state ?? screen;
  const scopeAnchor: Anchor = anchorBlock
    ? {
        kind: 'block',
        blockId: anchorBlock.id,
        ...(anchorBlock.design.kind === 'annotation' && !anchorBlock.design.state && state
          ? { viewedStateId: state.id }
          : {}),
      }
    : { kind: 'document' };
  const tab = pendingComment ? 'comments' : requestedTab;
  const contextThreads = threads.filter(
    (t) =>
      t.anchor.kind === 'block' &&
      t.anchor.blockId === anchorBlock?.id &&
      t.status !== 'resolved' &&
      targets.get(t.id) !== 'elementDeleted' &&
      targets.get(t.id) !== 'screenDeleted',
  );
  function focusInfo(a: DesignAddress) {
    select(a);
    setTab('details');
    setCollapsed(false);
  }
  function focusComment(a: DesignAddress | null) {
    if (!allowComment || !screen) return;
    const b = (a ? scopedAnnotations.get(designAddressKey(a)) : undefined) ?? state ?? screen;
    const target: Anchor = {
      kind: 'block',
      blockId: b.id,
      ...(b.design.kind === 'annotation' && !b.design.state && state ? { viewedStateId: state.id } : {}),
    };
    go({ screen: screen.id, state: state?.id, address: a ?? undefined });
    setTab('details');
    setCollapsed(false);
    if (reanchoring) {
      onReanchor?.(target);
      return;
    }
    if (!onStartComment) onRequestComment?.(target);
    setComposerFocus((n) => n + 1);
  }
  function follow(to: DesignJson | undefined): 'missing' | 'same' | 'moved' {
    const target = object(to),
      dest = screens.find((b) => b.design.id === target?.screen);
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
    setTab('details');
    setHighlighted(null);
    return 'moved';
  }
  function activate(a: DesignAddress) {
    const h = hotspots.find((h) => sameAddress(h.target, a));
    if (!h) return;
    const first = records(h.block.design.actions)[0];
    if (first && follow(first.to) === 'moved') return;
    focusInfo(a);
  }
  useImperativeHandle(ref, () => ({
    selectBlock(id, reveal = true) {
      const where = flowAnchorLocation(blocks, id);
      if (!where) return;
      go({ screen: where.screenBlockId, state: where.stateBlockId, address: where.address });
      setTab('details');
      if (reveal) setCollapsed(false);
    },
    openHistorical() {
      setTab('details');
      setCollapsed(false);
    },
    showComments() {
      setTab('comments');
      setCollapsed(false);
    },
    showInfo() {
      setTab('details');
      setCollapsed(false);
    },
    setPanelCollapsed: setCollapsed,
  }));
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
          setConfirmation(null);
        }}
      >
        <FlowIcon name="eye" size={13} />
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
        <FlowIcon name="edit" size={13} />
        편집
      </button>
    </div>
  );
  const href = (focus: DesignLocation) =>
    new URL(location.pathname + designQuery('', focus), window.location.origin).href;
  const validScope = !!screen && !invalidState;
  const baseLink = screen ? { screen: screen.id, state: state?.id } : null;
  const showNote = screen?.design.frame === 'note';
  const nameOf = (b: FlowDesignBlock) => stringValue(b.design.name, stringValue(b.design.id));
  const create = (kind: CreateKind) => {
    setMessage('');
    setDialog(kind);
  };
  const goScreen = (id: string) => {
    go({ screen: id });
    setTab('details');
    setHighlighted(null);
    setConfirmation(null);
  };
  const composerAnchor: Anchor = tab === 'comments' ? { kind: 'document' } : scopeAnchor;
  const scopeName = state ? nameOf(state) : '기본 상태';
  const tabs = screens.map((b) => ({
    id: b.id,
    code: stringValue(b.design.code),
    name: nameOf(b),
    comments: screenCounts.get(b.id) ?? 0,
  }));
  const trail = [
    ...navHistory.previous.map((search) => {
      const at = resolveDesignLocation(blocks, search);
      return at.screen ? stringValue(at.screen.design.code) : '';
    }),
    stringValue(screen?.design.code),
  ]
    .filter((code, i, all) => code && (!i || all[i - 1] !== code))
    .slice(-8)
    .join(' › ');
  const directCanvas =
    validScope &&
    (showNote ? (
      canWrite && !viewport ? (
        <DesignNoteEditor screen={screen} mutate={mutate} />
      ) : (
        <article className="flow-note">
          <h2>{nameOf(screen)}</h2>
          <FlowRichText
            assetUrl={(id) => assets.contentUrl(id)}
            text={stringValue(screen.design.description)}
          />
        </article>
      )
    ) : (
      <DesignCanvas
        blocks={blocks}
        scene={scene}
        screen={sc}
        state={st}
        selection={multi ? undefined : row?.address}
        mode={viewport ? 'full' : 'viewport'}
        onActivate={edit ? undefined : activate}
      />
    ));
  if (viewport)
    return (
      <section className="design-full">
        {validScope ? directCanvas : <p role="alert">이 화면 또는 상태를 찾을 수 없습니다.</p>}
      </section>
    );
  const scope = { screen: sc, state: st };
  const selectedTargets = confirmation?.scope === currentScope ? confirmation.targets : undefined;
  const doomed = selectedTargets
    ? new Set(connectedAnnotations(blocks, scope, selectedTargets).map((a) => a.id))
    : undefined;
  const threadCount = doomed
    ? threads.filter((t) => t.anchor.kind !== 'document' && doomed.has(t.anchor.blockId)).length
    : 0;
  return (
    <section className="design-document flow-document" aria-label="화면 디자인" data-save-state={s.status}>
      {editable && (modeTarget ? createPortal(controls, modeTarget) : controls)}
      {statsTarget &&
        createPortal(
          <>
            화면 {screens.length} · 설명 붙은 요소{' '}
            {blocks.filter((b) => b.design.kind === 'annotation' && b.design.enabled !== false).length}
          </>,
          statsTarget,
        )}
      <FlowScreenStrip
        screens={tabs}
        selected={validScope ? screen.id : undefined}
        onSelect={goScreen}
        onAdd={canWrite && screen ? () => addNamedDesignScreen(mutate, go, screen) : undefined}
        after={
          validScope && !showNote && states.length > 0 ? (
            <label className="design-state-picker">
              상태{' '}
              <select
                value={state?.id ?? ''}
                onChange={(e) => {
                  go({ screen: screen.id, state: e.target.value || undefined });
                  setHighlighted(null);
                  setTab('details');
                }}
              >
                <option value="">기본 상태</option>
                {states.map((b) => (
                  <option key={b.id} value={b.id}>
                    {nameOf(b)}
                  </option>
                ))}
              </select>
            </label>
          ) : undefined
        }
      />
      {message && (
        <p className="warning-box" role="alert">
          {message}
        </p>
      )}
      {!validScope ? (
        <div className="flow-delete-confirm" role="alert">
          이 화면 또는 상태를 찾을 수 없습니다.{' '}
          <button
            onClick={() => {
              const s = screen ?? screens[0];
              if (s) goScreen(s.id);
            }}
          >
            화면 보기
          </button>
        </div>
      ) : (
        <>
          {(invalidNode || (address && !row)) && (
            <p className="warning-box" role="alert">
              선택한 요소가 이 상태에 없거나 링크가 올바르지 않습니다.{' '}
              <button onClick={() => go({ screen: screen.id, state: state?.id })}>화면 보기</button>
            </p>
          )}
          <div
            className={collapsed && !pendingComment ? 'flow-layout design-panel-collapsed' : 'flow-layout'}
          >
            <DesignViewStage
              blocks={blocks}
              scene={scene}
              screen={screen}
              state={state}
              editing={edit}
              diagnosticsTarget={diagnosticsTarget}
              framePicker={
                canWrite && !showNote ? (
                  <DesignFramePicker blocks={blocks} screen={screen} state={state} mutate={mutate} />
                ) : undefined
              }
              markers={markers}
              highlighted={highlighted}
              selection={multi ? undefined : row?.address}
              onHighlight={setHighlighted}
              onInfo={focusInfo}
              onComment={allowComment ? focusComment : undefined}
              back={navHistory.previous.length ? () => travel(true) : undefined}
              forward={navHistory.following.length ? () => travel(false) : undefined}
              trail={trail}
              tools={
                edit ? (
                  <>
                    <button
                      type="button"
                      aria-label="실행 취소 (⌘Z)"
                      disabled={!canWrite || !historySizes.undo}
                      onClick={() => undo(true)}
                    >
                      ↶
                    </button>
                    <button
                      type="button"
                      aria-label="다시 실행 (⇧⌘Z)"
                      disabled={!canWrite || !historySizes.redo}
                      onClick={() => undo(false)}
                    >
                      ↷
                    </button>
                  </>
                ) : undefined
              }
              palette={
                canWrite && !showNote ? (
                  <DesignElementPalette
                    screen={screen}
                    state={state}
                    selected={row?.address}
                    frame={scene.frame}
                    mutate={mutate}
                    go={go}
                  >
                    <details open={layersOpen} onToggle={(e) => setLayersOpen(e.currentTarget.open)}>
                      <summary>레이어·컴포넌트</summary>
                      {layersOpen && (
                        <>
                          <DesignTree
                            rows={allRows}
                            selected={row?.address}
                            editable={canWrite}
                            scope={scope}
                            mutate={mutate}
                            select={focusInfo}
                          />
                          <div className="design-buttons">
                            <button onClick={() => create('component')}>컴포넌트 만들기</button>
                            {insertParent && (
                              <>
                                <button onClick={() => create('box')}>상자 추가</button>
                                <button onClick={() => create('text')}>텍스트 추가</button>
                                {blocks.some((b) => b.design.kind === 'component') && (
                                  <button onClick={() => create('instance')}>컴포넌트 배치</button>
                                )}
                              </>
                            )}
                            <button onClick={() => create('import')}>마크업 가져오기</button>
                          </div>
                        </>
                      )}
                    </details>
                  </DesignElementPalette>
                ) : undefined
              }
            >
              {edit && !showNote ? (
                <DesignEditCanvas
                  ref={canvasCommands}
                  onMeasure={measureSelection}
                  blocks={blocks}
                  rows={allRows}
                  scope={scope}
                  selection={selection.addresses}
                  onSelect={selection.change}
                  mutate={mutate}
                  editable={canWrite}
                  onUndo={() => undo(true)}
                  onRedo={() => undo(false)}
                  onRequestDelete={(targets) => {
                    setConfirmation({ scope: currentScope, targets });
                    setCollapsed(false);
                    setTab('details');
                  }}
                  onMessage={setMessage}
                >
                  {directCanvas}
                </DesignEditCanvas>
              ) : (
                directCanvas
              )}
            </DesignViewStage>
            {collapsed && !pendingComment ? (
              <button
                type="button"
                className="flow-reopen"
                aria-label="설명·댓글"
                title="설명·댓글 패널 펼치기"
                onClick={() => setCollapsed(false)}
              >
                <FlowIcon name="panel" />
                설명·댓글 <FlowCommentBadge count={openCount} />
              </button>
            ) : (
              <DesignSidePanel
                tab={tab}
                edit={edit}
                openCount={openCount}
                allowComment={allowComment}
                onCollapse={() => setCollapsed(true)}
                onTab={setTab}
                onDocumentComment={() => {
                  setTab('comments');
                  setComposerFocus((n) => n + 1);
                  if (!onStartComment) onRequestComment?.({ kind: 'document' });
                }}
                composerAnchor={composerAnchor}
                composerFocus={composerFocus}
                mentionLogins={mentionLogins}
                onStartComment={pendingComment ? undefined : onStartComment}
                composerLabel={
                  tab === 'comments'
                    ? '문서에 댓글'
                    : item && !multi
                      ? item.name + ' 요소에 댓글'
                      : stringValue(screen.design.code) +
                        ' ' +
                        nameOf(screen) +
                        (state ? ' · ' + scopeName : '') +
                        ' 화면에 댓글'
                }
              >
                {tab === 'comments' ? (
                  commentRail?.()
                ) : (
                  <>
                    {selectedTargets && edit && (
                      <DesignDeleteConfirmation
                        label="요소 삭제 확인"
                        title={`${selectedTargets.length > 1 ? '요소 ' + String(selectedTargets.length) + '개' : '“' + (row?.label ?? '선택한 요소') + '” 요소'}를 삭제할까요?`}
                        notice={`${state ? '이 상태에서만 요소가 숨겨집니다. ' : ''}${threadCount ? '댓글 ' + String(threadCount) + '개는 댓글 탭에 남습니다.' : '이 요소에 달린 댓글이 없습니다.'}`}
                        onCancel={() => setConfirmation(null)}
                        onConfirm={() => {
                          if (canvasCommands.current?.remove(selectedTargets)) setConfirmation(null);
                        }}
                      />
                    )}
                    {(row || multi) && (
                      <button
                        className="flow-back-detail"
                        onClick={() => {
                          go({ screen: screen.id, state: state?.id });
                          setHighlighted(null);
                        }}
                      >
                        <FlowIcon name="left" size={14} />
                        {stringValue(screen.design.code)} {nameOf(screen)} 화면 {edit ? '속성' : '설명'}으로
                      </button>
                    )}
                    {edit ? (
                      <>
                        {multi ? (
                          <DesignMultiSelection
                            selection={selection.addresses}
                            rows={allRows}
                            commands={canvasCommands}
                          />
                        ) : row ? (
                          <DesignNodeInspector
                            key={designAddressKey(row.address) + '/' + (st ?? '')}
                            blocks={blocks}
                            row={row}
                            screen={sc}
                            state={st}
                            rows={allRows}
                            inputs={scene.inputs}
                            mutate={mutate}
                            select={select}
                            annotation={annotation}
                            measuredRect={
                              measurement?.key === designAddressKey(row.address)
                                ? measurement.rect
                                : undefined
                            }
                            canResizeMeasured={
                              measurement?.key === designAddressKey(row.address) && measurement.resizable
                            }
                            number={number}
                            frame={scene.frame}
                            uploadImage={uploadImage}
                            onFollow={follow}
                            onCreateAnnotation={() => create('annotation')}
                            onDuplicate={() => canvasCommands.current?.execute('duplicate')}
                            onLayer={(front) => canvasCommands.current?.execute(front ? 'front' : 'back')}
                            onDelete={() => canvasCommands.current?.execute('delete')}
                          />
                        ) : (
                          <DesignScreenProperties
                            blocks={blocks}
                            screen={screen}
                            state={state}
                            mutate={mutate}
                            go={go}
                            threadCount={
                              threads.filter(
                                (t) =>
                                  t.anchor.kind !== 'document' &&
                                  flowAnchorLocation(blocks, t.anchor.blockId)?.screenBlockId === screen.id,
                              ).length
                            }
                            elementCount={
                              records(object(screen.design.root)?.children).length ||
                              (object(screen.design.root)?.kind === 'instance' ? 1 : 0)
                            }
                            annotationCount={hotspots.length}
                            missingCount={
                              hotspots.filter((h) => !stringValue(h.block.design.description).trim()).length
                            }
                          />
                        )}
                        {!row && !multi && !showNote && (
                          <button
                            type="button"
                            className="btn btn-small"
                            disabled={!canWrite}
                            onClick={() => create('state')}
                          >
                            상태 추가
                          </button>
                        )}
                        <DesignTokens blocks={blocks} mutate={mutate} />
                      </>
                    ) : item ? (
                      <DesignElementDetails
                        item={item}
                        number={number}
                        blocks={blocks}
                        assetUrl={(id) => assets.contentUrl(id)}
                        onFollow={follow}
                      />
                    ) : (
                      <DesignOverview
                        key={screen.id + '/' + (state?.id ?? '')}
                        blocks={blocks}
                        screen={screen}
                        state={state}
                        items={hotspots}
                        assetUrl={(id) => assets.contentUrl(id)}
                        highlighted={highlighted}
                        onInfo={focusInfo}
                        onHighlight={setHighlighted}
                        onFollow={follow}
                      />
                    )}
                    {reanchoring && (
                      <button onClick={() => onReanchor?.(scopeAnchor)}>여기에 다시 달기</button>
                    )}
                    {(item || multi || contextThreads.length > 0) && (
                      <section className="flow-context-comments">
                        <h3>
                          {item && !multi ? '이 요소의 댓글' : '이 화면 전체에 단 댓글'}{' '}
                          {contextThreads.length}
                        </h3>
                        {contextThreads.length ? (
                          commentRail?.(anchorBlock?.id)
                        ) : (
                          <p>
                            {multi
                              ? '여러 요소를 고른 상태라 아래에서 남기면 이 화면에 붙습니다.'
                              : '아직 댓글이 없습니다.'}
                          </p>
                        )}
                      </section>
                    )}
                    {!onStartComment && onRequestComment && (
                      <button onClick={() => onRequestComment(scopeAnchor)}>
                        {item ? '요소' : '화면'}에 댓글 달기
                      </button>
                    )}
                    {baseLink && (
                      <div className="design-share-actions">
                        <CopyButton text={href({ screen: screen.id })} label="화면 링크 복사" />
                        <a
                          className="btn btn-small"
                          href={href({ ...baseLink, address: row?.address }) + '&flowView=full'}
                          target="_blank"
                          rel="noreferrer"
                        >
                          단독 보기
                        </a>
                        {state && <CopyButton text={href(baseLink)} label="상태 링크 복사" />}
                        {row && (
                          <CopyButton
                            text={href({ ...baseLink, address: row.address })}
                            label="요소 링크 복사"
                          />
                        )}
                      </div>
                    )}
                    <div className="design-preview-diagnostics" ref={setDiagnosticsTarget} />
                  </>
                )}
              </DesignSidePanel>
            )}
          </div>
        </>
      )}
      {dialog && screen && canWrite && (
        <DesignCreateDialog
          kind={dialog}
          screen={screen}
          state={state}
          selected={row?.address}
          parent={insertParent}
          blocks={blocks}
          mutate={mutate}
          go={go}
          close={() => setDialog(null)}
          key={dialog}
        />
      )}
    </section>
  );
}
