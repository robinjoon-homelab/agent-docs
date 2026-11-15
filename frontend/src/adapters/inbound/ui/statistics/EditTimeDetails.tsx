import { useState } from 'react';
import type { StatisticsUseCases } from '../../../../application/statistics/statisticsUseCases';
import type { EditMeasure, EditStatistics } from '../../../../domain/statistics/statistics';

function elapsed(measure: EditMeasure): string {
  const seconds = Math.floor(measure.milliseconds / 1000);
  return `${Math.floor(seconds / 60)}분 ${seconds % 60}초 · ${measure.count}건`;
}

/** Read-only, closed intervals only. Opening statistics never delays document editing. */
export function EditTimeDetails({
  documentId,
  statistics,
}: {
  documentId: string;
  statistics: StatisticsUseCases;
}) {
  const [value, setValue] = useState<EditStatistics | null>(null);
  const [failed, setFailed] = useState(false);
  const load = (): void => {
    setFailed(false);
    void statistics
      .get(documentId)
      .then(setValue)
      .catch(() => setFailed(true));
  };
  return (
    <details
      onToggle={(event) => {
        if (event.currentTarget.open) load();
      }}
    >
      <summary>편집 시간</summary>
      {failed ? <p>편집 시간을 불러오지 못했습니다. 문서 편집은 계속할 수 있습니다.</p> : null}
      {value === null && !failed ? <p>불러오는 중…</p> : null}
      {value !== null ? (
        <dl>
          <dt>사람</dt>
          <dd>{elapsed(value.human)}</dd>
          <dt>에이전트 명시 종료</dt>
          <dd>{elapsed(value.agentExplicit)}</dd>
          <dt>에이전트 추정 종료</dt>
          <dd>{elapsed(value.agentEstimated)}</dd>
          <dt>아직 열린 구간</dt>
          <dd>{value.openCount}건, 합계에서 제외</dd>
        </dl>
      ) : null}
      <p>사람 시간은 첫 입력부터 마지막 저장된 입력까지입니다. 추정 종료는 명시 종료와 따로 계산합니다.</p>
    </details>
  );
}
