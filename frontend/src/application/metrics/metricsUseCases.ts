import type { Usage } from '../../domain/metrics/usage';
import type { MetricsGateway } from './ports/MetricsGateway';

export interface MetricsUseCases {
  usage(): Promise<Usage>;
}

export function createMetricsUseCases(gateway: MetricsGateway): MetricsUseCases {
  return { usage: () => gateway.usage() };
}
