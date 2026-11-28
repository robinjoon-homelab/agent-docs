import { useState } from 'react';
import { Link } from 'react-router';
import type { Activity } from '../../../../../domain/activity/activity';
import { activityHref } from '../../../../../domain/activity/activity';
import { useServices } from '../../../../../config/ServicesContext';
import { ActorChip } from '../../common/ActorChip';
import { Icon } from '../../common/Icon';
import { RelativeTime } from '../../common/RelativeTime';
import { ErrorBox, Loading } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';
import './recentActivity.css';

const ACTIVITY_LIMIT = 20;
const HOUR_MS = 60 * 60 * 1000;
const KINDS = {
  'document.created': '문서 생성',
  'document.revised': '문서 수정',
  'document.revision_confirmed': '문서 리비전 확정',
  'document.transferred': '문서 이사',
  'document.trashed': '휴지통으로 이동',
  'document.untrashed': '휴지통에서 복원',
  'document.purged': '문서 영구 삭제',
  'thread.opened': '댓글 스레드 열기',
  'comment.added': '댓글 추가',
  'comment.edited': '댓글 수정',
  'comment.deleted': '댓글 삭제',
  'thread.status': '스레드 상태 변경',
  'thread.moved': '스레드 위치 변경',
  'thread.linked': '스레드에 리비전 연결',
  'thread.reaction': '스레드 반응 변경',
  'thread.claim': '스레드 점유 변경',
  'thread.suggest': '수정 제안',
  'thread.suggestion.decide': '수정 제안 처리',
  'asset.uploaded': '첨부 업로드',
  'space.created': '스페이스 생성',
  'space.changed': '스페이스 설정 변경',
};
const ACTOR_KINDS = ['human', 'agent', 'system'] as const;
const ACTOR_LABELS = { human: '사람', agent: '에이전트', system: '시스템' };

function ActivityRows({ entries }: { entries: readonly Activity[] }) {
  return (
    <ul className="list card space-list">
      {entries.map((entry) => {
        const href = activityHref(entry);
        const summary = (
          <span className="space-activity-summary">
            {entry.documentTitle === null ? null : <strong>{entry.documentTitle} </strong>}
            {entry.summary}
          </span>
        );
        return (
          <li key={entry.id} className="space-activity">
            <div className="space-activity-head">
              {entry.actor.kind === 'system' ? (
                <span className="actor">
                  <Icon name="settings" size={16} />
                  <span className="actor-name">시스템</span>
                </span>
              ) : (
                <ActorChip actor={entry.actor} />
              )}
              <span className="faint small space-row-time">
                <RelativeTime iso={entry.at} />
              </span>
            </div>
            {href === null ? summary : <Link to={href}>{summary}</Link>}
          </li>
        );
      })}
    </ul>
  );
}

export function RecentActivity({ spaceSlug }: { spaceSlug: string }) {
  const services = useServices();
  const [kind, setKind] = useState('');
  const [actorKind, setActorKind] = useState<Activity['actor']['kind']>();
  const [hours, setHours] = useState(0);
  const filtered = kind !== '' || actorKind !== undefined || hours !== 0;
  const activity = useLoad(JSON.stringify(['space-home:activity', spaceSlug, kind, actorKind, hours]), () =>
    services.activity.recent(spaceSlug, ACTIVITY_LIMIT, {
      ...(kind === '' ? {} : { kind }),
      ...(actorKind === undefined ? {} : { actorKind }),
      ...(hours === 0
        ? {}
        : { since: new Date(services.clock.now().getTime() - hours * HOUR_MS).toISOString() }),
    }),
  );

  function resetFilters() {
    setKind('');
    setActorKind(undefined);
    setHours(0);
  }

  return (
    <section className="section" aria-labelledby="space-activity">
      <h2 className="section-title" id="space-activity">
        최근 활동
      </h2>
      <div className="recent-activity-filters" role="group" aria-label="최근 활동 필터">
        <label className="field">
          <span className="field-label">활동 종류</span>
          <select className="select" value={kind} onChange={(event) => setKind(event.currentTarget.value)}>
            <option value="">모든 종류</option>
            {Object.entries(KINDS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">작업 주체</span>
          <select
            className="select"
            value={actorKind ?? ''}
            onChange={(event) => setActorKind(ACTOR_KINDS.find((kind) => kind === event.currentTarget.value))}
          >
            <option value="">모든 주체</option>
            {ACTOR_KINDS.map((kind) => (
              <option key={kind} value={kind}>
                {ACTOR_LABELS[kind]}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">작업 시기</span>
          <select
            className="select"
            value={hours}
            onChange={(event) => setHours(Number(event.currentTarget.value))}
          >
            <option value={0}>전체 기간</option>
            <option value={24}>최근 24시간</option>
            <option value={7 * 24}>최근 7일</option>
            <option value={30 * 24}>최근 30일</option>
          </select>
        </label>
        <button type="button" className="btn" disabled={!filtered} onClick={resetFilters}>
          초기화
        </button>
      </div>
      {activity.state === 'loading' ? <Loading label="최근 활동을 불러오는 중…" /> : null}
      {activity.state === 'error' ? <ErrorBox error={activity.error} onRetry={activity.reload} /> : null}
      {activity.state === 'ready' && activity.data.length === 0 ? (
        <p className="faint small" role="status">
          {filtered ? '선택한 조건에 맞는 활동이 없습니다.' : '최근 활동이 없습니다.'}
        </p>
      ) : null}
      {activity.state === 'ready' && activity.data.length > 0 ? (
        <ActivityRows entries={activity.data} />
      ) : null}
    </section>
  );
}
