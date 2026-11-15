import { expect, it } from 'vitest';
import { emptyEditStatistics } from '../../../../domain/statistics/statistics';
import { MemoryStatisticsGateway } from './MemoryStatisticsGateway';

it('the fake preview starts with no invented statistics', async () => {
  expect(await new MemoryStatisticsGateway().get('doc')).toEqual(emptyEditStatistics());
});

it('the fake gateway keeps its own map of supplied document statistics', async () => {
  const value = { ...emptyEditStatistics(), human: { milliseconds: 1000, count: 1 } };
  const values = new Map([['doc', value]]);
  const gateway = new MemoryStatisticsGateway(values);
  values.clear();
  expect(await gateway.get('doc')).toEqual(value);
  expect(await gateway.get('other')).toEqual(emptyEditStatistics());
});
