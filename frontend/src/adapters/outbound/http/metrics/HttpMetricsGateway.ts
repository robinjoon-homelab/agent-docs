import type { MetricsGateway } from '../../../../application/metrics/ports/MetricsGateway';
import type { SpaceWeekUsage, Usage, WeekUsage } from '../../../../domain/metrics/usage';
import type { HttpClient } from '../shared/httpClient';
import { list, num, obj, optNum, str } from '../shared/json';

function toSpaceUsage(value: unknown): SpaceWeekUsage {
  const source = obj(value, 'spaceUsage');
  return {
    slug: str(source, 'slug'),
    name: str(source, 'name'),
    documents: num(source, 'documents'),
    comments: num(source, 'comments'),
  };
}

function toWeek(value: unknown): WeekUsage {
  const source = obj(value, 'week');
  return { weekStart: str(source, 'weekStart'), spaces: list(source.spaces, toSpaceUsage) };
}

export class HttpMetricsGateway implements MetricsGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async usage(): Promise<Usage> {
    const source = obj(await this.http.get('/api/metrics/usage'));
    return {
      weeks: list(source.weeks, toWeek),
      agentResponseMinutesMedian: optNum(source, 'agentResponseMinutesMedian'),
      revisitedDocuments: optNum(source, 'revisitedDocuments') ?? 0,
    };
  }
}
