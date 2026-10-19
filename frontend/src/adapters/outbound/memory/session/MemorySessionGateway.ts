import type { SessionGateway } from '../../../../application/session/ports/SessionGateway';
import type { Me } from '../../../../domain/session/me';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemorySessionGateway implements SessionGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  me(): Promise<Me> {
    return respond(() => this.store.me);
  }

  logout(): Promise<void> {
    return respond(() => undefined);
  }
}
