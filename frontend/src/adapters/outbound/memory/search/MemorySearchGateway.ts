import type { SearchGateway } from '../../../../application/search/ports/SearchGateway';
import { blockPlainText } from '../../../../domain/document/blockText';
import type { SearchHit, TitleHit } from '../../../../domain/search/search';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

function matches(text: string, query: string): boolean {
  return text.toLowerCase().includes(query.toLowerCase());
}

function snippet(text: string, query: string): string {
  const index = text.toLowerCase().indexOf(query.toLowerCase());
  const start = Math.max(0, index - 30);
  return `${start > 0 ? '…' : ''}${text.slice(start, index + query.length + 50)}`;
}

export class MemorySearchGateway implements SearchGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  search(query: string, spaceSlug?: string): Promise<SearchHit[]> {
    return respond(() => {
      const hits: SearchHit[] = [];
      for (const document of this.store.documents.values()) {
        if (document.trashedAt !== null || (spaceSlug !== undefined && document.space.slug !== spaceSlug))
          continue;
        const base = {
          space: { slug: document.space.slug, name: document.space.name },
          documentId: document.id,
          path: document.path,
          title: document.title,
        };
        if (matches(document.title, query))
          hits.push({ ...base, kind: 'title', blockId: null, threadId: null, snippet: document.path });
        for (const block of document.blocks) {
          const text = blockPlainText(block);
          if (matches(text, query))
            hits.push({
              ...base,
              kind: 'block',
              blockId: block.id,
              threadId: null,
              snippet: snippet(text, query),
            });
        }
        for (const thread of this.store.threads.get(document.id) ?? []) {
          const comment = thread.comments.find(
            (candidate) => !candidate.deleted && matches(candidate.body, query),
          );
          if (comment !== undefined)
            hits.push({
              ...base,
              kind: 'comment',
              blockId: null,
              threadId: thread.id,
              snippet: snippet(comment.body, query),
            });
        }
      }
      return hits;
    });
  }

  titles(query: string): Promise<TitleHit[]> {
    return respond(() =>
      [...this.store.documents.values()]
        .filter(
          (document) =>
            document.trashedAt === null && (matches(document.title, query) || matches(document.path, query)),
        )
        .map((document) => ({
          space: { slug: document.space.slug, name: document.space.name },
          documentId: document.id,
          path: document.path,
          title: document.title,
        })),
    );
  }
}
