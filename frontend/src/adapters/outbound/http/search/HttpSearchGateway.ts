import type { SearchGateway } from '../../../../application/search/ports/SearchGateway';
import type { SearchHit, TitleHit } from '../../../../domain/search/search';
import type { HttpClient } from '../shared/httpClient';
import { query } from '../shared/httpClient';
import { list, obj, oneOf, optStr, str, strOr } from '../shared/json';
import { toSpaceName } from '../shared/mappers';

function toHit(value: unknown): SearchHit {
  const source = obj(value, 'hit');
  return {
    kind: oneOf(source, 'kind', ['title', 'block', 'comment'] as const),
    space: toSpaceName(source.space),
    documentId: str(source, 'documentId'),
    path: str(source, 'path'),
    title: str(source, 'title'),
    blockId: optStr(source, 'blockId'),
    threadId: optStr(source, 'threadId'),
    snippet: strOr(source, 'snippet', ''),
  };
}

function toTitleHit(value: unknown): TitleHit {
  const source = obj(value, 'hit');
  return {
    space: toSpaceName(source.space),
    documentId: str(source, 'documentId'),
    path: str(source, 'path'),
    title: str(source, 'title'),
  };
}

export class HttpSearchGateway implements SearchGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async search(q: string, spaceSlug?: string): Promise<SearchHit[]> {
    return list(await this.http.get(`/api/search${query({ q, space: spaceSlug })}`), toHit);
  }

  async titles(q: string): Promise<TitleHit[]> {
    return list(await this.http.get(`/api/search/titles${query({ q })}`), toTitleHit);
  }
}
