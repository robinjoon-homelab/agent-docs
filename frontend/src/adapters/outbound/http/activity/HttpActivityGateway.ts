import type { ActivityFilter, ActivityGateway } from '../../../../application/activity/ports/ActivityGateway';
import type { Activity } from '../../../../domain/activity/activity';
import type { HttpClient } from '../shared/httpClient';
import { query } from '../shared/httpClient';
import { list, num, obj, optNum, optStr, str, strOr } from '../shared/json';
import { toActor, toSpaceName } from '../shared/mappers';

function toActivity(value: unknown): Activity {
  const source = obj(value, 'activity');
  const actor = obj(source.actor, 'actor');
  return {
    // The server numbers its log entries; the screen only uses the id as a key.
    id: String(num(source, 'id')),
    kind: str(source, 'kind'),
    actor: actor.kind === 'system' ? { kind: 'system' } : toActor(actor),
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

  async list(spaceSlug: string | null, limit: number, filter?: ActivityFilter): Promise<Activity[]> {
    return list(
      await this.http.get(`/api/activity${query({ space: spaceSlug, limit, ...filter })}`),
      toActivity,
    );
  }
}
