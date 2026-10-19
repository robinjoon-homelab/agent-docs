import type { Activity } from '../../../domain/activity/activity';

export interface ActivityGateway {
  list(spaceSlug: string | null, limit: number): Promise<Activity[]>;
}
