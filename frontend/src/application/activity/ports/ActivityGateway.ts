import type { Activity } from '../../../domain/activity/activity';

export type ActivityFilter = {
  readonly kind?: string;
  readonly actorKind?: Activity['actor']['kind'];
  readonly since?: string;
};

export interface ActivityGateway {
  list(spaceSlug: string | null, limit: number, filter?: ActivityFilter): Promise<Activity[]>;
}
