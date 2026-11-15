import type { EditStatistics } from '../../../domain/statistics/statistics';

export interface StatisticsGateway {
  get(documentId: string): Promise<EditStatistics>;
  close(documentId: string, tabId: string, lastInputAt: string): Promise<void>;
}
