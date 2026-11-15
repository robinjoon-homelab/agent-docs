import type { StatisticsGateway } from '../../../../application/statistics/ports/StatisticsGateway';
import type { EditStatistics } from '../../../../domain/statistics/statistics';
import { emptyEditStatistics } from '../../../../domain/statistics/statistics';

export class MemoryStatisticsGateway implements StatisticsGateway {
  private readonly values: ReadonlyMap<string, EditStatistics>;

  constructor(values: ReadonlyMap<string, EditStatistics> = new Map()) {
    this.values = new Map(values);
  }

  get(documentId: string): Promise<EditStatistics> {
    return Promise.resolve(this.values.get(documentId) ?? emptyEditStatistics());
  }

  close(): Promise<void> {
    return Promise.resolve();
  }
}
