import type { EditStatistics } from '../../domain/statistics/statistics';
import type { StatisticsGateway } from './ports/StatisticsGateway';

export interface StatisticsUseCases {
  get(documentId: string): Promise<EditStatistics>;
  close(documentId: string, tabId: string, lastInputAt: string): Promise<boolean>;
}

export function createStatisticsUseCases(gateway: StatisticsGateway): StatisticsUseCases {
  return {
    get: (documentId) => gateway.get(documentId),
    close: async (documentId, tabId, lastInputAt) => {
      try {
        await gateway.close(documentId, tabId, lastInputAt);
        return true;
      } catch {
        // Optional statistics never change the editor's save outcome.
        return false;
      }
    },
  };
}
