import { ApiError } from '../../../../application/shared/ports/errors';
import type { SpaceGateway, SpacePatch } from '../../../../application/space/ports/SpaceGateway';
import type { Space, SpaceDraft, SpaceGuide, SpaceLink } from '../../../../domain/space/space';
import { linkedRepositories } from '../../../../domain/space/space';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemorySpaceGateway implements SpaceGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(): Promise<Space[]> {
    return respond(() =>
      this.store.spaces
        .filter((space) => this.store.permissionIn(space.id) !== null)
        .map((space) => ({
          ...space,
          permission: this.store.permissionIn(space.id) ?? 'read',
          documentCount: this.countDocuments(space),
        })),
    );
  }

  create(draft: SpaceDraft): Promise<Space> {
    return respond(() => {
      this.store.requireOwner();
      if (this.store.spaces.some((space) => space.slug === draft.slug)) {
        throw new ApiError('conflict', 409, '이미 쓰는 슬러그입니다.');
      }
      const space: Space = {
        ...draft,
        id: this.store.nextId('space'),
        permission: 'owner',
        createdAt: this.store.now(),
        archivedAt: null,
        lastActivityAt: null,
        documentCount: 0,
        agentGuide: null,
        agentGuideIssue: null,
        defaultRepository: null,
      };
      this.store.spaces.push(space);
      return space;
    });
  }

  get(slug: string): Promise<Space> {
    return respond(() => {
      const space = this.store.space(slug);
      return { ...space, permission: this.store.permissionIn(space.id) ?? 'read' };
    });
  }

  update(slug: string, patch: SpacePatch): Promise<Space> {
    return respond(() => {
      this.store.requireOwner();
      const current = this.store.space(slug);
      const { archived, agentGuideDocumentId, defaultRepository, ...settings } = patch;
      const archivedAt =
        archived === undefined
          ? current.archivedAt
          : archived
            ? (current.archivedAt ?? this.store.now())
            : null;
      const links = settings.links ?? current.links;
      const updated: Space = {
        ...current,
        ...settings,
        archivedAt,
        ...this.guideOf(current, agentGuideDocumentId),
        defaultRepository: this.repositoryOf(current, links, defaultRepository),
      };
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

  /** Like the server: only a document of this space outside the trash can be the guide. */
  private guideOf(
    current: Space,
    documentId: string | null | undefined,
  ): Pick<Space, 'agentGuide' | 'agentGuideIssue'> {
    if (documentId === undefined)
      return { agentGuide: current.agentGuide, agentGuideIssue: current.agentGuideIssue };
    if (documentId === null || documentId === '') return { agentGuide: null, agentGuideIssue: null };
    const document = this.store.documents.get(documentId);
    if (document === undefined || document.space.id !== current.id || document.trashedAt !== null) {
      throw new ApiError(
        'invalid',
        400,
        'the agent guide must be a document of this space outside the trash',
      );
    }
    const guide: SpaceGuide = { documentId, title: document.title, path: document.path };
    return { agentGuide: guide, agentGuideIssue: null };
  }

  /** Like the server: the default is one of the linked repositories, and dropping its link drops it. */
  private repositoryOf(
    current: Space,
    links: readonly SpaceLink[],
    chosen: string | null | undefined,
  ): string | null {
    const linked = linkedRepositories({ links });
    if (chosen === undefined) {
      const kept = current.defaultRepository ?? null;
      return kept !== null && linked.includes(kept) ? kept : null;
    }
    if (chosen === null || chosen.trim() === '') return null;
    const repository = linked.find((value) => value.toLowerCase() === chosen.trim().toLowerCase());
    if (repository === undefined) {
      throw new ApiError(
        'invalid',
        400,
        'the default repository must be one of the GitHub repositories linked to the space',
      );
    }
    return repository;
  }

  private countDocuments(space: Space): number {
    return [...this.store.documents.values()].filter(
      (doc) => doc.space.id === space.id && doc.trashedAt === null,
    ).length;
  }
}
