// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, expect, it } from 'vitest';
import type { ActivityUseCases } from '../../../../../application/activity/activityUseCases';
import { ServicesProvider, type Services } from '../../../../../config/ServicesContext';
import type { Activity } from '../../../../../domain/activity/activity';
import { documentHref, historyHref, threadHref } from '../../../../../domain/document/document';
import { RecentActivity } from './RecentActivity';

const slug = 'agent-docs';
const thread: Activity = {
  id: '1',
  kind: 'thread.opened',
  actor: { kind: 'human', login: 'robinjoon', agent: null },
  space: { slug, name: 'Agent Docs' },
  documentId: 'doc',
  documentTitle: null,
  threadId: 'thread',
  revision: 4,
  summary: '댓글을 남겼습니다.',
  at: '2026-11-26T09:00:00Z',
};
const purged: Activity = {
  ...thread,
  id: '2',
  kind: 'document.purged',
  actor: { kind: 'system' },
  threadId: null,
  revision: null,
  summary: '휴지통 보관 기간이 지나 삭제',
};

afterEach(cleanup);

function show(recent: ActivityUseCases['recent']) {
  const services = {
    activity: { recent },
    clock: { now: () => new Date('2026-11-28T09:00:00Z') },
  } as Services;
  render(
    <ServicesProvider services={services}>
      <MemoryRouter>
        <RecentActivity spaceSlug={slug} />
      </MemoryRouter>
    </ServicesProvider>,
  );
}

function select(name: string, value: string) {
  fireEvent.change(screen.getByRole('combobox', { name }), { target: { value } });
}

it('combines filters before rendering and resets them back to the original list', async () => {
  show((_space, _limit, filter) => {
    const since = filter?.since;
    return Promise.resolve(
      [thread, purged].filter(
        (entry) =>
          (filter?.kind === undefined || entry.kind === filter.kind) &&
          (filter?.actorKind === undefined || entry.actor.kind === filter.actorKind) &&
          (since === undefined || Date.parse(entry.at) > Date.parse(since)),
      ),
    );
  });
  const reset = screen.getByRole<HTMLButtonElement>('button', { name: '초기화' });
  expect(reset.disabled).toBe(true);
  expect((await screen.findByRole('link', { name: thread.summary })).getAttribute('href')).toBe(
    threadHref(slug, 'doc', 'thread'),
  );
  expect(screen.getByRole('link', { name: purged.summary }).getAttribute('href')).toBe(
    documentHref(slug, 'doc'),
  );

  select('활동 종류', 'document.purged');
  select('작업 주체', 'system');
  select('작업 시기', '24');
  await screen.findByText('선택한 조건에 맞는 활동이 없습니다.');
  expect(screen.queryByRole('link', { name: purged.summary })).toBeNull();

  select('작업 시기', '168');
  await screen.findByRole('link', { name: purged.summary });
  expect(screen.queryByRole('link', { name: thread.summary })).toBeNull();
  select('작업 주체', 'human');
  await screen.findByText('선택한 조건에 맞는 활동이 없습니다.');

  fireEvent.click(reset);
  await screen.findByRole('link', { name: thread.summary });
  expect(screen.getByRole('link', { name: purged.summary })).toBeDefined();
  expect(reset.disabled).toBe(true);
});

it('keeps the filters usable while loading and reloads the activity after an error', async () => {
  const revision = { ...thread, threadId: null, summary: '리비전이 확정되었습니다.' };
  let error = true;
  show(() => (error ? Promise.reject(new Error('조회 실패')) : Promise.resolve([revision])));

  expect(screen.getByRole('status').textContent).toContain('최근 활동을 불러오는 중');
  expect(screen.getByRole('combobox', { name: '작업 주체' })).toBeDefined();
  expect((await screen.findByRole('alert')).textContent).toContain('조회 실패');

  error = false;
  fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));
  expect((await screen.findByRole('link', { name: revision.summary })).getAttribute('href')).toBe(
    historyHref(slug, 'doc', 4),
  );
});
