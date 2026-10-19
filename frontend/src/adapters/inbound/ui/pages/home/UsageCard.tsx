import { formatMinutes, weekBars, weeklyTotals } from '../../../../../domain/metrics/usage';
import { useServices } from '../../../../../config/ServicesContext';
import { ErrorBox, Loading } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';

function weekLabel(weekStart: string): string {
  return weekStart.slice(5).replace('-', '/');
}

/** Owner-only usage summary: documents and comments per week, agent response time, revisits. */
export function UsageCard() {
  const services = useServices();
  const usage = useLoad('home:usage', () => services.metrics.usage());

  if (usage.state === 'loading') return <Loading />;
  if (usage.state === 'error') return <ErrorBox error={usage.error} onRetry={usage.reload} />;

  const bars = weekBars(weeklyTotals(usage.data));
  return (
    <div className="card usage-card">
      <div className="usage-stats">
        <div className="usage-stat">
          <span className="usage-stat-value">{formatMinutes(usage.data.agentResponseMinutesMedian)}</span>
          <span className="faint small">에이전트 응답 시간(중간값)</span>
        </div>
        <div className="usage-stat">
          <span className="usage-stat-value">{usage.data.revisitedDocuments}</span>
          <span className="faint small">다시 찾은 문서</span>
        </div>
      </div>
      {bars.length === 0 ? (
        <p className="faint small">아직 사용 기록이 없습니다.</p>
      ) : (
        <table className="usage-bars">
          <caption className="visually-hidden">주별 바뀐 문서 수와 댓글 수</caption>
          <thead>
            <tr>
              <th scope="col">주</th>
              <th scope="col" title="그 주에 바뀐 서로 다른 문서 수">
                바뀐 문서
              </th>
              <th scope="col">댓글</th>
            </tr>
          </thead>
          <tbody>
            {bars.map((bar) => (
              <tr key={bar.weekStart}>
                <th scope="row" className="faint small mono">
                  {weekLabel(bar.weekStart)}
                </th>
                <td>
                  <span
                    className="usage-bar usage-bar-documents"
                    style={{ width: `${bar.documentsRatio * 100}%` }}
                  />
                  <span className="usage-bar-value">{bar.documents}</span>
                </td>
                <td>
                  <span
                    className="usage-bar usage-bar-comments"
                    style={{ width: `${bar.commentsRatio * 100}%` }}
                  />
                  <span className="usage-bar-value">{bar.comments}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
