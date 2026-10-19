import { describe, expect, it } from 'vitest';
import type { SearchHit } from '../../domain/search/search';
import type { SearchGateway } from './ports/SearchGateway';
import { createSearchUseCases } from './searchUseCases';

function hit(slug: string): SearchHit {
  return {
    kind: 'block',
    space: { slug, name: slug },
    documentId: 'd',
    path: 'p',
    title: 't',
    blockId: null,
    threadId: null,
    snippet: '',
  };
}

const gateway: SearchGateway = {
  search: (_query, spaceSlug) =>
    Promise.resolve(spaceSlug === undefined ? [hit('a'), hit('b')] : [hit(spaceSlug)]),
  titles: (query) =>
    Promise.resolve([{ space: { slug: 'a', name: 'A' }, documentId: 'd', path: 'p', title: query }]),
};

describe('search use cases', () => {
  it('returns nothing for a blank query and normalizes spacing', async () => {
    const useCases = createSearchUseCases(gateway);
    expect(await useCases.titles('   ')).toEqual([]);
    expect((await useCases.titles('  api   docs ')).map((t) => t.title)).toEqual(['api docs']);
  });

  it('limits the search to the current space when asked', async () => {
    const useCases = createSearchUseCases(gateway);
    expect((await useCases.search('x', { kind: 'space', slug: 'b' })).map((h) => h.space.slug)).toEqual([
      'b',
    ]);
    expect((await useCases.search('x', { kind: 'all' })).map((h) => h.space.slug)).toEqual(['a', 'b']);
  });
});
