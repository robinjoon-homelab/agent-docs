// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { fakeServicesOf } from '../../../../../config/fakeServices';
import { ServicesProvider } from '../../../../../config/ServicesContext';
import { document as docFixture, paragraph, stored } from '../../../../../test-support/fixtures';
import { MemoryStore } from '../../../../outbound/memory/shared/store';
import { SessionProvider } from '../../app/SessionContext';
import { ShellProvider } from '../../app/ShellContext';
import { ToastProvider } from '../../common/Toast';
import { TrashSection } from './SpaceSettingsPage';

afterEach(cleanup);

function ensureDialogApi() {
  if (typeof HTMLDialogElement === 'undefined') return;
  if (typeof HTMLDialogElement.prototype.showModal !== 'function') {
    HTMLDialogElement.prototype.showModal = function showModal() {
      this.setAttribute('open', '');
    };
  }
  if (typeof HTMLDialogElement.prototype.close !== 'function') {
    HTMLDialogElement.prototype.close = function close() {
      this.removeAttribute('open');
    };
  }
}

function setupTrash({ owner }: { owner: boolean }) {
  ensureDialogApi();
  const me = {
    userId: owner ? 'u-owner' : 'u-editor',
    login: owner ? 'owner' : 'editor',
    name: 'User',
    avatarUrl: '',
    owner,
  };
  const store = new MemoryStore(me);
  store.spaces = [
    {
      id: 'space-1',
      slug: 'team',
      name: 'Team',
      description: '',
      links: [],
      createdAt: '2026-01-01T00:00:00Z',
      archivedAt: null,
      lastActivityAt: null,
      documentCount: 4,
      permission: owner ? 'owner' : 'edit',
    },
  ];
  const space = { id: 'space-1', slug: 'team', name: 'Team' };
  const addTrashed = (id: string, path: string, title: string, trashedAt: string) => {
    store.documents.set(
      id,
      docFixture({
        id,
        space,
        path,
        title,
        trashedAt,
        blocks: [stored(paragraph('p1', title))],
      }),
    );
  };
  addTrashed('doc-parent', 'guides', '가이드', '2026-10-10T09:00:00Z');
  addTrashed('doc-child-same', 'guides/setup', '설치', '2026-10-10T09:00:00Z');
  addTrashed('doc-child-sep', 'guides/faq', '자주 묻는 질문', '2026-10-01T09:00:00Z');
  addTrashed('doc-sibling', 'guides-extra', '별도 문서', '2026-10-10T09:00:00Z');

  const services = fakeServicesOf(store, 'UTC');
  return { store, services, me };
}

