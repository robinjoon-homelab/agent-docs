// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { fakeServicesOf } from '../../../../../config/fakeServices';
import { ServicesProvider } from '../../../../../config/ServicesContext';
import { SpacePermissionsProvider } from '../../app/SpacePermissionsContext';
import { SessionProvider } from '../../app/SessionContext';
import { ShellProvider } from '../../app/ShellContext';
import { ToastProvider } from '../../common/Toast';
import { MemoryStore } from '../../../../outbound/memory/shared/store';
import { SpaceHomePage } from './SpaceHomePage';
import { SpaceSettingsPage } from './SpaceSettingsPage';

afterEach(cleanup);

function LocationEcho({ label }: { label: string }) {
  const location = useLocation();
  return (
    <p data-testid={label}>
      {location.pathname}
      {location.search}
      {location.hash}
    </p>
  );
}

function setup(initialUrl: string, owner: boolean) {
  const me = { userId: 'u1', login: owner ? 'owner' : 'member', name: 'User', avatarUrl: '', owner };
  const store = new MemoryStore(me);
  store.spaces = [
    {
      id: 'space-1',
      slug: 'canonical-space',
      name: '정식 스페이스',
      description: '',
      links: [],
      createdAt: '2026-01-01T00:00:00Z',
      archivedAt: null,
      lastActivityAt: null,
      documentCount: 0,
      permission: owner ? 'owner' : 'read',
    },
  ];
  store.slugAliases.set('old-space', 'canonical-space');
  const services = fakeServicesOf(store, 'UTC');

  render(
    <ServicesProvider services={services}>
      <SessionProvider session={{ me, inboxVersion: 0, refreshInbox: () => undefined }}>
        <SpacePermissionsProvider>
          <ToastProvider>
            <ShellProvider
              shell={{
                spaces: store.spaces,
                currentSpaceSlug: null,
                refreshSpaces: () => undefined,
                refreshTree: () => undefined,
                treeVersion: 0,
                openPalette: () => undefined,
                openNewDocument: () => undefined,
                railCollapsed: false,
                setRailCollapsed: () => undefined,
              }}
            >
              <MemoryRouter initialEntries={[initialUrl]}>
                <Routes>
                  <Route path="/s/:space" element={<SpaceHomePage />} />
                  <Route path="/s/:space/settings" element={<SpaceSettingsPage />} />
                </Routes>
                <LocationEcho label="current-location" />
              </MemoryRouter>
            </ShellProvider>
          </ToastProvider>
        </SpacePermissionsProvider>
      </SessionProvider>
    </ServicesProvider>,
  );
}

describe('old space slug alias navigation', () => {
  it('navigates old space home alias to canonical slug while preserving query params and fragment', async () => {
    setup('/s/old-space?filter=recent#space-tree', false);

    await waitFor(() => {
      expect(screen.getByTestId('current-location').textContent).toBe(
        '/s/canonical-space?filter=recent#space-tree',
      );
    });
    expect(await screen.findByRole('heading', { name: '정식 스페이스' })).toBeDefined();
  });

  it('navigates old space settings alias for owners while preserving params and fragment', async () => {
    setup('/s/old-space/settings?tab=links#settings-guide', true);

    await waitFor(() => {
      expect(screen.getByTestId('current-location').textContent).toBe(
        '/s/canonical-space/settings?tab=links#settings-guide',
      );
    });
    expect(await screen.findByRole('heading', { name: '스페이스 설정' })).toBeDefined();
  });

  it('keeps settings hidden and does not redirect for non-owners or unknown spaces', async () => {
    setup('/s/old-space/settings?tab=links#settings-guide', false);

    expect(await screen.findByRole('heading', { name: '찾을 수 없습니다' })).toBeDefined();
    expect(screen.getByTestId('current-location').textContent).toBe(
      '/s/old-space/settings?tab=links#settings-guide',
    );
    cleanup();

    setup('/s/missing-space?x=1#top', true);
    expect(await screen.findByRole('heading', { name: '찾을 수 없습니다' })).toBeDefined();
    expect(screen.getByTestId('current-location').textContent).toBe('/s/missing-space?x=1#top');
  });
});
