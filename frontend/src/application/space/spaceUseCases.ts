import type { Space, SpaceDraft, SpaceDraftError } from '../../domain/space/space';
import {
  isArchived,
  normalizeSpaceDraft,
  sortSpacesByActivity,
  validateSpaceDraft,
} from '../../domain/space/space';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { SpaceGateway, SpacePatch } from './ports/SpaceGateway';

export interface SpaceUseCases {
  /** Spaces with the most recent activity first. */
  list(): Promise<Space[]>;
  get(slug: string): Promise<Space>;
  create(draft: SpaceDraft): Promise<Result<Space, SpaceDraftError[]>>;
  update(current: Space, draft: SpaceDraft): Promise<Result<Space, SpaceDraftError[]>>;
  /** Archives the space or brings it back; a space already in that state is answered as is. */
  setArchived(current: Space, archived: boolean): Promise<Space>;
  /**
   * Names the document agents read before writing here, or clears it with null. The server takes only a document of
   * this space outside the trash; an offered guide that is chosen again is answered as is.
   */
  setAgentGuide(current: Space, documentId: string | null): Promise<Space>;
  /** Chooses the linked repository short references stand for, or clears the choice with null. */
  setDefaultRepository(current: Space, repository: string | null): Promise<Space>;
}

function changedFields(current: Space, draft: SpaceDraft): SpacePatch {
  const patch: { -readonly [K in keyof SpacePatch]: SpacePatch[K] } = {};
  if (draft.slug !== current.slug) patch.slug = draft.slug;
  if (draft.name !== current.name) patch.name = draft.name;
  if (draft.description !== current.description) patch.description = draft.description;
  if (JSON.stringify(draft.links) !== JSON.stringify(current.links)) patch.links = draft.links;
  return patch;
}

export function createSpaceUseCases(gateway: SpaceGateway): SpaceUseCases {
  return {
    async list() {
      return sortSpacesByActivity(await gateway.list());
    },
    get: (slug) => gateway.get(slug),
    async create(input) {
      const draft = normalizeSpaceDraft(input);
      const errors = validateSpaceDraft(draft);
      if (errors.length > 0) return failure(errors);
      return ok(await gateway.create(draft));
    },
    async update(current, input) {
      const draft = normalizeSpaceDraft(input);
      const errors = validateSpaceDraft(draft);
      if (errors.length > 0) return failure(errors);
      const patch = changedFields(current, draft);
      if (Object.keys(patch).length === 0) return ok(current);
      return ok(await gateway.update(current.slug, patch));
    },
    async setArchived(current, archived) {
      if (isArchived(current) === archived) return current;
      return gateway.update(current.slug, { archived });
    },
    async setAgentGuide(current, documentId) {
      const offered = current.agentGuide?.documentId ?? null;
      if (offered === documentId && (documentId !== null || !current.agentGuideIssue)) return current;
      return gateway.update(current.slug, { agentGuideDocumentId: documentId });
    },
    async setDefaultRepository(current, repository) {
      const chosen = repository === null || repository.trim() === '' ? null : repository.trim();
      if ((current.defaultRepository ?? null) === chosen) return current;
      return gateway.update(current.slug, { defaultRepository: chosen });
    },
  };
}
