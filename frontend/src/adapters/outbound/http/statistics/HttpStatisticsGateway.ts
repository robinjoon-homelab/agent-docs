import type { StatisticsGateway } from '../../../../application/statistics/ports/StatisticsGateway';
import type { EditMeasure, EditStatistics } from '../../../../domain/statistics/statistics';
import type { HttpClient } from '../shared/httpClient';
import { query, segment } from '../shared/httpClient';
import { num, obj } from '../shared/json';

function measure(value: unknown): EditMeasure {
  const source = obj(value, 'edit measure');
  return { milliseconds: num(source, 'milliseconds'), count: num(source, 'count') };
}

export class HttpStatisticsGateway implements StatisticsGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async get(documentId: string): Promise<EditStatistics> {
    const source = obj(await this.http.get(`/api/documents/${segment(documentId)}/edit-statistics`));
    return {
      human: measure(source.human),
      agentExplicit: measure(source.agentExplicit),
      agentEstimated: measure(source.agentEstimated),
      openCount: num(source, 'openCount'),
    };
  }

  async close(documentId: string, tabId: string, lastInputAt: string): Promise<void> {
    await this.http.postKeepalive(
      `/api/documents/${segment(documentId)}/edit-statistics/close${query({ tabId, lastInputAt })}`,
    );
  }
}