describe('SpaceSettings TrashSection', () => {
  it('warns about all trashed descendants including separately trashed and shows owner export link', async () => {
    const { store, services, me } = setupTrash({ owner: true });

    render(
      <MemoryRouter>
        <ServicesProvider services={services}>
          <SessionProvider session={{ me, inboxVersion: 0, refreshInbox: () => undefined }}>
            <ToastProvider>
              <ShellProvider
                shell={{
                  spaces: store.spaces,
                  currentSpaceSlug: 'team',
                  refreshSpaces: () => undefined,
                  refreshTree: () => undefined,
                  treeVersion: 0,
                  openPalette: () => undefined,
                  openNewDocument: () => undefined,
                  railCollapsed: false,
                  setRailCollapsed: () => undefined,
                }}
              >
                <TrashSection spaceSlug="team" />
              </ShellProvider>
            </ToastProvider>
          </SessionProvider>
        </ServicesProvider>
      </MemoryRouter>,
    );

    const deleteButtons = await screen.findAllByRole('button', { name: '영구 삭제' });
    const firstDelete = deleteButtons[0];
    if (firstDelete === undefined) throw new Error('expected delete button');
    fireEvent.click(firstDelete);

    const dialog = screen.getByRole('dialog', { name: '영구 삭제' });
    expect(dialog.textContent).toContain('휴지통에 있는 하위 문서 2개도 함께 지웁니다.');
    expect(dialog.textContent).toContain('되돌릴 수 없습니다.');

    const exportLink = screen.getByRole<HTMLAnchorElement>('link', { name: '삭제 전에 스페이스 내보내기' });
    expect(exportLink.getAttribute('href')).toBe('/settings/data');
    expect(exportLink.getAttribute('target')).toBe('_blank');
    expect(exportLink.getAttribute('rel')).toBe('noreferrer');

    const confirmButton = dialog.querySelector('button.btn-danger');
    if (confirmButton === null) throw new Error('expected confirm button');
    fireEvent.click(confirmButton);
    await waitFor(() => {
      expect(store.documents.has('doc-parent')).toBe(false);
    });
  });

  it('hides purge and export suggestion for non-owner editors while preserving restore behavior', async () => {
    const { store, services, me } = setupTrash({ owner: false });
    const untrashedIds: string[] = [];
    const trackedServices = {
      ...services,
      documents: {
        ...services.documents,
        untrash: (id: string) => {
          untrashedIds.push(id);
          return services.documents.untrash(id);
        },
      },
    };

    render(
      <MemoryRouter>
        <ServicesProvider services={trackedServices}>
          <SessionProvider session={{ me, inboxVersion: 0, refreshInbox: () => undefined }}>
            <ToastProvider>
              <ShellProvider
                shell={{
                  spaces: store.spaces,
                  currentSpaceSlug: 'team',
                  refreshSpaces: () => undefined,
                  refreshTree: () => undefined,
                  treeVersion: 0,
                  openPalette: () => undefined,
                  openNewDocument: () => undefined,
                  railCollapsed: false,
                  setRailCollapsed: () => undefined,
                }}
              >
                <TrashSection spaceSlug="team" />
              </ShellProvider>
            </ToastProvider>
          </SessionProvider>
        </ServicesProvider>
      </MemoryRouter>,
    );

    const restoreButtons = await screen.findAllByRole('button', { name: '복원' });
    expect(screen.queryByRole('button', { name: '영구 삭제' })).toBeNull();
    expect(screen.queryByRole('link', { name: '삭제 전에 스페이스 내보내기' })).toBeNull();

    const firstRestore = restoreButtons[0];
    if (firstRestore === undefined) throw new Error('expected restore button');
    fireEvent.click(firstRestore);
    await waitFor(() => {
      expect(untrashedIds).toEqual(['doc-parent']);
      expect(store.documents.get('doc-parent')?.trashedAt).toBeNull();
    });
  });

  it('does not offer purge confirmation while trash list is unavailable or loading', async () => {
    const { store, services, me } = setupTrash({ owner: true });
    const holder: { reject: ((error: Error) => void) | null } = { reject: null };
    const controlledServices = {
      ...services,
      documents: {
        ...services.documents,
        listTrash: () =>
          new Promise<never>((_resolve, reject) => {
            holder.reject = reject;
          }),
      },
    };

    render(
      <MemoryRouter>
        <ServicesProvider services={controlledServices}>
          <SessionProvider session={{ me, inboxVersion: 0, refreshInbox: () => undefined }}>
            <ToastProvider>
              <ShellProvider
                shell={{
                  spaces: store.spaces,
                  currentSpaceSlug: 'team',
                  refreshSpaces: () => undefined,
                  refreshTree: () => undefined,
                  treeVersion: 0,
                  openPalette: () => undefined,
                  openNewDocument: () => undefined,
                  railCollapsed: false,
                  setRailCollapsed: () => undefined,
                }}
              >
                <TrashSection spaceSlug="team" />
              </ShellProvider>
            </ToastProvider>
          </SessionProvider>
        </ServicesProvider>
      </MemoryRouter>,
    );

    expect(screen.queryByRole('button', { name: '영구 삭제' })).toBeNull();
    expect(screen.queryByRole('dialog', { name: '영구 삭제' })).toBeNull();

    holder.reject?.(new Error('network error'));
    await screen.findByText(/network error/);
    expect(screen.queryByRole('button', { name: '영구 삭제' })).toBeNull();
    expect(screen.queryByRole('dialog', { name: '영구 삭제' })).toBeNull();
  });
});
