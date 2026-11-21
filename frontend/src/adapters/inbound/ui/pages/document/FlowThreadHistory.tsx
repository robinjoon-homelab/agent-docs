import { useFlowCommentTargets } from './useFlowCommentTargets';
import { useDocumentPermission } from './DocumentPermissionContext';
import { canComment } from '../../../../../domain/space/permission';
import { FlowDeviceFrame } from './FlowDeviceFrame';
import { FlowIcon, FlowCommentBadge } from './FlowIcon';
import { flowPreview } from './flowPreview';
import type { ReactNode } from 'react';
import { FlowElementVisual } from './FlowElementVisual';
import { useEffect, useRef, useState } from 'react';
import { useStageArea } from './useStageArea';
import type { Block } from '../../../../../domain/document/block';
import type { Thread } from '../../../../../domain/thread/thread';
import {
  readFlow,
  frameSize,
  flowTarget,
  flowDifferences,
  numbered,
  anchoredScreenId,
} from '../../../../../domain/flow/flow';
import { useServices } from '../../../../../config/ServicesContext';
import { errorMessage } from '../../common/States';
const TARGET_LABEL = { ok: '', changed: '바뀜', elementDeleted: '요소 삭제됨', screenDeleted: '화면 삭제됨' };
export function FlowThreadHistory({
  thread,
  blocks,
  onSnapshot,
  onSelect,
}: {
  thread: Thread;
  blocks: readonly Block[];
  onSelect: (blockId: string) => void;
  onSnapshot: (blocks: readonly Block[], blockId: string, revision: number) => void;
}) {
  const { documents } = useServices();
  const [past, setPast] = useState<readonly Block[] | null>(null);
  const [error, setError] = useState('');
  const revision = thread.anchoredRevision;
  useEffect(() => {
    let active = true;
    if (revision !== undefined)
      void documents
        .revision(thread.documentId, revision)
        .then((s) => {
          if (active) setPast(s.blocks);
        })
        .catch((e: unknown) => {
          if (active) setError(errorMessage(e));
        });
    return () => {
      active = false;
    };
  }, [documents, thread.documentId, revision]);
  if (thread.anchor.kind === 'document') return <span>문서 전체</span>;
  const blockId = thread.anchor.blockId;
  const current = readFlow(blocks),
    old = past ? readFlow(past) : null;
  const target = past ? flowTarget(past, blocks, blockId) : 'ok';
  const currentScreen = current.screens.find(
    (s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId),
  );
  const originalScreen = old?.screens.find(
    (s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId),
  );
  const screen =
    target === 'elementDeleted' || target === 'screenDeleted'
      ? (originalScreen ?? currentScreen)
      : (currentScreen ?? originalScreen);
  const element = screen?.elements.find((e) => e.id === blockId);
  return (
    <div className="flow-thread-target">
      <button
        onClick={() => {
          if ((target === 'elementDeleted' || target === 'screenDeleted') && past && revision !== undefined)
            onSnapshot(past, blockId, revision);
          else onSelect(blockId);
        }}
      >
        {screen?.screen.screenId} · {element?.label ?? `${screen?.screen.name ?? ''} · 화면 전체`}
      </button>
      <span className={`flow-target-flag kind-${target}`}>{TARGET_LABEL[target]}</span>
      {target === 'changed' && <p>댓글을 단 뒤 이 {element ? '요소' : '화면'}가 바뀌었습니다.</p>}
      {target === 'elementDeleted' && <p>이 요소는 삭제됐습니다. 이 댓글은 댓글 사이드바에만 남습니다.</p>}
      {target === 'screenDeleted' && <p>이 화면은 삭제됐습니다. 삭제된 화면 탭에서만 보입니다.</p>}
      {target !== 'ok' && past && revision !== undefined && (
        <button onClick={() => onSnapshot(past, blockId, revision)}>
          {target === 'elementDeleted'
            ? '삭제된 요소 보기'
            : target === 'screenDeleted'
              ? '삭제된 화면 보기'
              : '댓글 단 시점 보기'}{' '}
          · rev {revision}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
export function FlowSnapshot({
  blocks,
  current,
  blockId,
  revision,
  onClose,
  onSelectScreen,
  threadCard,
  threads,
  onComments,
  onInfo,
  onPanelCollapse,
}: {
  blocks: readonly Block[];
  current: readonly Block[];
  blockId: string;
  revision: number;
  onClose: () => void;
  onSelectScreen: (blockId: string) => void;
  threadCard: ReactNode;
  threads: readonly Thread[];
  onComments: () => void;
  onInfo: () => void;
  onPanelCollapse: (collapsed: boolean) => void;
}) {
  const flow = readFlow(blocks),
    now = readFlow(current);
  const targets = useFlowCommentTargets(threads, current);
  const commentable = canComment(useDocumentPermission());
  const [collapsed, setCollapsed] = useState(false);
  const stageRef = useRef<HTMLDivElement>(null);
  const stageArea = useStageArea(stageRef, 0, 30);
  const openCount = threads.filter(
    (t) => t.status !== 'resolved' && targets.get(t.id) !== 'screenDeleted',
  ).length;
  const screen = flow.screens.find(
    (s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId),
  );
  if (!screen) return <p>댓글 당시 화면을 찾을 수 없습니다.</p>;
  const [name, width, height] = frameSize(flow, screen.screen);
  const target = flowTarget(blocks, current, blockId);
  const before = blocks.find((b) => b.id === blockId);
  const differences = flowDifferences(blocks, current, blockId);
  const numberedElements = numbered(screen.elements);
  const frame = screen.screen.frame ?? flow.settings.frame;
  const pad = frame.startsWith('phone') ? 8 : frame.startsWith('tablet') ? 12 : frame === 'modal' ? 0 : 1;
  const percent = Math.floor(
    Math.min(
      1,
      740 / (width + pad * 2),
      690 / (height + pad * 2 + (['laptop', 'desktop'].includes(frame) ? 30 : 0)),
    ) * 100,
  );
  return (
    <section className={`flow-document${flowPreview().tall ? ' flow-board-tall' : ''}`}>
      <nav className="flow-strip">
        <button className="flow-strip-arrow" onClick={onClose}>
          <FlowIcon name="left" />
        </button>
        <div className="flow-screen-buttons">
          {now.screens.map((s) => (
            <button
              key={s.screen.id}
              aria-pressed={s.screen.id === screen.screen.id}
              onClick={() => onSelectScreen(s.screen.id)}
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
                        now.screens,
                        targets.parents.get(t.id),
                      ) === s.screen.id,
                  ).length
                }
              />
            </button>
          ))}
          {target === 'screenDeleted' && (
            <span className="flow-deleted-screen" aria-current="true">
              {screen.screen.screenId} {screen.screen.name} · 삭제됨
            </span>
          )}
        </div>
        <button className="flow-strip-arrow" onClick={onClose}>
          <FlowIcon name="right" />
        </button>
      </nav>
      <div className="flow-layout">
        <section className="flow-preview">
          <div className="flow-snapshot-banner">
            <strong>rev {revision} 시점</strong>
            <span>
              {target === 'screenDeleted'
                ? '삭제된 화면을 댓글이 달린 때의 모습으로 보여 줍니다 (읽기 전용)'
                : '댓글이 달린 때의 화면입니다 (읽기 전용)'}
            </span>
            <button onClick={onClose}>
              {target === 'changed' && before?.type === 'flowElement' ? '현재 요소 보기' : '현재 화면으로'}
            </button>
          </div>
          <div className="flow-stage-content" ref={stageRef}>
            <div className="flow-stage">
              <FlowDeviceFrame
                area={stageArea}
                frame={screen.screen.frame ?? flow.settings.frame}
                width={width}
                height={height}
                title={`agent-docs · ${screen.screen.screenId} ${screen.screen.name}`}
              >
                <div
                  className="flow-canvas"
                  style={{ width, minHeight: Math.max(height, ...screen.elements.map((e) => e.y + e.h)) }}
                >
                  {screen.elements.map((e) => {
                    const state = flowTarget(blocks, current, e.id);
                    return (
                      <div
                        key={e.id}
                        className="flow-element flow-snapshot-element"
                        style={{
                          left: e.x,
                          top: e.y,
                          width: e.w,
                          height: e.h,
                          outline: e.id === blockId ? '2px solid var(--flow-history-solid)' : undefined,
                        }}
                      >
                        <div className="flow-element-paint">
                          <FlowElementVisual element={e} />
                        </div>
                        {e.annotated && (
                          <div className="flow-markers always">
                            <b>{numberedElements.findIndex((el) => el.id === e.id) + 1}</b>
                          </div>
                        )}
                        {(e.id === blockId || state !== 'ok') && (
                          <span className={`flow-snapshot-tag${e.id === blockId ? ' anchored' : ''}`}>
                            {e.id === blockId ? '댓글 위치' : ''}
                            {state === 'changed' ? ' · 이후 바뀜' : state !== 'ok' ? ' · 지금은 삭제됨' : ''}
                          </span>
                        )}
                      </div>
                    );
                  })}
                </div>
              </FlowDeviceFrame>
              <p className="flow-frame-caption">
                {name} · {width}×{height} · {percent}% {screen.screen.frame === null ? '· 문서 기본값' : ''}
              </p>
            </div>
          </div>
        </section>
        {collapsed ? (
          <button
            className="flow-reopen"
            onClick={() => {
              setCollapsed(false);
              onPanelCollapse(false);
            }}
          >
            <FlowIcon name="panel" />
            설명·댓글
            <FlowCommentBadge count={openCount} />
          </button>
        ) : (
          <aside className="flow-properties">
            <div className="flow-panel-header">
              <div className="flow-segmented">
                <button aria-pressed={flowPreview().tab !== 'comments'} onClick={onInfo}>
                  설명
                </button>
                <button aria-pressed={flowPreview().tab === 'comments'} onClick={onComments}>
                  댓글 {openCount}
                </button>
              </div>
              <button
                className="flow-panel-icon"
                aria-label="문서에 댓글"
                disabled={!commentable}
                onClick={onComments}
              >
                <FlowIcon name="plus" />
              </button>
              <button
                className="flow-panel-icon"
                aria-label="패널 접기"
                onClick={() => {
                  setCollapsed(true);
                  onPanelCollapse(true);
                }}
              >
                <FlowIcon name="panel" />
              </button>
            </div>
            <div className="flow-panel-body">
              <button className="flow-back-detail" onClick={onClose}>
                ‹{' '}
                {target === 'changed' && before?.type === 'flowElement' ? '현재 요소 보기' : '현재 화면으로'}
              </button>
              <div className="flow-snapshot-heading">
                <span>rev {revision}</span>댓글이 달린 때
              </div>
              <h2>
                {screen.screen.screenId} ·{' '}
                {before?.type === 'flowElement' ? before.label : screen.screen.name}
              </h2>
              <p className="flow-description">
                {target === 'ok'
                  ? '댓글을 단 뒤 바뀐 것이 없습니다.'
                  : target === 'elementDeleted'
                    ? '이 요소는 지금 화면에 없습니다. 가운데는 댓글이 달린 때의 화면이고, 주황 테두리가 삭제된 요소입니다.'
                    : target === 'screenDeleted'
                      ? '이 화면은 삭제됐습니다. 가운데는 댓글이 달린 때의 화면입니다.'
                      : '댓글을 단 뒤 바뀌었습니다. 댓글이 어떻게 반영됐는지 아래에서 그때와 지금을 비교하세요.'}
              </p>
              <h3>그때와 지금</h3>
              {differences.length ? (
                differences.map((difference, index) => (
                  <div className="flow-comparison" key={index}>
                    <strong>{difference.field}</strong>
                    <div>
                      <span>그때</span>
                      <p>{difference.before}</p>
                    </div>
                    <div>
                      <span>지금</span>
                      <p>{difference.after}</p>
                    </div>
                  </div>
                ))
              ) : (
                <p>차이가 없습니다.</p>
              )}
              <section className="flow-context-comments">
                <h3>이 댓글</h3>
                {threadCard}
              </section>
            </div>
          </aside>
        )}
      </div>
    </section>
  );
}
