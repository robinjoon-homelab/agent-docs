import { expect, it } from 'vitest';
import { emptyEditStatistics } from '../../../../domain/statistics/statistics';
import { HttpClient } from '../shared/httpClient';
import { HttpStatisticsGateway } from './HttpStatisticsGateway';

it('maps separate closed statistics measures from the API', async () => {
  const value = { ...emptyEditStatistics(), human: { milliseconds: 12000, count: 2 } };
  const http = new HttpClient({ fetch: () => Promise.resolve(Response.json(value)), cookies: () => '' });
  const gateway = new HttpStatisticsGateway(http);
  expect(await gateway.get('doc')).toEqual(value);
});

it('exposes a close transport failure for the use case to isolate', async () => {
  const http = new HttpClient({ fetch: () => Promise.reject(new Error('offline')), cookies: () => '' });
  const gateway = new HttpStatisticsGateway(http);
  await expect(gateway.close('doc', 'tab', '2026-01-01T00:00:00Z')).rejects.toThrow();
});
