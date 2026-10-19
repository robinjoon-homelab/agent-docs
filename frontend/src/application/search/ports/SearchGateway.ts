import type { SearchHit, TitleHit } from '../../../domain/search/search';

export interface SearchGateway {
  search(query: string, spaceSlug?: string): Promise<SearchHit[]>;
  titles(query: string): Promise<TitleHit[]>;
}
