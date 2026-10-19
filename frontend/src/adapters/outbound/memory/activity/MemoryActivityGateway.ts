import type { ActivityGateway } from '../../../../application/activity/ports/ActivityGateway';
import type { Activity } from '../../../../domain/activity/activity';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemoryActivityGateway implements ActivityGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(spaceSlug: string | null, limit: number): Promise<Activity[]> {
    return respond(() =>
      this.store.activity
        .filter((activity) => spaceSlug === null || activity.space?.slug === spaceSlug)
        .slice(0, limit),
    );
  }
}
