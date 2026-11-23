import { useFlowCommentTargets } from './useFlowCommentTargets';
import { useDocumentPermission } from './DocumentPermissionContext';
import { canComment } from '../../../../../domain/space/permission';
import { FlowDeviceFrame } from './FlowDeviceFrame';
import { FlowRichText } from './FlowRichText';
import { FlowIcon, FlowCommentBadge } from './FlowIcon';
import { flowPreview } from './flowPreview';
import { FlowElementVisual } from './FlowElementVisual';
import { useRef, useState } from 'react';
import { useStageArea } from './useStageArea';
import type { Block } from '../../../../../domain/document/block';
import type { Thread } from '../../../../../domain/thread/thread';
import { readFlow, frameSize, numbered } from '../../../../../domain/flow/flow';
import { useServices } from '../../../../../config/ServicesContext';
import { errorMessage } from '../../common/States';
import {
  flowAnchorTarget,
  flowAnchorDifferences,
  type FlowAnchorTarget,
} from '../../../../../domain/flow/flowAnchorComparison';
import { flowAnchorLocation } from '../../../../../domain/flow/flowAnchorLocation';
import { DesignHistoricalSnapshot, type HistoricalSnapshotProps } from './design/DesignHistoricalSnapshot';

export function FlowThreadHistory({
  thread,
  blocks,
  past,
  target: status,
  currentVersion,
  onSelect,
  onSnapshot,
}: {
  thread: Thread;
  blocks: readonly Block[];
  past?: readonly Block[];
  target?: FlowAnchorTarget;
  currentVersion?: number;
  onSelect: (id: string, viewedStateId?: string) => void;
  onSnapshot: (blocks: readonly Block[], blockId: string, revision: number) => void;
}) {
  const { documents } = useServices();
  const [error, setError] = useState('');
  if (thread.anchor.kind === 'document') return <span>문서 전체</span>;
  const id = thread.anchor.blockId,
    revision = thread.anchoredRevision;
  const target = status ?? (past ? flowAnchorTarget(past, blocks, id, thread, currentVersion) : 'ok');
  const deleted = target === 'screenDeleted' || target === 'elementDeleted';
  const viewedStateId = thread.anchor.kind === 'block' ? thread.anchor.viewedStateId : undefined;
  const current = flowAnchorLocation(blocks, id, viewedStateId);
  const original = past && flowAnchorLocation(past, id, viewedStateId);
  const context = deleted ? (original ?? current) : (current ?? original);
  const removedState =
    target === 'elementDeleted' &&
    original?.stateBlockId &&
    flowAnchorLocation(blocks, original.stateBlockId)?.screenBlockId !== original.screenBlockId;
  const kind =
    removedState || context?.kind === 'state' ? '상태' : context?.kind === 'screen' ? '화면' : '요소';
  const flag =
    target === 'changed'
      ? '바뀜'
      : target === 'screenDeleted'
        ? '화면 삭제됨'
        : target === 'elementDeleted'
          ? `${kind} 삭제됨`
          : '';
  const openHistory = async () => {
    if (revision === undefined) return;
    try {
      const snapshot = past ?? (await documents.revision(thread.documentId, revision)).blocks;
      setError('');
      onSnapshot(snapshot, id, revision);
    } catch (e) {
      setError(errorMessage(e));
    }
  };
  return (
    <div className="flow-thread-target">
      <button onClick={() => (deleted ? void openHistory() : onSelect(id, viewedStateId))}>
        {context
          ? `${context.code}${context.kind === 'element' && context.stateName ? ' · ' + context.stateName : ''} · ${context.name}${kind === '화면' ? ' · 화면 전체' : ''}`
          : '댓글 위치'}
      </button>
      <span className={`flow-target-flag kind-${target}`}>{flag}</span>
      {target === 'changed' && (
        <p>
          댓글을 단 뒤 이 {kind}
          {kind === '화면' ? '이' : '가'} 바뀌었습니다.
        </p>
      )}
      {target === 'elementDeleted' && <p>이 {kind}는 삭제됐습니다. 이 댓글은 댓글 사이드바에만 남습니다.</p>}
      {target === 'screenDeleted' && <p>이 화면은 삭제됐습니다. 삭제된 화면 탭에서만 보입니다.</p>}
      {target !== 'ok' && revision !== undefined && (
        <button onClick={() => void openHistory()}>
          {deleted ? `삭제된 ${target === 'screenDeleted' ? '화면' : kind} 보기` : '댓글 단 시점 보기'} · rev{' '}
          {revision}
        </button>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}

type FlowSnapshotProps = HistoricalSnapshotProps & {
  threads: readonly Thread[];
  onComments: () => void;
  onInfo: () => void;
  onPanelCollapse: (collapsed: boolean) => void;
};
export function FlowSnapshot(props: FlowSnapshotProps) {
  return props.blocks[0]?.type === 'flowDesign' ? (
    <DesignHistoricalSnapshot {...props} />
  ) : (
    <LegacyFlowSnapshot {...props} />
  );
}
function LegacyFlowSnapshot({
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
  thread,
  currentVersion,
}: FlowSnapshotProps) {
  const flow = readFlow(blocks);
  const currentScreens = current
    .filter((b) => b.type === 'flowScreen' || (b.type === 'flowDesign' && b.design.kind === 'screen'))
    .flatMap((b) => {
      const location = flowAnchorLocation(current, b.id);
      return location ? [location] : [];
    });
  const targets = useFlowCommentTargets(threads, current, currentVersion);
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
  const target = flowAnchorTarget(blocks, current, blockId, thread, currentVersion);
  const before = blocks.find((b) => b.id === blockId);
  const differences = flowAnchorDifferences(blocks, current, blockId, target);
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
          {currentScreens.map((s) => (
            <button
              key={s.screenBlockId}
              aria-pressed={s.screenBlockId === screen.screen.id}
              onClick={() => onSelectScreen(s.screenBlockId)}
            >
              <span>{s.code}</span>
              {s.screenName}
              <FlowCommentBadge
                count={
                  threads.filter(
                    (t) =>
                      t.status !== 'resolved' &&
                      (t.anchor.kind !== 'document' &&
                        (flowAnchorLocation(current, t.anchor.blockId)?.screenBlockId ??
                          targets.parents.get(t.id))) === s.screenBlockId,
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
            {screen.screen.frame === 'note' ? (
              <article className="flow-note">
                <FlowRichText text={screen.screen.desc} />
                <p className="flow-frame-caption">글 페이지 · rev {revision} 시점</p>
              </article>
            ) : (
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
                      const state = e.id === blockId ? target : flowAnchorTarget(blocks, current, e.id);
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
                              {state === 'changed'
                                ? ' · 이후 바뀜'
                                : state !== 'ok'
                                  ? ' · 지금은 삭제됨'
                                  : ''}
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
            )}
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
                <p>{target === 'changed' ? '디자인이 바뀌었습니다.' : '차이가 없습니다.'}</p>
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
