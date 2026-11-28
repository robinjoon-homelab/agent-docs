import type { ActivityFilter, ActivityGateway } from '../../../../application/activity/ports/ActivityGateway';
import type { Activity } from '../../../../domain/activity/activity';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemoryActivityGateway implements ActivityGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(spaceSlug: string | null, limit: number, filter: ActivityFilter = {}): Promise<Activity[]> {
    const since = filter.since === undefined ? null : Date.parse(filter.since);
    return respond(() =>
      this.store.activity
        .filter(
          (activity) =>
            (spaceSlug === null || activity.space?.slug === spaceSlug) &&
            (filter.kind === undefined || activity.kind === filter.kind) &&
            (filter.actorKind === undefined || activity.actor.kind === filter.actorKind) &&
            (since === null || Date.parse(activity.at) > since),
        )
        .slice(0, limit),
    );
  }
}
