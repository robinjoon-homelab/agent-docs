import { expect, it } from 'vitest';
import { emptyEditStatistics } from '../../domain/statistics/statistics';
import { createStatisticsUseCases } from './statisticsUseCases';

it('optional close failure is returned independently of the editor state', async () => {
  const service = createStatisticsUseCases({
    get: () => Promise.resolve(emptyEditStatistics()),
    close: () => Promise.reject(new Error('unavailable')),
  });
  expect(await service.close('doc', 'tab', '2026-01-01T00:00:00Z')).toBe(false);
  expect(await service.get('doc')).toEqual(emptyEditStatistics());
});
