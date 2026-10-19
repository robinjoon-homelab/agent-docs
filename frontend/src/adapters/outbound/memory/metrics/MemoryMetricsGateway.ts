import type { MetricsGateway } from '../../../../application/metrics/ports/MetricsGateway';
import type { Usage } from '../../../../domain/metrics/usage';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

export class MemoryMetricsGateway implements MetricsGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  usage(): Promise<Usage> {
    return respond(() => this.store.usage);
  }
}
