// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { emptyEditStatistics } from '../../../../domain/statistics/statistics';
import { EditTimeDetails } from './EditTimeDetails';

afterEach(cleanup);

it('starts collapsed and displays separate closed time measures when expanded', async () => {
  const value = {
    ...emptyEditStatistics(),
    human: { milliseconds: 10000, count: 2 },
    agentExplicit: { milliseconds: 20000, count: 1 },
    agentEstimated: { milliseconds: 5000, count: 1 },
    openCount: 3,
  };
  const { container } = render(
    <EditTimeDetails
      documentId="doc"
      statistics={{ get: () => Promise.resolve(value), close: () => Promise.resolve(true) }}
    />,
  );
  const details = container.querySelector('details');
  if (details === null) throw new Error('expected details');
  expect(details.open).toBe(false);
  details.open = true;
  fireEvent(details, new Event('toggle'));
  await waitFor(() => expect(screen.getByText('0분 10초 · 2건').textContent).toBe('0분 10초 · 2건'));
  expect(screen.getByText('0분 20초 · 1건').textContent).toBe('0분 20초 · 1건');
  expect(screen.getByText('0분 5초 · 1건').textContent).toBe('0분 5초 · 1건');
  expect(screen.getByText('3건, 합계에서 제외').textContent).toBe('3건, 합계에서 제외');
  expect(screen.queryByRole('button')).toBeNull();
});

it('an unavailable statistics query leaves an inline message instead of blocking the editor', async () => {
  const { container } = render(
    <EditTimeDetails
      documentId="doc"
      statistics={{
        get: () => Promise.reject(new Error('offline')),
        close: () => Promise.resolve(true),
      }}
    />,
  );
  const details = container.querySelector('details');
  if (details === null) throw new Error('expected details');
  details.open = true;
  fireEvent(details, new Event('toggle'));
  await waitFor(() =>
    expect(screen.getByText(/문서 편집은 계속할 수 있습니다/).textContent).toContain('계속'),
  );
});
