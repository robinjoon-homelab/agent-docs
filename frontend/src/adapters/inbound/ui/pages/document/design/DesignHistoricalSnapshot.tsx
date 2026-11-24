import { flowAnchorTarget, flowAnchorDifferences } from '../../../../../../domain/flow/flowAnchorComparison';
import { designBlocks, flowAnchorLocation } from '../../../../../../domain/flow/flowAnchorLocation';
import { useRef, useState, useMemo, type ReactNode } from 'react';
import type { FlowFrame } from '../../../../../../domain/document/block';
import { FRAMES } from '../../../../../../domain/flow/flow';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { asText } from '../../../../../../domain/flow/designValue';
import { FlowScreenStrip } from '../FlowScreenStrip';
import { FlowDeviceFrame } from '../FlowDeviceFrame';
import { FlowCommentBadge, FlowIcon } from '../FlowIcon';
import { useStageArea } from '../useStageArea';
import { useFlowCommentTargets } from '../useFlowCommentTargets';
import type { Thread } from '../../../../../../domain/thread/thread';
import type { Block } from '../../../../../../domain/document/block';
import { FlowRichText } from '../FlowRichText';
import { DesignCanvas } from './DesignCanvas';

export type HistoricalSnapshotProps = {
  thread?: Thread;
  currentVersion?: number;
  blocks: readonly Block[];
  current: readonly Block[];
  blockId: string;
  revision: number;
  onClose: () => void;
  onSelectScreen: (blockId: string, viewedStateId?: string) => void;
  threadCard: ReactNode;
};

