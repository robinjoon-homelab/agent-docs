import { describe, expect, it } from 'vitest';
import type { AgentDocument, DocumentSummary, RevisionSnapshot } from '../../domain/document/document';
import { document, human, paragraph, stored, summary } from '../../test-support/fixtures';
import { createDocumentUseCases } from './documentUseCases';
import type { DocumentGateway } from './ports/DocumentGateway';

function gateway(overrides: Partial<DocumentGateway>): DocumentGateway {
  return overrides as DocumentGateway;
}

describe('document use cases', () => {
  it('merges recent documents across spaces newest first', async () => {
    const bySpace: Record<string, DocumentSummary[]> = {
      a: [
        summary({ id: 'a1', updatedAt: '2026-10-01T00:00:00Z' }),
        summary({ id: 'a2', updatedAt: '2026-10-09T00:00:00Z' }),
      ],
      b: [summary({ id: 'b1', updatedAt: '2026-10-05T00:00:00Z' })],
    };
    const useCases = createDocumentUseCases(
      gateway({ listInSpace: (slug) => Promise.resolve(bySpace[slug] ?? []) }),
    );
    const recent = await useCases.recentAcross([{ slug: 'a' }, { slug: 'b' }], 2);
    expect(recent.map((entry) => [entry.space.slug, entry.document.id])).toEqual([
      ['a', 'a2'],
      ['b', 'b1'],
    ]);
  });

  it('validates the path and title before creating', async () => {
    const useCases = createDocumentUseCases(
      gateway({
        create: (_slug, input) => Promise.resolve(document({ path: input.path, title: input.title })),
      }),
    );
    expect(await useCases.create('team', '/', ' ')).toEqual({
      ok: false,
      error: ['path_invalid', 'title_required'],
    });
    const result = await useCases.create('team', ' guides / setup ', ' 설치 ');
    expect(result.ok && [result.value.path, result.value.title]).toEqual(['guides/setup', '설치']);
  });

  it('moves a document into a folder with its own name and skips no-op moves', async () => {
    const useCases = createDocumentUseCases(
      gateway({ patch: (_id, _revision, patch) => Promise.resolve(document({ path: patch.path ?? '' })) }),
    );
    const moved = await useCases.move({ id: 'd1', path: 'guides/setup', revision: 4 }, 'design');
    expect(moved.ok && moved.value?.path).toBe('design/setup');
    expect(await useCases.move({ id: 'd1', path: 'guides/setup', revision: 4 }, 'guides')).toEqual({
      ok: true,
      value: null,
    });
    const deep = Array.from({ length: 10 }, () => 'f').join('/');
    expect(await useCases.move({ id: 'd1', path: 'a', revision: 4 }, deep)).toEqual({
      ok: false,
      error: ['path_invalid'],
    });
  });

  it('compares a revision with the current document', async () => {
    const snapshot: RevisionSnapshot = {
      revision: 2,
      title: 'T',
      path: 'p',
      blocks: [stored(paragraph('aaaaaaaaaa', 'old'))],
      actor: human(),
      createdAt: '2026-10-01T00:00:00Z',
    };
    const current: AgentDocument = document({ blocks: [stored(paragraph('aaaaaaaaaa', 'new'))] });
    const useCases = createDocumentUseCases(
      gateway({
        diff: (_id, from, to) =>
          Promise.resolve({ from, to: to ?? current.revision, changes: [], unified: '' }),
        revision: () => Promise.resolve(snapshot),
        get: () => Promise.resolve(current),
      }),
    );
    const comparison = await useCases.compare('doc-1', 2, null);
    expect(comparison.diff.to).toBe(current.revision);
    expect(comparison.fromBlocks).toEqual(snapshot.blocks);
    expect(comparison.toBlocks).toEqual(current.blocks);
  });
});
