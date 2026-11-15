import type { Space, SpaceDraft } from '../../../domain/space/space';

/**
 * archived: true archives the space and false brings it back; the owner only. agentGuideDocumentId and
 * defaultRepository set those settings, null clears them, and leaving them out keeps them.
 */
export type SpacePatch = Partial<SpaceDraft> & {
  readonly archived?: boolean;
  readonly agentGuideDocumentId?: string | null;
  readonly defaultRepository?: string | null;
};

export interface SpaceGateway {
  list(): Promise<Space[]>;
  create(draft: SpaceDraft): Promise<Space>;
  /** A former slug resolves to the space with its current slug. */
  get(slug: string): Promise<Space>;
  update(slug: string, patch: SpacePatch): Promise<Space>;
}
