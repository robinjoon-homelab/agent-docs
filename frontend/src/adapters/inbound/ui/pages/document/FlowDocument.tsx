import { FlowMultiInspector } from './FlowMultiInspector';
import { FlowElementInspector } from './FlowElementInspector';
import { FlowScreenInspector } from './FlowScreenInspector';
import { flowPreview } from './flowPreview';
import { useFlowSelection } from './useFlowSelection';
import { FlowSelectionLink } from './FlowSelectionLink';
import { createPortal } from 'react-dom';
import { FlowIcon, FlowCommentBadge, FlowToolIcon } from './FlowIcon';
import { FlowOverview } from './FlowOverview';
import { useFlowCommentTargets } from './useFlowCommentTargets';
import { FlowElementVisual } from './FlowElementVisual';
import { CommentEditor } from './CommentEditor';
import { useFlowUndo } from './useFlowUndo';
import type { ReactNode, Ref } from 'react';
import type { Thread } from '../../../../../domain/thread/thread';
import { ACTION_MODES } from './FlowElementOptions';
import {
  alignElements,
  bounds,
  snapMove,
  cloneElements,
  resizeElement,
  anchoredScreenId,
  fitScale,
  isNotePage,
} from '../../../../../domain/flow/flow';
import { useStageArea } from './useStageArea';
import { FlowRichText } from './FlowRichText';
import { useServices } from '../../../../../config/ServicesContext';
import { UPLOAD_ERROR_TEXT } from '../../../../../application/asset/assetUseCases';
import { canComment } from '../../../../../domain/space/permission';
import { useDocumentPermission } from './DocumentPermissionContext';
import { useEffect, useImperativeHandle, useRef, useState, useSyncExternalStore } from 'react';
import { NavigationType, useLocation, useNavigate, useNavigationType } from 'react-router';
import type { DocumentEditorSession } from '../../../../../application/document/editorSession';
import type { Block, FlowElementBlock, FlowKind, FlowFrame } from '../../../../../domain/document/block';
import { newBlockId } from '../../../../../domain/document/block';
import {
  readFlow,
  flowBlocks,
  frameSize,
  FRAMES,
  numbered,
  nextScreenId,
  newElement,
  KINDS,
  warnings,
  fitElement,
} from '../../../../../domain/flow/flow';
import type { Flow } from '../../../../../domain/flow/flow';
import type { Anchor } from '../../../../../domain/thread/thread';
import './flow.css';
const id = () => newBlockId(() => crypto.getRandomValues(new Uint8Array(1))[0] ?? 0);
export type FlowHandle = {
  selectBlock: (blockId: string, reveal?: boolean) => void;
  openHistorical: (screenBlockId: string) => void;
  showComments: () => void;
  showInfo: () => void;
  setPanelCollapsed: (collapsed: boolean) => void;
};
export function FlowDocument({
  ref,
  modeTarget,
  statsTarget,
  session,
  editable,
  onInput,
  onEditMode,
  commentRail,
  threads,
  onReanchor,
  reanchoring,
  onStartComment,
  mentionLogins,
  spaceSlug,
}: {
  modeTarget: HTMLElement | null;
  statsTarget: HTMLElement | null;
  ref?: Ref<FlowHandle>;
  reanchoring: boolean;
  onReanchor: (anchor: Anchor) => void;
  onStartComment: (anchor: Anchor, body: string) => Promise<boolean>;
  mentionLogins: readonly string[];
  spaceSlug: string;
  commentRail: (scope?: string) => ReactNode;
  threads: readonly Thread[];
  session: DocumentEditorSession;
  editable: boolean;
  onInput: () => void;
  onEditMode: () => void;
  onRequestComment: (anchor: Anchor) => void;
}) {
  const preview = flowPreview();
  const commentable = canComment(useDocumentPermission());

  const state = useSyncExternalStore(session.subscribe, session.getState);
  const location = useLocation();
  const navigationType = useNavigationType();
  const routerNavigate = useNavigate();
  useEffect(() => {
    if ((location.state as { flowEdit?: boolean } | null)?.flowEdit)
      void routerNavigate(location.pathname + location.search + location.hash, {
        replace: true,
        state: null,
      });
  }, [location, routerNavigate]);
  const [editing, setEditing] = useState(
    preview.mode === 'edit' || (location.state as { flowEdit?: boolean } | null)?.flowEdit === true,
  );
  const [previousEditable, setPreviousEditable] = useState(editable);
  if (previousEditable !== editable) {
    setPreviousEditable(editable);
    if (!editable) setEditing(false);
  }
  const edit = editing && editable;
  const flow = readFlow(session.currentBlocks());
  const history = useFlowUndo({
    past:
      preview.selection?.length && new URLSearchParams(location.search).get('flowCase') === '53'
        ? [
            flowBlocks(flow).map((b) =>
              b.type === 'flowElement' && b.id === preview.selection?.[0] ? { ...b, y: 578 } : b,
            ),
          ]
        : [],
    future: preview.redo
      ? [
          flowBlocks(flow).map((b) =>
            b.type === 'flowElement' && b.id === preview.selection?.[0] ? { ...b, y: 640 } : b,
          ),
        ]
      : [],
  });
  const {
    current: { screenId: current, elements: selected },
    select,
    link,
  } = useFlowSelection(flow.screens, preview);
  const active = flow.screens.find((s) => s.screen.id === current) ?? flow.screens[0];
  const [trail, setTrail] = useState<string[]>(preview.history ?? []);
  const [tab, setTab] = useState<'info' | 'comments'>(preview.tab === 'comments' ? 'comments' : 'info');
  const [collapsed, setCollapsed] = useState(preview.collapsed ?? false);
  const [confirmation, setConfirmation] = useState<'elements' | 'screen' | null>(
    preview.confirm === 'screen' ? 'screen' : preview.confirm ? 'elements' : null,
  );
  const setSelected = (ids: string[], record = false) => {
    setConfirmation(null);
    select(active?.screen.id ?? current, ids, record);
  };
  const [lastLocation, setLastLocation] = useState<string>();
  if (lastLocation !== location.key) {
    setLastLocation(location.key);
    if (
      (lastLocation === undefined || navigationType === NavigationType.Pop) &&
      link &&
      !link.error &&
      !location.hash
    ) {
      setTab('info');
      setCollapsed(false);
      setConfirmation(null);
    }
  }
  const [markers, setMarkers] = useState(preview.markers ?? false);
  const [zoom, setZoom] = useState<'fit' | number>(preview.zoom === '100' ? 1 : 'fit');
  const services = useServices();
  const stageRef = useRef<HTMLDivElement>(null);
  const linkedElement = link?.elementId;
  useEffect(() => {
    if (linkedElement && !location.hash)
      stageRef.current
        ?.querySelector('[data-linked-element]')
        ?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }, [linkedElement, location.key, location.hash]);
  const [expanded, setExpanded] = useState<string[]>(preview.expanded ?? []);
  const [dragging, setDragging] = useState<string | null>(preview.drag ?? null);
  const [hover, setHover] = useState<string | null>(preview.hover ?? null);
  const clipboard = useRef<FlowElementBlock[]>([]);
  const [guides, setGuides] = useState<{ axis: 'x' | 'y'; value: number }[]>(preview.guides ?? []);
  const [marquee, setMarquee] = useState<{ x: number; y: number; w: number; h: number } | null>(
    preview.marquee ?? null,
  );
  const range = useRef<{ x: number; y: number; initial: string[] } | null>(null);

  const gesture = useRef<{
    x: number;
    y: number;
    elements: FlowElementBlock[];
    before: Block[];
    resize: string;
    selected: string[];
    moved: boolean;
    clickSelection?: string[];
  } | null>(null);
  useImperativeHandle(ref, () => ({
    selectBlock: (blockId: string, reveal = true) => {
      const parent = flow.screens.find(
        (s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId),
      );
      if (parent) {
        if (active && parent.screen.id !== active.screen.id)
          setTrail([...trail, active.screen.screenId].slice(-7));
        setConfirmation(null);
        select(parent.screen.id, parent.screen.id === blockId ? [] : [blockId]);
        setTab('info');
        if (reveal) setCollapsed(false);
      }
    },
    openHistorical: (screenBlockId: string) => {
      const parent = flow.screens.find((s) => s.screen.id === screenBlockId);
      if (parent && parent.screen.id !== active?.screen.id) {
        if (active) setTrail([...trail, active.screen.screenId].slice(-7));
        select(parent.screen.id, []);
      }
      setHover(null);
      setConfirmation(null);
    },
    showComments: () => {
      setTab('comments');
      setCollapsed(false);
    },
    showInfo: () => {
      setTab('info');
    },
    setPanelCollapsed: setCollapsed,
  }));
  const targets = useFlowCommentTargets(
    threads,
    session.currentBlocks(),
    state.status === 'saved' ? state.version : undefined,
  );
  if (!active) throw new Error('화면이 없습니다.');
  const screen = active.screen;
  const element = selected.length === 1 ? active.elements.find((e) => e.id === selected[0]) : undefined;
  const contextThreads = threads.filter(
    (t) =>
      t.anchor.kind !== 'document' &&
      t.anchor.blockId === (element?.id ?? screen.id) &&
      targets.get(t.id) !== 'elementDeleted' &&
      targets.get(t.id) !== 'screenDeleted',
  );
  const selectedCommentCount = threads.filter(
    (t) => t.anchor.kind !== 'document' && selected.includes(t.anchor.blockId),
  ).length;
  const screenCommentCount = threads.filter(
    (t) =>
      anchoredScreenId(
        t.anchor.kind === 'document' ? null : t.anchor.blockId,
        flow.screens,
        targets.parents.get(t.id),
      ) === screen.id,
  ).length;
  const inboundCount = flow.screens
    .filter((s) => s !== active)
    .flatMap((s) => s.elements)
    .filter((e) => e.actions.some((a) => a.to === screen.screenId)).length;
  const deletionNotice =
    confirmation === 'screen'
      ? `이 화면에 달린 댓글 ${screenCommentCount}개는 댓글 탭의 "삭제된 화면"으로 옮겨지고, 누르면 삭제 전 화면을 볼 수 있습니다.${inboundCount ? ` 이 화면으로 가는 요소 ${inboundCount}개에는 "없는 화면" 경고가 붙습니다.` : ''}`
      : selectedCommentCount
        ? selected.length === 1
          ? `이 요소에 달린 댓글 ${selectedCommentCount}개는 댓글 사이드바에만 남고, 댓글에서 "삭제된 요소 보기"로 삭제 전 모습을 볼 수 있습니다.`
          : `이 요소들에 달린 댓글 ${selectedCommentCount}개는 댓글 사이드바에만 남고, "삭제된 요소 보기"로 삭제 전 모습을 볼 수 있습니다.`
        : selected.length === 1
          ? '이 요소에는 댓글이 없습니다.'
          : '이 요소들에는 댓글이 없습니다.';
  const [frameName, width, height] = frameSize(flow, screen);
  const frameKey = screen.frame ?? flow.settings.frame;
  const chrome = frameKey.startsWith('phone')
    ? 'phone'
    : frameKey.startsWith('tablet')
      ? 'tablet'
      : frameKey === 'laptop' || frameKey === 'desktop'
        ? 'window'
        : frameKey === 'modal'
          ? 'modal'
          : 'plain';
  const pad = chrome === 'phone' ? 8 : chrome === 'tablet' ? 12 : chrome === 'modal' ? 0 : 1;
  const outerW = width + pad * 2,
    outerH = height + pad * 2 + (chrome === 'window' ? 30 : 0);
  // The palette (150px + 20px gap) shares the row in edit mode; the caption takes about 30px below the frame.
  const stageArea = useStageArea(stageRef, edit ? 170 : 0, 30);
  const scale =
    zoom === 'fit' ? fitScale(outerW, outerH, stageArea ?? { width: edit ? 560 : 740, height: 690 }) : zoom;
  const note = isNotePage(screen);
  const patchScreen = (patch: Partial<typeof screen>) =>
    onCommit(
      {
        ...flow,
        screens: flow.screens.map((s) => (s === active ? { ...s, screen: { ...screen, ...patch } } : s)),
      },
      true,
      screen.id + Object.keys(patch).join(','),
    );
  const uploadImage = async (file: File) => {
    if (!file.type.startsWith('image/')) throw new Error('이미지 파일을 골라 주세요.');
    const uploaded = await services.assets.upload(spaceSlug, {
      name: file.name,
      type: file.type,
      size: file.size,
      data: file,
    });
    if (!uploaded.ok) throw new Error(UPLOAD_ERROR_TEXT[uploaded.error]);
    return `asset:${uploaded.value.id}`;
  };

  const numbers = numbered(active.elements);
  const onCommit = (next: Flow, record = true, key?: string) => {
    if (!edit) return;
    if (record) history.record(flowBlocks(flow), key);
    const blocks = flowBlocks(next);
    onInput();
    session.markEdited(blocks);
    void session.saveContent(blocks);
  };
  const replaceElements = (elements: FlowElementBlock[], record = true, key?: string) =>
    onCommit(
      { ...flow, screens: flow.screens.map((s) => (s.screen.id === screen.id ? { ...s, elements } : s)) },
      record,
      key,
    );
  const onPatch = (change: Partial<FlowElementBlock>) => {
    const key = selected.join(',') + Object.keys(change).join(',');
    replaceElements(
      active.elements.map((e) =>
        selected.includes(e.id)
          ? Object.keys(change).some((k) => ['x', 'y', 'w', 'h'].includes(k))
            ? fitElement({ ...e, ...change }, width)
            : { ...e, ...change }
          : e,
      ),
      true,
      key,
    );
  };
  const navigate = (target: string) => {
    const found = flow.screens.find((s) => s.screen.screenId === target);
    if (found?.screen.id === screen.id) {
      setSelected([], true);
      return;
    }
    if (found && found.screen.id !== screen.id) {
      setTrail([...trail, screen.screenId].slice(-7));
      setConfirmation(null);
      select(found.screen.id, [], true);
    }
  };
  const travel = (back: boolean) => {
    const blocks = history.travel(flowBlocks(flow), back);
    if (blocks) {
      const restored = readFlow(blocks);
      const restoredScreen = restored.screens.find((s) => s.screen.id === current) ?? restored.screens[0];
      onCommit({ ...restored, settings: flow.settings }, false);
      if (restoredScreen) {
        setConfirmation(null);
        select(
          restoredScreen.screen.id,
          selected.filter((id) => restoredScreen.elements.some((e) => e.id === id)),
          true,
        );
      }
    }
  };
  const remove = () => setConfirmation('elements');
  const duplicate = (paste = false) => {
    if (note) return;
    const copies = cloneElements(
      paste ? clipboard.current : active.elements.filter((e) => selected.includes(e.id)),
      { id, offset: 16, copyName: !paste, width },
    );
    replaceElements([...active.elements, ...copies]);
    setSelected(
      copies.map((e) => e.id),
      true,
    );
    if (paste) clipboard.current = copies;
  };
  const addScreen = (copy = false) => {
    const screenId = nextScreenId(flow.settings);
    const fresh = {
      screen: {
        ...screen,
        id: id(),
        screenId,
        name: copy ? (screen.name + ' 사본').slice(0, 100) : '새 화면',
        ...(copy ? {} : { entry: '', desc: '', frame: null, frameW: null, frameH: null }),
      },
      elements: copy ? cloneElements(active.elements, { id, offset: 0, copyName: false, width }) : [],
    };
    const screens = [...flow.screens];
    screens.splice(screens.indexOf(active) + 1, 0, fresh);
    onCommit({ settings: { ...flow.settings, nextScreen: flow.settings.nextScreen + 1 }, screens });
    setConfirmation(null);
    select(fresh.screen.id, [], true);
  };
  return (
    <section
      className={`flow-document${preview.tall ? ' flow-board-tall' : ''}`}
      data-save-state={state.status}
    >
      {modeTarget &&
        editable &&
        createPortal(
          <div className="flow-segmented" aria-label="문서 모드">
            <button
              aria-pressed={!edit}
              onClick={() => {
                setEditing(false);
                setConfirmation(null);
              }}
            >
              <FlowIcon name="eye" size={13} />
              보기
            </button>
            <button
              aria-pressed={edit}
              onClick={() => {
                onEditMode();
                setEditing(true);
                setConfirmation(null);
              }}
            >
              <FlowIcon name="edit" size={13} />
              편집
            </button>
          </div>,
          modeTarget,
        )}
      {statsTarget &&
        createPortal(
          <>
            화면 {flow.screens.length} · 설명 붙은 요소{' '}
            {flow.screens.reduce((n, s) => n + numbered(s.elements).length, 0)}
          </>,
          statsTarget,
        )}
      <nav className="flow-strip">
        <button
          className="flow-strip-arrow"
          aria-label="이전 화면"
          disabled={flow.screens.indexOf(active) === 0}
          onClick={() => {
            const prev = flow.screens[flow.screens.indexOf(active) - 1];
            if (prev) navigate(prev.screen.screenId);
          }}
        >
          <FlowIcon name="left" />
        </button>
        <div className="flow-screen-buttons">
          {flow.screens.map((s) => (
            <button
              key={s.screen.id}
              aria-pressed={s === active && !link?.error}
              onClick={() => navigate(s.screen.screenId)}
            >
              <span>{s.screen.screenId}</span>
              {s.screen.name}
              <FlowCommentBadge
                count={
                  threads.filter(
                    (t) =>
                      t.status !== 'resolved' &&
                      anchoredScreenId(
                        t.anchor.kind === 'document' ? null : t.anchor.blockId,
                        flow.screens,
                        targets.parents.get(t.id),
                      ) === s.screen.id,
                  ).length
                }
              />
            </button>
          ))}
          {edit && !link?.error && (
            <button className="flow-add-screen" onClick={() => addScreen()}>
              <FlowIcon name="plus" size={14} />
              화면 추가
            </button>
          )}
        </div>
        <button
          className="flow-strip-arrow"
          aria-label="다음 화면"
          disabled={flow.screens.indexOf(active) === flow.screens.length - 1}
          onClick={() => {
            const next = flow.screens[flow.screens.indexOf(active) + 1];
            if (next) navigate(next.screen.screenId);
          }}
        >
          <FlowIcon name="right" />
        </button>
      </nav>
      {link?.error && (
        <div role="status" className="flow-delete-confirm">
          <span>{link.error}</span>
          <button type="button" onClick={() => select(screen.id, [], true)}>
            {link.screenId ? '화면 보기' : '첫 화면 보기'}
          </button>
        </div>
      )}
      <div className="flow-layout" style={link?.error ? { display: 'none' } : undefined}>
        <section className="flow-preview">
          <div className={edit ? 'flow-toolbar' : 'flow-toolbar is-view'}>
            <button
              disabled={!trail.length}
              onClick={() => {
                const target = trail.at(-1);
                if (target) {
                  navigate(target);
                  setTrail(trail.slice(0, -1));
                }
              }}
            >
              <FlowIcon name="left" size={14} />
              뒤로
            </button>
            <span className="flow-trail">{[...trail, screen.screenId].join(' › ')}</span>
            {edit && (
              <>
                <span className="flow-editing-chip">편집 중</span>
                <button aria-label="실행 취소 (⌘Z)" disabled={!history.undo} onClick={() => travel(true)}>
                  ↶
                </button>
                <button aria-label="다시 실행 (⇧⌘Z)" disabled={!history.redo} onClick={() => travel(false)}>
                  ↷
                </button>
              </>
            )}
            <span className="flow-stage-hint">
              {edit ? 'Shift+클릭·빈 곳 끌기로 여러 개 선택' : '요소를 누르면 이동 · (i)로 설명'}
            </span>
            {edit && (
              <label className="flow-frame-select">
                기기 틀
                <select
                  value={screen.frame ?? ''}
                  onChange={(event) =>
                    onCommit({
                      ...flow,
                      screens: flow.screens.map((s) =>
                        s === active
                          ? {
                              ...s,
                              screen: {
                                ...screen,
                                frame: (event.target.value || null) as FlowFrame | 'custom' | 'note' | null,
                                frameW: event.target.value === 'custom' ? 600 : null,
                                frameH: event.target.value === 'custom' ? 720 : null,
                              },
                            }
                          : s,
                      ),
                    })
                  }
                >
                  <option value="">문서 기본값 따름 ({FRAMES[flow.settings.frame][0]})</option>
                  {Object.entries(FRAMES).map(([key, value]) => (
                    <option value={key} key={key}>
                      {value[0]} {value[1]}×{value[2]}
                    </option>
                  ))}
                  <option value="custom">직접 입력</option>
                  <option value="note" disabled={active.elements.length > 0}>
                    글 페이지 (요소 없는 화면만)
                  </option>
                </select>
              </label>
            )}
            {!edit && !note && (
              <button
                aria-pressed={markers}
                className="flow-markers-toggle"
                onClick={() => setMarkers(!markers)}
              >
                번호 모두 표시
              </button>
            )}
            {!note && (
              <div className="flow-segmented">
                {(['fit', 0.5, 0.75, 1] as const).map((level) => (
                  <button key={level} aria-pressed={zoom === level} onClick={() => setZoom(level)}>
                    {level === 'fit' ? '맞춤' : `${level * 100}%`}
                  </button>
                ))}
              </div>
            )}
          </div>
          <div className="flow-stage-content" ref={stageRef}>
            {edit && !note && (
              <aside className="flow-palette">
                <strong>요소 추가</strong>
                {Object.entries(KINDS)
                  .filter(([k]) => k !== 'option' && k !== 'crumb')
                  .map(([kind, label]) => (
                    <button
                      key={kind}
                      onClick={() => {
                        const e = newElement(id(), kind as FlowKind, width, height, element);
                        replaceElements([...active.elements, e]);
                        setSelected([e.id], true);
                      }}
                    >
                      <FlowIcon name="plus" size={13} />
                      {label}
                    </button>
                  ))}
                <p>
                  선택한 요소 아래 12px에, 없으면 가로 가운데·세로 1/3에 놓입니다. 상단 바는 맨 위. 놓은 뒤
                  끌어서 옮기세요.
                </p>
              </aside>
            )}
            {note ? (
              <article className="flow-note">
                {edit ? (
                  <textarea
                    className="flow-note-editor"
                    aria-label="글 페이지 본문"
                    value={screen.desc}
                    maxLength={20000}
                    placeholder="이 글 페이지의 본문을 쓰세요. 목록(- ), 표(|), **굵게**, `코드`, [링크](doc:ID)를 쓸 수 있습니다."
                    onChange={(event) => patchScreen({ desc: event.target.value })}
                  />
                ) : screen.desc.trim() ? (
                  <FlowRichText text={screen.desc} />
                ) : (
                  <p className="muted">본문이 없습니다.</p>
                )}
                <p className="flow-frame-caption">글 페이지 · {screen.desc.length}자</p>
              </article>
            ) : (
              <div className="flow-stage">
                <div
                  className="flow-frame-box"
                  style={{ width: Math.round(outerW * scale), height: Math.round(outerH * scale) }}
                >
                  <div
                    className={`flow-device chrome-${chrome}`}
                    style={{ width: outerW, height: outerH, transform: `scale(${scale})` }}
                  >
                    {chrome === 'window' && (
                      <div className="flow-window-bar">
                        <i />
                        <i />
                        <i />
                        <span>
                          agent-docs · {screen.screenId} {screen.name}
                        </span>
                      </div>
                    )}
                    <div className="flow-screen-viewport" style={{ width, height }}>
                      <div
                        className={edit ? 'flow-canvas editing' : 'flow-canvas'}
                        style={{
                          width,
                          minHeight:
                            Math.max(height, ...active.elements.map((e) => e.y + e.h)) + (edit ? 160 : 0),
                        }}
                        tabIndex={0}
                        onKeyDown={(e) => {
                          if (
                            !edit ||
                            e.target instanceof HTMLInputElement ||
                            e.target instanceof HTMLTextAreaElement ||
                            e.target instanceof HTMLSelectElement
                          )
                            return;
                          const cmd = e.metaKey || e.ctrlKey;
                          if (cmd && ['a', 'c', 'v', 'd', 'z', 'y', 'g'].includes(e.key.toLowerCase())) {
                            e.preventDefault();
                            switch (e.key.toLowerCase()) {
                              case 'a':
                                setSelected(active.elements.filter((x) => !x.pinned).map((x) => x.id));
                                break;
                              case 'c':
                                clipboard.current = structuredClone(active.elements).filter((x) =>
                                  selected.includes(x.id),
                                );
                                break;
                              case 'v':
                                duplicate(true);
                                break;
                              case 'd':
                                duplicate();
                                break;
                              case 'z':
                                travel(!e.shiftKey);
                                break;
                              case 'y':
                                travel(false);
                                break;
                              case 'g':
                                onPatch({ group: e.shiftKey ? null : id() });
                                break;
                            }
                          } else if (e.key === 'Delete' || e.key === 'Backspace') {
                            e.preventDefault();
                            remove();
                          } else if (e.key.startsWith('Arrow')) {
                            e.preventDefault();
                            const step = e.shiftKey ? 10 : 1;
                            replaceElements(
                              active.elements.map((x) =>
                                selected.includes(x.id) && !x.pinned
                                  ? fitElement(
                                      {
                                        ...x,
                                        x:
                                          x.x +
                                          (e.key === 'ArrowRight' ? step : e.key === 'ArrowLeft' ? -step : 0),
                                        y:
                                          x.y +
                                          (e.key === 'ArrowDown' ? step : e.key === 'ArrowUp' ? -step : 0),
                                      },
                                      width,
                                    )
                                  : x,
                              ),
                            );
                          }
                        }}
                        onPointerDown={(e) => {
                          if (edit && e.target === e.currentTarget) {
                            const rect = e.currentTarget.getBoundingClientRect();
                            range.current = {
                              x: (e.clientX - rect.left) / scale,
                              y: (e.clientY - rect.top) / scale,
                              initial: e.shiftKey ? selected : [],
                            };
                            if (!e.shiftKey) setSelected([]);
                            e.currentTarget.setPointerCapture(e.pointerId);
                          }
                        }}
                        onPointerMove={(e) => {
                          if (!edit) return;
                          const r = range.current;
                          if (r) {
                            const rect = e.currentTarget.getBoundingClientRect();
                            const x = (e.clientX - rect.left) / scale,
                              y = (e.clientY - rect.top) / scale;
                            const box = {
                              x: Math.min(x, r.x),
                              y: Math.min(y, r.y),
                              w: Math.abs(x - r.x),
                              h: Math.abs(y - r.y),
                            };
                            setMarquee(box);
                            setSelected([
                              ...new Set([
                                ...r.initial,
                                ...active.elements
                                  .filter(
                                    (el) =>
                                      !el.pinned &&
                                      el.x < box.x + box.w &&
                                      el.x + el.w > box.x &&
                                      el.y < box.y + box.h &&
                                      el.y + el.h > box.y,
                                  )
                                  .map((el) => el.id),
                              ]),
                            ]);
                            return;
                          }
                          const g = gesture.current;
                          if (!g) return;
                          const dx = (e.clientX - g.x) / scale,
                            dy = (e.clientY - g.y) / scale;
                          if (Math.abs(dx) + Math.abs(dy) < 2) return;
                          g.moved = true;
                          setDragging(g.selected[0] ?? null);
                          const moving = g.elements.filter((x) => g.selected.includes(x.id) && !x.pinned);
                          if (!moving.length) return;
                          const box = bounds(moving);
                          const snapped = snapMove(
                            box,
                            dx,
                            dy,
                            g.elements.filter((x) => !g.selected.includes(x.id)),
                            width,
                            e.altKey,
                          );
                          setGuides(snapped.guides);
                          replaceElements(
                            g.elements.map((x) =>
                              !moving.includes(x)
                                ? x
                                : g.resize
                                  ? resizeElement(x, { dx, dy, handle: g.resize, width, alt: e.altKey })
                                  : { ...x, x: x.x + snapped.x - box.x, y: x.y + snapped.y - box.y },
                            ),
                            false,
                          );
                        }}
                        onPointerUp={() => {
                          setDragging(null);
                          range.current = null;
                          setMarquee(null);
                          setGuides([]);
                          if (gesture.current && !gesture.current.moved && gesture.current.clickSelection)
                            setSelected(gesture.current.clickSelection, true);
                          if (gesture.current?.moved) {
                            history.record(gesture.current.before);
                          }
                          gesture.current = null;
                        }}
                      >
                        {guides.map((g, i) => (
                          <div
                            key={i}
                            className={`flow-guide ${g.axis}`}
                            style={g.axis === 'x' ? { left: g.value } : { top: g.value }}
                          />
                        ))}
                        {marquee && (
                          <div
                            className="flow-selection flow-marquee"
                            style={{ left: marquee.x, top: marquee.y, width: marquee.w, height: marquee.h }}
                          />
                        )}
                        {selected.length > 1 &&
                          (() => {
                            const els = active.elements.filter((e) => selected.includes(e.id));
                            if (!els.length) return null;
                            const box = bounds(els);
                            return (
                              <div
                                className="flow-selection"
                                style={{ left: box.x, top: box.y, width: box.w, height: box.h }}
                              >
                                <span>
                                  요소 {els.length}개{' '}
                                  {els[0]?.group && els.every((e) => e.group === els[0]?.group)
                                    ? '· 묶음'
                                    : ''}
                                </span>
                              </div>
                            );
                          })()}
                        {!active.elements.length && (
                          <div className="flow-empty">
                            {edit
                              ? '요소가 없습니다. 왼쪽 "요소 추가"에서 넣고 끌어서 놓으세요.'
                              : '아직 요소가 없습니다.'}
                          </div>
                        )}
                        {active.elements.map((e) => (
                          <div
                            key={e.id}
                            data-linked-element={
                              linkedElement === e.id && selected.includes(e.id) ? '' : undefined
                            }
                            className={`flow-element kind-${e.kind} look-${e.look ?? 'normal'} ${selected.includes(e.id) || hover === e.id ? 'selected' : ''}`}
                            style={{
                              left: e.x,
                              top: e.y,
                              width: e.w,
                              height: e.h,
                              opacity: dragging === e.id ? 0.92 : undefined,
                              cursor: edit
                                ? e.pinned
                                  ? 'default'
                                  : 'move'
                                : e.annotated
                                  ? 'pointer'
                                  : 'default',
                            }}
                            tabIndex={edit || e.annotated ? 0 : -1}
                            role={edit || e.annotated ? 'button' : undefined}
                            onKeyDown={(event) => {
                              if (!edit && e.annotated && (event.key === 'Enter' || event.key === ' ')) {
                                event.preventDefault();
                                event.currentTarget.click();
                              }
                            }}
                            aria-label={e.label}
                            onMouseEnter={() => {
                              if (edit || e.annotated) setHover(e.id);
                            }}
                            onMouseLeave={() => setHover(null)}
                            onFocus={() => {
                              if (edit || e.annotated) setHover(e.id);
                            }}
                            onBlur={(event) => {
                              if (
                                !(event.relatedTarget instanceof Node) ||
                                !event.currentTarget.contains(event.relatedTarget)
                              )
                                setHover(null);
                            }}
                            onPointerDown={(event) => {
                              if (!edit) return;
                              event.stopPropagation();
                              const ids = e.group
                                ? active.elements.filter((x) => x.group === e.group).map((x) => x.id)
                                : [e.id];
                              if (event.shiftKey)
                                setSelected(
                                  selected.includes(e.id)
                                    ? selected.filter((x) => !ids.includes(x))
                                    : [...selected, ...ids],
                                );
                              else if (!selected.includes(e.id)) setSelected(ids);
                              if (e.pinned) {
                                if (!event.shiftKey) setSelected(ids, true);
                                gesture.current = null;
                                return;
                              }
                              gesture.current = {
                                x: event.clientX,
                                y: event.clientY,
                                elements: structuredClone(active.elements),
                                before: structuredClone(flowBlocks(flow)),
                                resize: '',
                                selected: selected.includes(e.id) ? selected : ids,
                                moved: false,
                                clickSelection: event.shiftKey ? undefined : ids,
                              };
                              event.currentTarget.setPointerCapture(event.pointerId);
                            }}
                            onClick={() => {
                              if (edit || !e.annotated) return;
                              const to = e.actions[0]?.to;
                              if (
                                to &&
                                to !== screen.screenId &&
                                flow.screens.some((s) => s.screen.screenId === to)
                              )
                                navigate(to);
                              else {
                                setSelected([e.id], true);
                                setCollapsed(false);
                                setTab('info');
                              }
                            }}
                          >
                            <div className="flow-element-paint">
                              <FlowElementVisual element={e} />
                            </div>
                            {edit && selected.length === 1 && selected[0] === e.id && (
                              <>
                                {dragging !== e.id && (
                                  <div
                                    role="group"
                                    aria-label="요소 도구"
                                    className="flow-floating"
                                    style={{ top: e.y < 44 ? e.h + 8 : -38 }}
                                    onPointerDown={(event) => event.stopPropagation()}
                                  >
                                    <button
                                      aria-label="앞으로 가져오기"
                                      onClick={() =>
                                        replaceElements([...active.elements.filter((x) => x.id !== e.id), e])
                                      }
                                    >
                                      <FlowToolIcon name="front" />
                                    </button>
                                    <button
                                      aria-label="뒤로 보내기"
                                      onClick={() =>
                                        replaceElements([e, ...active.elements.filter((x) => x.id !== e.id)])
                                      }
                                    >
                                      <FlowToolIcon name="back" />
                                    </button>
                                    <button aria-label="복제" onClick={() => duplicate()}>
                                      <FlowToolIcon name="copy" />
                                    </button>
                                    <button aria-label="삭제" onClick={remove}>
                                      <FlowToolIcon name="delete" />
                                    </button>
                                  </div>
                                )}
                                {dragging === e.id && (
                                  <small className="flow-coordinates">
                                    {e.x} · {e.y} · {e.w}×{e.h}
                                  </small>
                                )}
                              </>
                            )}

                            {e.annotated && (
                              <div className={`flow-markers ${markers || edit ? 'always' : ''}`}>
                                <b>{numbers.findIndex((n) => n.id === e.id) + 1}</b>
                                <button
                                  aria-label={`${e.label} 설명`}
                                  onPointerDown={(event) => event.stopPropagation()}
                                  onClick={(event) => {
                                    event.stopPropagation();
                                    setSelected([e.id], true);
                                    setCollapsed(false);
                                    setTab('info');
                                  }}
                                >
                                  <FlowIcon name="info" size={12} />
                                </button>
                                {e.actions[0]?.to && dragging !== e.id && <small>→ {e.actions[0].to}</small>}
                              </div>
                            )}
                            {dragging !== e.id &&
                              threads.some(
                                (t) =>
                                  t.status !== 'resolved' &&
                                  t.anchor.kind !== 'document' &&
                                  t.anchor.blockId === e.id,
                              ) && (
                                <span className="flow-comment-count">
                                  <FlowCommentBadge
                                    count={
                                      threads.filter(
                                        (t) =>
                                          t.status !== 'resolved' &&
                                          t.anchor.kind !== 'document' &&
                                          t.anchor.blockId === e.id,
                                      ).length
                                    }
                                  />
                                </span>
                              )}
                            {edit && dragging !== e.id && warnings(flow, active, e)[0] && (
                              <small className="flow-warning">{warnings(flow, active, e)[0]}</small>
                            )}
                            {edit && selected.length === 1 && selected[0] === e.id && !e.pinned && (
                              <>
                                {['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'].map((handle) => (
                                  <button
                                    key={handle}
                                    className={`flow-resize handle-${handle}`}
                                    aria-label={`크기 조절 ${handle}`}
                                    onPointerDown={(event) => {
                                      event.stopPropagation();
                                      gesture.current = {
                                        x: event.clientX,
                                        y: event.clientY,
                                        elements: structuredClone(active.elements),
                                        before: structuredClone(flowBlocks(flow)),
                                        resize: handle,
                                        selected: [e.id],
                                        moved: false,
                                      };
                                      event.currentTarget.setPointerCapture(event.pointerId);
                                    }}
                                  />
                                ))}
                              </>
                            )}
                          </div>
                        ))}
                        {edit && (
                          <div className="flow-boundary" style={{ top: height }}>
                            <span>여기까지 첫 화면 ({height}px), 아래는 스크롤</span>
                          </div>
                        )}
                      </div>
                      {!edit && active.elements.some((e) => e.y + e.h > height) && (
                        <div className="flow-scroll-hint">
                          <span>↓ 아래로 스크롤해 더 보기</span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
                <p className="flow-frame-caption">
                  {frameName} · {width}×{height} · {Math.round(scale * 100)}%{' '}
                  {screen.frame === null ? '· 문서 기본값' : ''}
                </p>
              </div>
            )}
          </div>
        </section>
        {collapsed ? (
          <button className="flow-reopen" onClick={() => setCollapsed(false)}>
            <FlowIcon name="panel" />
            설명·댓글
            <FlowCommentBadge
              count={
                threads.filter((t) => t.status !== 'resolved' && targets.get(t.id) !== 'screenDeleted').length
              }
            />
          </button>
        ) : (
          <aside className="flow-properties" aria-label="설명과 댓글">
            <div className="flow-panel-header">
              <div className="flow-segmented">
                <button aria-pressed={tab === 'info'} onClick={() => setTab('info')}>
                  {edit ? '속성' : '설명'}
                </button>
                <button aria-pressed={tab === 'comments'} onClick={() => setTab('comments')}>
                  댓글{' '}
                  {
                    threads.filter((t) => t.status !== 'resolved' && targets.get(t.id) !== 'screenDeleted')
                      .length
                  }
                </button>
              </div>
              <button
                className="flow-panel-icon"
                disabled={!commentable}
                aria-label="문서에 댓글"
                onClick={() => setTab('comments')}
              >
                <FlowIcon name="plus" />
              </button>
              <button className="flow-panel-icon" aria-label="패널 접기" onClick={() => setCollapsed(true)}>
                <FlowIcon name="panel" />
              </button>
            </div>
            <div className="flow-panel-body">
              <div hidden={tab !== 'info'}>
                <FlowSelectionLink screenId={screen.id} />
                {element && <FlowSelectionLink screenId={screen.id} elementId={element.id} />}
                {selected.length > 0 && (
                  <button className="flow-back-detail" onClick={() => setSelected([], true)}>
                    <FlowIcon name="left" size={14} />
                    {selected.length > 1 ? '선택 풀고 ' : ''}
                    {screen.screenId} {screen.name} 화면 {edit ? '속성' : '설명으로'}
                  </button>
                )}
                {edit && selected.length > 1 && (
                  <FlowMultiInspector
                    elements={active.elements.filter((e) => selected.includes(e.id))}
                    onAlign={(operation) =>
                      replaceElements(alignElements(active.elements, selected, operation))
                    }
                    onGroup={() => onPatch({ group: id() })}
                    onUngroup={() => onPatch({ group: null })}
                    onDuplicate={() => duplicate()}
                    onDelete={remove}
                  />
                )}
                {edit && element ? (
                  <FlowElementInspector
                    number={numbers.findIndex((e) => e.id === element.id) + 1}
                    element={element}
                    flow={flow}
                    width={width}
                    height={height}
                    onPatch={onPatch}
                    onUploadImage={uploadImage}
                    onLayer={(front) =>
                      replaceElements(
                        front
                          ? [...active.elements.filter((e) => e !== element), element]
                          : [element, ...active.elements.filter((e) => e !== element)],
                      )
                    }
                    onDuplicate={() => duplicate()}
                    onDelete={remove}
                  />
                ) : edit && selected.length === 0 ? (
                  <FlowScreenInspector
                    flow={flow}
                    active={active}
                    onScreen={patchScreen}
                    onSettings={(patch) =>
                      onCommit({ ...flow, settings: { ...flow.settings, ...patch } }, false)
                    }
                    onMove={(direction) => {
                      const screens = [...flow.screens];
                      const index = screens.indexOf(active);
                      screens.splice(index, 1);
                      screens.splice(index + direction, 0, active);
                      onCommit({ ...flow, screens });
                    }}
                    onDuplicate={() => addScreen(true)}
                    onDelete={() => setConfirmation('screen')}
                  />
                ) : element ? (
                  <>
                    <div className="flow-detail">
                      <div className="flow-detail-title">
                        {element.annotated && (
                          <span className="flow-number">
                            {numbers.findIndex((e) => e.id === element.id) + 1}
                          </span>
                        )}
                        <h2>{element.label}</h2>
                        <span className="flow-kind-chip">{KINDS[element.kind]}</span>
                      </div>
                      {element.desc ? (
                        <FlowRichText className="flow-description" text={element.desc} />
                      ) : (
                        <p className="flow-description">(설명 없음)</p>
                      )}
                      {element.actions.length > 0 && (
                        <>
                          <h3>이동</h3>
                          <ul>
                            {element.actions.map((action, index) => (
                              <li key={index}>
                                <span>{action.when}</span>
                                <button onClick={() => action.to && navigate(action.to)}>
                                  {action.to ?? '이동 없음'}{' '}
                                  {action.to &&
                                    (flow.screens.find((s) => s.screen.screenId === action.to)?.screen.name ??
                                      '(삭제된 화면)')}
                                  <span>({ACTION_MODES[action.mode]})</span>
                                  <FlowIcon name="right" size={12} />
                                </button>
                              </li>
                            ))}
                          </ul>
                        </>
                      )}
                      {element.states.length > 0 && (
                        <>
                          <h3>상태</h3>
                          <div className="flow-detail-states">
                            {element.states.map((state) => (
                              <span key={state}>{state}</span>
                            ))}
                          </div>
                        </>
                      )}
                    </div>
                  </>
                ) : edit ? null : (
                  <>
                    <FlowOverview
                      flow={flow}
                      active={active}
                      hover={hover}
                      threads={threads}
                      expanded={expanded}
                      onExpand={(elementId) =>
                        setExpanded(
                          expanded.includes(elementId)
                            ? expanded.filter((x) => x !== elementId)
                            : [...expanded, elementId],
                        )
                      }
                      onSelect={(e) => setSelected([e.id], true)}
                      onHover={setHover}
                      onNavigate={navigate}
                    />
                  </>
                )}
                {edit && confirmation && current === screen.id && (
                  <div className="flow-delete-confirm" role="alert">
                    <strong>
                      {confirmation === 'screen'
                        ? `${screen.screenId} ${screen.name} 화면을 삭제할까요?`
                        : selected.length === 1
                          ? `"${element?.label ?? '요소'}" 요소를 삭제할까요?`
                          : `요소 ${selected.length}개를 삭제할까요?`}
                    </strong>
                    <span>{deletionNotice}</span>
                    <div>
                      <button onClick={() => setConfirmation(null)}>취소</button>
                      <button
                        onClick={() => {
                          if (confirmation === 'screen') {
                            if (flow.screens.length < 2) return;
                            const remaining = flow.screens.filter((s) => s !== active);
                            const fallback =
                              remaining[Math.min(flow.screens.indexOf(active), remaining.length - 1)];
                            onCommit({ ...flow, screens: remaining });
                            if (fallback) {
                              select(fallback.screen.id, [], true);
                              const past = trail.filter((id) => id !== screen.screenId);
                              setTrail(
                                (past.at(-1) === fallback.screen.screenId ? past.slice(0, -1) : past).slice(
                                  -7,
                                ),
                              );
                            }
                            setHover(null);
                          } else {
                            replaceElements(active.elements.filter((e) => !selected.includes(e.id)));
                            setSelected([], true);
                          }
                          setConfirmation(null);
                        }}
                      >
                        삭제
                      </button>
                    </div>
                  </div>
                )}
                {reanchoring && (
                  <button onClick={() => onReanchor({ kind: 'block', blockId: element?.id ?? screen.id })}>
                    여기에 다시 달기
                  </button>
                )}
              </div>
              {tab === 'comments'
                ? commentRail()
                : (selected.length > 0 || contextThreads.length > 0) && (
                    <section className="flow-context-comments">
                      <h3>
                        {element
                          ? `이 요소의 댓글 ${contextThreads.length}`
                          : selected.length > 1
                            ? `이 화면 전체에 단 댓글 ${contextThreads.length}`
                            : '이 화면 전체에 단 댓글'}
                      </h3>
                      {contextThreads.length ? (
                        commentRail(element?.id ?? screen.id)
                      ) : (
                        <p>
                          {selected.length > 1
                            ? '여러 요소를 고른 상태라 아래에서 남기면 이 화면에 붙습니다.'
                            : '아직 댓글이 없습니다. 아래에서 남기면 이 요소에 붙습니다.'}
                        </p>
                      )}
                    </section>
                  )}
            </div>
            <fieldset className="flow-composer" disabled={!commentable}>
              <label>
                {tab === 'comments'
                  ? '문서에 댓글'
                  : element
                    ? `${element.label} 요소에 댓글`
                    : `${screen.screenId} ${screen.name} 화면에 댓글`}
              </label>
              <CommentEditor
                initial=""
                key={tab === 'comments' ? 'document' : (element?.id ?? screen.id)}
                mentionLogins={mentionLogins}
                placeholder={
                  commentable
                    ? '댓글을 남기세요. @로 멘션 (⌘Enter)'
                    : '읽기 권한으로는 댓글을 달 수 없습니다.'
                }
                onSubmit={(body) =>
                  onStartComment(
                    tab === 'comments'
                      ? { kind: 'document' }
                      : { kind: 'block', blockId: element?.id ?? screen.id },
                    body,
                  )
                }
                submitLabel="댓글"
              />
            </fieldset>
          </aside>
        )}
      </div>
    </section>
  );
}