export function DesignHistoricalSnapshot({
  blocks,
  current,
  blockId,
  revision,
  currentVersion,
  thread,
  threadCard,
  onClose,
  onSelectScreen,
  threads,
  onInfo,
  onComments,
  onPanelCollapse,
}: HistoricalSnapshotProps & {
  threads: readonly Thread[];
  onInfo: () => void;
  onComments: () => void;
  onPanelCollapse: (collapsed: boolean) => void;
}) {
  const [collapsed, setCollapsed] = useState(false);
  const stage = useRef<HTMLDivElement>(null),
    area = useStageArea(stage, 0, 30);
  const library = useMemo(() => designBlocks(blocks), [blocks]);
  const targets = useFlowCommentTargets(threads, current, currentVersion);

  const viewedStateId = thread?.anchor.kind === 'block' ? thread.anchor.viewedStateId : undefined;
  const where = flowAnchorLocation(blocks, blockId, viewedStateId);
  if (!where)
    return (
      <section>
        <p>댓글 당시 화면을 찾을 수 없습니다.</p>
        <button onClick={onClose}>현재 화면으로</button>
      </section>
    );
  const target = flowAnchorTarget(blocks, current, blockId, thread, currentVersion);
  const differences = flowAnchorDifferences(blocks, current, blockId, target, viewedStateId);
  const currentLocation = flowAnchorLocation(current, blockId, viewedStateId);
  const destination = currentLocation && target !== 'elementDeleted' && target !== 'screenDeleted';
  const label = where.kind === 'screen' ? '화면' : where.kind === 'state' ? '상태' : '요소';
  const visited = library.find((b) => b.id === where.screenBlockId);
  const scene = resolveDesignScene(library, where.screenId, where.stateId);
  const frame = visited?.design.frame ?? library.find((b) => b.design.kind === 'settings')?.design.frame;
  const preset: FlowFrame | 'custom' =
    typeof frame === 'string' && Object.hasOwn(FRAMES, frame) ? (frame as FlowFrame) : 'custom';
  const openThreads = threads.filter((t) => t.status !== 'resolved' && targets.get(t.id) !== 'screenDeleted');
  const screens = current.flatMap((b) => {
    if (b.type !== 'flowScreen' && !(b.type === 'flowDesign' && b.design.kind === 'screen')) return [];
    const info = flowAnchorLocation(current, b.id);
    return info
      ? [
          {
            id: b.id,
            code: info.code,
            name: info.screenName,
            comments: openThreads.filter(
              (t) =>
                t.anchor.kind !== 'document' &&
                (targets.parents.get(t.id) ??
                  flowAnchorLocation(current, t.anchor.blockId)?.screenBlockId) === b.id,
            ).length,
          },
        ]
      : [];
  });
  function fold(value: boolean) {
    setCollapsed(value);
    onPanelCollapse(value);
  }
  return (
    <section className="flow-document design-history">
      <FlowScreenStrip
        screens={screens}
        selected={target === 'screenDeleted' ? undefined : where.screenBlockId}
        onSelect={onSelectScreen}
      >
        {target === 'screenDeleted' && (
          <span className="flow-deleted-screen" aria-current="page">
            {where.code} {where.screenName} · 삭제됨
          </span>
        )}
      </FlowScreenStrip>
      <div className="flow-layout">
        <section className="flow-preview">
          <div className="flow-snapshot-banner">
            <strong>rev {revision} 시점</strong>
            <span>
              {target === 'screenDeleted'
                ? '삭제된 화면을 댓글이 달린 때의 모습으로 보여 줍니다 (읽기 전용)'
                : '댓글이 달린 때의 화면입니다 (읽기 전용)'}
            </span>
            <button onClick={destination ? () => onSelectScreen(blockId, viewedStateId) : onClose}>
              {destination ? `현재 ${label} 보기` : '현재 화면으로'}
            </button>
          </div>
          <div className="flow-stage-content" ref={stage}>
            {frame === 'note' ? (
              <article className="flow-note">
                <FlowRichText text={asText(visited?.design.description) ?? ''} />
              </article>
            ) : (
              <div className="flow-stage">
                <FlowDeviceFrame
                  area={area}
                  frame={preset}
                  width={scene.frame.width}
                  height={scene.frame.height}
                  title={where.code + ' ' + where.screenName}
                >
                  <DesignCanvas
                    scene={scene}
                    blocks={library}
                    screen={where.screenId}
                    state={where.stateId}
                    selection={where.address}
                  />
                </FlowDeviceFrame>
                <p className="flow-frame-caption">
                  {preset === 'custom' ? '직접 입력' : FRAMES[preset][0]} · {scene.frame.width}×
                  {scene.frame.height} · rev {revision} 시점
                </p>
              </div>
            )}
          </div>
        </section>
        {collapsed ? (
          <button className="flow-reopen" onClick={() => fold(false)}>
            <FlowIcon name="panel" />
            설명·댓글 <FlowCommentBadge count={openThreads.length} />
          </button>
        ) : (
          <aside className="flow-properties">
            <div className="flow-panel-header">
              <div className="flow-segmented">
                <button aria-pressed="true" onClick={onInfo}>
                  설명
                </button>
                <button onClick={onComments}>댓글 {openThreads.length}</button>
              </div>
              <button className="flow-panel-icon" aria-label="패널 접기" onClick={() => fold(true)}>
                <FlowIcon name="panel" />
              </button>
            </div>
            <div className="flow-panel-body">
              <div className="flow-snapshot-heading">
                <span>rev {revision}</span>댓글이 달린 때
              </div>
              <h2>
                {where.code} · {where.name}
              </h2>
              {where.stateName && <p>{where.stateName}</p>}
              <p className="flow-description">
                {target === 'screenDeleted'
                  ? '이 화면은 삭제됐습니다. 가운데는 댓글이 달린 때의 화면입니다.'
                  : target === 'elementDeleted'
                    ? '이 요소는 지금 화면에 없습니다. 가운데는 댓글이 달린 때의 화면입니다.'
                    : target === 'changed'
                      ? '댓글을 단 뒤 바뀌었습니다. 아래에서 그때와 지금을 비교하세요.'
                      : '댓글을 단 뒤 바뀐 것이 없습니다.'}
              </p>
              {where.description && <FlowRichText text={where.description} />}
              <h3>그때와 지금</h3>
              {differences.length ? (
                differences.map((d) => (
                  <div className="flow-comparison" key={d.field}>
                    <strong>{d.field}</strong>
                    <div>
                      <span>그때</span>
                      <p>{d.before}</p>
                    </div>
                    <div>
                      <span>지금</span>
                      <p>{d.after}</p>
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
