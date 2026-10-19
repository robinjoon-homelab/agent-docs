import type { Activity } from '../../domain/activity/activity';
import type { ActivityGateway } from './ports/ActivityGateway';

export const DEFAULT_ACTIVITY_LIMIT = 50;

export interface ActivityUseCases {
  recent(spaceSlug: string | null, limit?: number): Promise<Activity[]>;
}

export function createActivityUseCases(gateway: ActivityGateway): ActivityUseCases {
  return {
    recent: (spaceSlug, limit = DEFAULT_ACTIVITY_LIMIT) => gateway.list(spaceSlug, limit),
  };
}
