import type { Space, SpaceDraft, SpaceDraftError } from '../../domain/space/space';
import { normalizeSpaceDraft, sortSpacesByActivity, validateSpaceDraft } from '../../domain/space/space';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { SpaceGateway, SpacePatch } from './ports/SpaceGateway';

export interface SpaceUseCases {
  /** Spaces with the most recent activity first. */
  list(): Promise<Space[]>;
  get(slug: string): Promise<Space>;
  create(draft: SpaceDraft): Promise<Result<Space, SpaceDraftError[]>>;
  update(current: Space, draft: SpaceDraft): Promise<Result<Space, SpaceDraftError[]>>;
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
  };
}
