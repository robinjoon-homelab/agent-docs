import type { Usage } from '../../../domain/metrics/usage';

export interface MetricsGateway {
  usage(): Promise<Usage>;
}
