import { describe, expect, it } from 'vitest';
import type { Space, SpaceDraft } from '../../domain/space/space';
import type { SpaceGateway, SpacePatch } from './ports/SpaceGateway';
import { createSpaceUseCases } from './spaceUseCases';

function space(overrides: Partial<Space>): Space {
  return {
    id: 'sp',
    slug: 'team',
    name: 'Team',
    description: '',
    links: [],
    createdAt: '2026-10-01T00:00:00Z',
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
    ...overrides,
  };
}

/** In-memory server whose stored spaces are the observable result. */
function fakeServer(initial: Space[] = []): SpaceGateway & { spaces: Space[] } {
  const server = {
    spaces: [...initial],
    list: () => Promise.resolve(server.spaces),
    create: (draft: SpaceDraft) => {
      const created = space({ ...draft, id: `sp-${server.spaces.length + 1}` });
      server.spaces.push(created);
      return Promise.resolve(created);
    },
    get: (slug: string) => Promise.resolve(server.spaces.find((s) => s.slug === slug) ?? space({ slug })),
    update: (slug: string, patch: SpacePatch) => {
      const { archived, ...settings } = patch;
      const found = server.spaces.find((s) => s.slug === slug) ?? space({});
      const archivedAt = archived === undefined ? found.archivedAt : archived ? '2026-10-20T00:00:00Z' : null;
      const updated = { ...found, ...settings, archivedAt };
      server.spaces = server.spaces.map((s) => (s.slug === slug ? updated : s));
      return Promise.resolve(updated);
    },
  };
  return server;
}

describe('space use cases', () => {
  it('lists spaces with the most recent activity first', async () => {
    const server = fakeServer([
      space({ slug: 'old', lastActivityAt: '2026-10-02T00:00:00Z' }),
      space({ slug: 'new', lastActivityAt: '2026-10-17T00:00:00Z' }),
      space({ slug: 'quiet', createdAt: '2026-10-10T00:00:00Z' }),
    ]);
    const spaces = await createSpaceUseCases(server).list();
    expect(spaces.map((s) => s.slug)).toEqual(['new', 'quiet', 'old']);
  });

  it('creates a normalized space and rejects invalid drafts without saving', async () => {
    const server = fakeServer();
    const useCases = createSpaceUseCases(server);
    const rejected = await useCases.create({ slug: 'Bad Slug', name: '', description: '', links: [] });
    expect(rejected).toEqual({ ok: false, error: ['slug_invalid', 'name_required'] });
    expect(server.spaces).toEqual([]);

    const created = await useCases.create({ slug: ' docs ', name: ' 문서 ', description: ' d ', links: [] });
    expect(created.ok && created.value.name).toBe('문서');
    expect(server.spaces.map((s) => [s.slug, s.description])).toEqual([['docs', 'd']]);
  });

  it('keeps the space as is when nothing changed and stores a changed slug', async () => {
    const current = space({ slug: 'team', name: 'Team' });
    const server = fakeServer([current]);
    const useCases = createSpaceUseCases(server);
    expect(
      await useCases.update(current, { slug: 'team', name: 'Team', description: '', links: [] }),
    ).toEqual({
      ok: true,
      value: current,
    });
    const renamed = await useCases.update(current, {
      slug: 'crew',
      name: 'Team',
      description: '',
      links: [],
    });
    expect(renamed.ok && renamed.value.slug).toBe('crew');
    expect(server.spaces.map((s) => s.slug)).toEqual(['crew']);
  });

  it('archives a space, brings it back, and leaves one already in that state alone', async () => {
    const current = space({ slug: 'team' });
    const server = fakeServer([current]);
    const useCases = createSpaceUseCases(server);

    const archived = await useCases.setArchived(current, true);
    expect(archived.archivedAt).toBe('2026-10-20T00:00:00Z');
    expect(await useCases.setArchived(archived, true)).toBe(archived);
    expect((await useCases.setArchived(archived, false)).archivedAt).toBeNull();
  });
});
