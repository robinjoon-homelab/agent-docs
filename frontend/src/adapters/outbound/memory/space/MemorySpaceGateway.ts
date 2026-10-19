import { ApiError } from '../../../../application/shared/ports/errors';
import type { SpaceGateway, SpacePatch } from '../../../../application/space/ports/SpaceGateway';
import type { Space, SpaceDraft } from '../../../../domain/space/space';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemorySpaceGateway implements SpaceGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(): Promise<Space[]> {
    return respond(() =>
      this.store.spaces.map((space) => ({ ...space, documentCount: this.countDocuments(space) })),
    );
  }

  create(draft: SpaceDraft): Promise<Space> {
    return respond(() => {
      if (this.store.spaces.some((space) => space.slug === draft.slug)) {
        throw new ApiError('conflict', 409, '이미 쓰는 슬러그입니다.');
      }
      const space: Space = {
        ...draft,
        id: this.store.nextId('space'),
        createdAt: this.store.now(),
        archivedAt: null,
        lastActivityAt: null,
        documentCount: 0,
      };
      this.store.spaces.push(space);
      return space;
    });
  }

  get(slug: string): Promise<Space> {
    return respond(() => this.store.space(slug));
  }

  update(slug: string, patch: SpacePatch): Promise<Space> {
    return respond(() => {
      const current = this.store.space(slug);
      const { archived, ...settings } = patch;
      const archivedAt =
        archived === undefined
          ? current.archivedAt
          : archived
            ? (current.archivedAt ?? this.store.now())
            : null;
      const updated: Space = { ...current, ...settings, archivedAt };
      if (patch.slug !== undefined && patch.slug !== current.slug)
        this.store.slugAliases.set(current.slug, patch.slug);
      this.store.spaces = this.store.spaces.map((space) => (space.id === current.id ? updated : space));
      for (const [id, document] of this.store.documents) {
        if (document.space.id !== current.id) continue;
        this.store.documents.set(id, {
          ...document,
          space: { ...document.space, slug: updated.slug, name: updated.name },
        });
      }
      return updated;
    });
  }

  private countDocuments(space: Space): number {
    return [...this.store.documents.values()].filter(
      (doc) => doc.space.id === space.id && doc.trashedAt === null,
    ).length;
  }
}
