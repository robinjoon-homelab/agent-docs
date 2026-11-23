import { flowAnchorTarget, flowAnchorDifferences } from '../../../../../../domain/flow/flowAnchorComparison';
import { designBlocks, flowAnchorLocation } from '../../../../../../domain/flow/flowAnchorLocation';
import type { ReactNode } from 'react';
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
}: HistoricalSnapshotProps) {
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
  return (
    <section className="flow-document">
      <div className="flow-snapshot-banner">
        <strong>rev {revision} 시점</strong>
        <span>댓글이 달린 때의 화면입니다 (읽기 전용)</span>
        <button onClick={destination ? () => onSelectScreen(blockId, viewedStateId) : onClose}>
          {destination ? `현재 ${label} 보기` : '현재 화면으로'}
        </button>
      </div>
      <div className="flow-layout">
        <section className="flow-preview">
          <div className="flow-stage-content">
            <DesignCanvas
              blocks={designBlocks(blocks)}
              screen={where.screenId}
              state={where.stateId}
              selection={where.address}
            />
          </div>
        </section>
        <aside className="flow-properties">
          <div className="flow-panel-body">
            <span className="flow-snapshot-heading">
              {where.code} {where.screenName}
              {where.kind === 'element' && where.stateName ? ' · ' + where.stateName : ''} · {label}
            </span>
            <h2>{where.name}</h2>
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
      </div>
    </section>
  );
}
