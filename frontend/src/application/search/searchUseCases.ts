import type { SearchHit, SearchScope, TitleHit } from '../../domain/search/search';
import { normalizeQuery } from '../../domain/search/search';
import type { SearchGateway } from './ports/SearchGateway';

export interface SearchUseCases {
  /** Title quick jump across all spaces; an empty query returns nothing without a request. */
  titles(query: string): Promise<TitleHit[]>;
  search(query: string, scope: SearchScope): Promise<SearchHit[]>;
}

export function createSearchUseCases(gateway: SearchGateway): SearchUseCases {
  return {
    async titles(raw) {
      const query = normalizeQuery(raw);
      return query === '' ? [] : gateway.titles(query);
    },
    async search(raw, scope) {
      const query = normalizeQuery(raw);
      if (query === '') return [];
      return gateway.search(query, scope.kind === 'space' ? scope.slug : undefined);
    },
  };
}
