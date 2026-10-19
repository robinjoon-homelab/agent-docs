import type { ActivityGateway } from '../../../../application/activity/ports/ActivityGateway';
import type { Activity } from '../../../../domain/activity/activity';
import type { HttpClient } from '../shared/httpClient';
import { query } from '../shared/httpClient';
import { list, obj, optNum, optStr, str, strOr } from '../shared/json';
import { toActor, toSpaceName } from '../shared/mappers';

function toActivity(value: unknown): Activity {
  const source = obj(value, 'activity');
  return {
    id: str(source, 'id'),
    kind: str(source, 'kind'),
    actor: toActor(source.actor),
    space: source.space === null || source.space === undefined ? null : toSpaceName(source.space),
    documentId: optStr(source, 'documentId'),
    documentTitle: optStr(source, 'documentTitle'),
    threadId: optStr(source, 'threadId'),
    revision: optNum(source, 'revision'),
    summary: strOr(source, 'summary', ''),
    at: str(source, 'at'),
  };
}

export class HttpActivityGateway implements ActivityGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async list(spaceSlug: string | null, limit: number): Promise<Activity[]> {
    return list(await this.http.get(`/api/activity${query({ space: spaceSlug, limit })}`), toActivity);
  }
}
