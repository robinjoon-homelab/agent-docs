// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { ServicesProvider, type Services } from '../../../../../config/ServicesContext';
import type { DocumentUseCases, Comparison } from '../../../../../application/document/documentUseCases';
import type { RevisionFilter } from '../../../../../application/document/ports/DocumentGateway';
import type { Revision } from '../../../../../domain/document/document';
import { agent, document, human } from '../../../../../test-support/fixtures';
import { SessionProvider } from '../../app/SessionContext';
import { HistoryPage } from './HistoryPage';
afterEach(cleanup);
function revision(number: number, isAgent = false): Revision {
  return {
    revision: number,
    actor: isAgent ? agent() : human(),
    source: isAgent ? 'mcp' : 'web',
    summary: `revision-${number}`,
    changedBlockIds: [],
    createdAt: '2026-11-16T00:00:00Z',
    threadIds: [],
  };
}
class HistorySource {
  current = document({ revision: 5, permission: 'read', blocks: [] });
  items = [revision(6, true), revision(5, true), revision(4), revision(3)];
  pending: Promise<Revision[]> | null = null;
  comparisonHead: number | null = null;
  get = () => Promise.resolve(this.current);
  revisions = async (_id: string, filter: RevisionFilter = {}) => {
    const items = await (this.pending ?? Promise.resolve(this.items));
    return items.filter((item) => filter.actor === undefined || item.actor.kind === filter.actor);
  };
  compare = (_id: string, from: number, to: number | null): Promise<Comparison> =>
    Promise.resolve({
      diff: { from, to: to ?? this.comparisonHead ?? this.current.revision, changes: [], unified: '' },
      fromBlocks: [],
      toBlocks: [],
    });
}
function LocationPath() {
  const { pathname, search, hash } = useLocation();
  return (
    <output aria-label="주소">
      {pathname}
      {search}
      {hash}
    </output>
  );
}
function show(source: HistorySource, entry = '/s/team/d/doc-1/history') {
  const documents: Pick<DocumentUseCases, 'get' | 'revisions' | 'compare'> = source;
  const services = {
    documents,
    clock: { now: () => new Date('2026-11-16T00:00:00Z') },
    spaces: {
      get: () => Promise.resolve({ id: 'sp-1', slug: 'team', name: 'Team', permission: 'read', links: [] }),
    },
    threads: { list: () => Promise.resolve([]) },
    assets: {},
  } as unknown as Services;
  const session = {
    me: { userId: 'reader', login: 'reader', name: 'Reader', avatarUrl: '', owner: false },
    inboxVersion: 0,
    refreshInbox: () => {},
  };
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <ServicesProvider services={services}>
        <SessionProvider session={session}>
          <LocationPath />
          <Routes>
            <Route path="/s/:space/d/:docId/history" element={<HistoryPage />} />
          </Routes>
        </SessionProvider>
      </ServicesProvider>
    </MemoryRouter>,
  );
}
it('uses a fresh current revision when finalization lands between the initial document and history list', async () => {
  const source = new HistorySource();
  let publish: (items: Revision[]) => void = () => {};
  source.pending = new Promise<Revision[]>((resolve) => {
    publish = resolve;
  });
  show(source);
  await screen.findByRole('heading', { name: 'Setup · 기록' });
  source.current = { ...source.current, revision: 6 };
  await act(async () => {
    publish(source.items);
    await source.pending;
  });
  await screen.findByText('rev 5 → 현재 (rev 6)');
  expect(screen.getByRole('button', { name: /revision-6/ }).textContent).toContain('현재');
  const previous = screen.getByRole('button', { name: /revision-5/ });
  expect(previous.textContent).not.toContain('현재');
  expect(previous.getAttribute('aria-pressed')).toBe('true');
  fireEvent.click(screen.getByRole('button', { name: /revision-3/ }));
  await screen.findByText('rev 3 → 현재 (rev 6)');
});
it('keeps the authoritative head when an actor filter excludes the latest revisions', async () => {
  const source = new HistorySource();
  source.current = { ...source.current, revision: 6 };
  show(source);
  await screen.findByText('rev 5 → 현재 (rev 6)');
  fireEvent.click(screen.getByRole('button', { name: '사람' }));
  await screen.findByText('rev 4 → 현재 (rev 6)');
  const latestVisible = screen.getByRole('button', { name: /revision-4/ });
  expect(latestVisible.getAttribute('aria-pressed')).toBe('true');
  expect(latestVisible.textContent).not.toContain('현재');
  expect(screen.queryByRole('button', { name: /revision-6/ })).toBeNull();
  fireEvent.click(screen.getByRole('checkbox', { name: '두 개 비교' }));
  fireEvent.click(screen.getByRole('button', { name: /revision-3/ }));
  await screen.findByText('rev 3 → rev 4');
  expect(latestVisible.textContent).not.toContain('현재');
});
it('uses the actual server comparison target when it is newer than the document snapshot', async () => {
  const source = new HistorySource();
  source.current = { ...source.current, revision: 6 };
  source.comparisonHead = 7;
  show(source);
  await screen.findByText('rev 6 → 현재 (rev 7)');
  expect(screen.getByRole('button', { name: /revision-6/ }).getAttribute('aria-pressed')).toBe('true');
  expect(screen.getByRole('button', { name: /revision-6/ }).textContent).not.toContain('현재');
  fireEvent.click(screen.getByRole('button', { name: /revision-3/ }));
  await screen.findByText('rev 3 → 현재 (rev 7)');
  fireEvent.click(screen.getByRole('checkbox', { name: '두 개 비교' }));
  fireEvent.click(screen.getByRole('button', { name: /revision-4/ }));
  await screen.findByText('rev 3 → rev 4');
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /revision-6/ }).textContent).not.toContain('현재'),
  );
});

it('redirects a reply history URL using current document access and compares to the linked revision', async () => {
  const source = new HistorySource();
  source.current = { ...source.current, space: { ...source.current.space, slug: 'team' } };
  show(source, '/s/former-space/d/doc-1/history?replyRev=4#reply');

  await screen.findByText('rev 3 → rev 4');
  expect(screen.getByLabelText('주소').textContent).toBe('/s/team/d/doc-1/history?replyRev=4#reply');
});
it('can open a reply linked to the first revision', async () => {
  const source = new HistorySource();
  show(source, '/s/team/d/doc-1/history?replyRev=1');
  await screen.findByText('rev 1 · 첫 리비전');
  await screen.findByText('첫 리비전에 본문이 없습니다.');
});
