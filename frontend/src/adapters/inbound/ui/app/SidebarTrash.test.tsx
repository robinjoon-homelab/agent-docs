// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { DocumentEditorSession } from '../../../../application/document/editorSession';
import { fakeServicesOf } from '../../../../config/fakeServices';
import type { Services } from '../../../../config/services';
import { ServicesProvider } from '../../../../config/ServicesContext';
import { blockPlainText } from '../../../../domain/document/blockText';
import type { Space } from '../../../../domain/space/space';
import { document as docFixture, paragraph, stored } from '../../../../test-support/fixtures';
import { MemoryDocumentGateway } from '../../../outbound/memory/document/MemoryDocumentGateway';
import { MemoryStore } from '../../../outbound/memory/shared/store';
import { ToastProvider } from '../common/Toast';
import { DocumentPage } from '../pages/document/DocumentPage';
import { SpaceHomePage } from '../pages/space/SpaceHomePage';
import { SpaceTrashPage } from '../pages/space/SpaceTrashPage';
import { AppShell } from './AppShell';
import { SessionProvider } from './SessionContext';
import { SpacePermissionsProvider } from './SpacePermissionsContext';

afterEach(cleanup);

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

const MIME = 'application/x-agent-docs-document';
const team: Space = {
  id: 'space-1',
  slug: 'team',
  name: '팀 문서',
  description: '',
  links: [],
  createdAt: '2026-01-01T00:00:00Z',
  archivedAt: null,
  lastActivityAt: null,
  documentCount: 3,
  permission: 'edit',
};
const other = { ...team, id: 'space-2', slug: 'other', name: '다른 팀', documentCount: 1 };

class FailingSaveGateway extends MemoryDocumentGateway {
  override saveContent(): Promise<never> {
    return Promise.reject(new Error('저장 요청 실패'));
  }
}

function setup({
  url = '/s/team/trash',
  permission = 'edit',
  trash,
  failSave = false,
}: {
  url?: string;
  permission?: 'edit' | 'read';
  trash?: Services['documents']['trash'];
  failSave?: boolean;
} = {}) {
  const me = { userId: 'editor', login: 'editor', name: 'Editor', avatarUrl: '', owner: false };
  const store = new MemoryStore(me);
  store.spaces = [{ ...team, permission }, other];
  const add = (space: Space, id: string, path: string, title: string) =>
    store.documents.set(
      id,
      docFixture({ id, space, path, title, blocks: [stored(paragraph('p1', '본문'))] }),
    );
  add(team, 'parent', 'guide', '안내서');
  add(team, 'child', 'guide/setup', '설치');
  add(team, 'leaf', 'memo', '메모');
  add(other, 'other', 'other', '다른 문서');
  const fakeServices = fakeServicesOf(store, 'UTC');
  const services: Services = {
    ...fakeServices,
    documents: trash === undefined ? fakeServices.documents : { ...fakeServices.documents, trash },
    openEditor: failSave
      ? (document) => new DocumentEditorSession(new FailingSaveGateway(store), fakeServices.clock, document)
      : fakeServices.openEditor,
  };

  render(
    <MemoryRouter initialEntries={[url]}>
      <ServicesProvider services={services}>
        <SessionProvider session={{ me, inboxVersion: 0, refreshInbox: () => undefined }}>
          <SpacePermissionsProvider>
            <ToastProvider>
              <Routes>
                <Route element={<AppShell />}>
                  <Route path="/s/:space" element={<SpaceHomePage />} />
                  <Route path="/s/:space/trash" element={<SpaceTrashPage />} />
                  <Route path="/s/:space/d/:docId" element={<DocumentPage />} />
                </Route>
              </Routes>
            </ToastProvider>
          </SpacePermissionsProvider>
        </SessionProvider>
      </ServicesProvider>
    </MemoryRouter>,
  );
  return store;
}

function sidebar() {
  return within(screen.getByRole('navigation', { name: '사이드바' }));
}

function dataTransfer() {
  const items = new Map<string, string>();
  return {
    get types() {
      return [...items.keys()];
    },
    setData: (type: string, value: string) => items.set(type, value),
    getData: (type: string) => items.get(type) ?? '',
    dropEffect: 'none',
    effectAllowed: 'none',
  };
}

function drop(from: HTMLElement, to: HTMLElement) {
  const transfer = dataTransfer();
  fireEvent.dragStart(from, { dataTransfer: transfer });
  fireEvent.dragOver(to, { dataTransfer: transfer });
  fireEvent.drop(to, { dataTransfer: transfer });
}

async function pasteDraft() {
  const body = await screen.findByText('본문');
  const editor = body.closest('.tiptap');
  if (editor === null) throw new Error('편집기를 찾을 수 없습니다.');
  fireEvent.paste(editor, {
    clipboardData: {
      types: ['text/plain'],
      files: [],
      getData: (type: string) => (type === 'text/plain' ? '대기 중인 입력' : ''),
    },
  });
  return editor;
}

describe('사이드바 문서 휴지통 드롭', () => {
  it('휴지통 링크를 클릭해 열 수 있고 단일 문서를 놓으면 열린 휴지통과 트리가 갱신된다', async () => {
    setup({ url: '/s/team' });
    const link = await sidebar().findByRole('link', { name: '휴지통' });
    fireEvent.click(link);
    await screen.findByText('휴지통이 비어 있습니다.');
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    const transfer = dataTransfer();

    fireEvent.dragStart(leaf, { dataTransfer: transfer });
    fireEvent.dragOver(link, { dataTransfer: transfer });
    expect(link.classList.contains('drop-target')).toBe(true);
    fireEvent.drop(link, { dataTransfer: transfer });

    expect(link.classList.contains('drop-target')).toBe(false);
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(await within(screen.getByRole('main')).findByText('메모')).toBeDefined();
    expect(sidebar().queryByRole('link', { name: '메모' })).toBeNull();
  });

  it('상위 문서의 이동을 확인하기 전까지 보존하고 확인하면 스페이스 홈과 휴지통에 함께 반영한다', async () => {
    const store = setup({ url: '/s/team' });
    const main = within(screen.getByRole('main'));
    await main.findAllByRole('link', { name: /안내서/ });
    const parent = await sidebar().findByRole('link', { name: '안내서' });
    const link = sidebar().getByRole('link', { name: '휴지통' });

    drop(parent, link);
    let dialog = within(screen.getByRole('dialog', { name: '휴지통으로 이동' }));
    expect(dialog.getByText(/하위 문서 1개/)).toBeDefined();
    fireEvent.click(dialog.getByRole('button', { name: '취소' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(store.documents.get('parent')?.trashedAt).toBeNull();
    expect(store.documents.get('child')?.trashedAt).toBeNull();

    drop(parent, link);
    dialog = within(screen.getByRole('dialog', { name: '휴지통으로 이동' }));
    fireEvent.click(dialog.getByRole('button', { name: '휴지통으로 이동' }));
    await waitFor(() => {
      expect(main.queryAllByRole('link', { name: /안내서|설치/ })).toHaveLength(0);
      expect(main.queryAllByRole('link', { name: /메모/ }).length).toBeGreaterThan(0);
    });
    fireEvent.click(link);
    expect(await main.findByText('안내서')).toBeDefined();
    expect(await main.findByText('설치')).toBeDefined();
    expect(store.documents.get('child')?.trashedAt).toBe(store.documents.get('parent')?.trashedAt);
    fireEvent.click(
      within(main.getByText('안내서').closest('li') as HTMLElement).getByRole('button', { name: '복원' }),
    );
    await main.findByText('휴지통이 비어 있습니다.');
    expect(store.documents.get('child')?.trashedAt).toBeNull();
  });

  it('드래그 종료와 다른 스페이스로 전환한 뒤에는 기존 페이로드를 거절한다', async () => {
    const store = setup();
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    const link = sidebar().getByRole('link', { name: '휴지통' });
    const transfer = dataTransfer();
    fireEvent.dragStart(leaf, { dataTransfer: transfer });
    fireEvent.dragOver(link, { dataTransfer: transfer });
    fireEvent.dragEnd(leaf, { dataTransfer: transfer });
    expect(link.classList.contains('drop-target')).toBe(false);
    fireEvent.drop(link, { dataTransfer: transfer });

    fireEvent.dragStart(leaf, { dataTransfer: transfer });
    fireEvent.click(sidebar().getByRole('button', { name: /팀 문서/ }));
    fireEvent.click(sidebar().getByRole('menuitem', { name: /다른 팀/ }));
    await sidebar().findByRole('link', { name: '다른 문서' });
    fireEvent.drop(sidebar().getByRole('link', { name: '휴지통' }), { dataTransfer: transfer });
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
  });

  it('같은 스페이스라도 드래그를 시작한 행과 다른 문서 ID를 거절한다', async () => {
    const store = setup();
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    const transfer = dataTransfer();
    fireEvent.dragStart(leaf, { dataTransfer: transfer });
    transfer.setData(MIME, 'child');
    fireEvent.drop(sidebar().getByRole('link', { name: '휴지통' }), { dataTransfer: transfer });
    expect(store.documents.get('child')?.trashedAt).toBeNull();
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
  });

  it('읽기 권한에서는 문서 드래그와 휴지통 화면을 사용할 수 없다', async () => {
    const store = setup({ permission: 'read' });
    const leaf = await sidebar().findByRole<HTMLAnchorElement>('link', { name: '메모' });
    expect(leaf.draggable).toBe(false);
    expect(sidebar().queryByRole('link', { name: '휴지통' })).toBeNull();
    expect(screen.getByRole('heading', { name: '찾을 수 없습니다' })).toBeDefined();
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
  });

  it('실패하면 오류를 표시하고 트리와 휴지통은 그대로 둔다', async () => {
    const store = setup({ trash: () => Promise.reject(new Error('휴지통 요청 실패')) });
    await screen.findByText('휴지통이 비어 있습니다.');
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    drop(leaf, sidebar().getByRole('link', { name: '휴지통' }));
    expect(await screen.findByText('휴지통 요청 실패')).toBeDefined();
    expect(sidebar().getByRole('link', { name: '메모' })).toBe(leaf);
    expect(screen.getByText('휴지통이 비어 있습니다.')).toBeDefined();
    expect(screen.queryByText(/휴지통으로 옮겼습니다/)).toBeNull();
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
  });

  it('문서를 다른 문서 아래로 옮기고 최상위로 되돌리는 동작을 유지한다', async () => {
    const store = setup();
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    drop(leaf, sidebar().getByRole('link', { name: '안내서' }));
    await waitFor(() => expect(store.documents.get('leaf')?.path).toBe('guide/memo'));
    fireEvent.click(await sidebar().findByRole('button', { name: '안내서 펼치기' }));
    const moved = await sidebar().findByTitle(/^guide\/memo ·/);
    const root = moved.closest('.tree-root');
    if (root === null || !(root instanceof HTMLElement)) throw new Error('문서 트리가 없습니다.');
    drop(moved, root);
    await waitFor(() => expect(store.documents.get('leaf')?.path).toBe('memo'));
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
  });

  it('열린 하위 문서의 제목 초안을 저장한 뒤 상위 문서와 함께 휴지통으로 옮긴다', async () => {
    const store = setup({ url: '/s/team/d/child' });
    const title = await screen.findByRole('textbox', { name: '제목' });
    fireEvent.change(title, { target: { value: '새 제목' } });
    expect(store.documents.get('child')?.title).toBe('설치');
    const parent = await sidebar().findByRole('link', { name: '안내서' });
    drop(parent, sidebar().getByRole('link', { name: '휴지통' }));
    const dialog = within(screen.getByRole('dialog', { name: '휴지통으로 이동' }));
    fireEvent.click(dialog.getByRole('button', { name: '휴지통으로 이동' }));

    await screen.findByRole('heading', { name: '팀 문서' });
    expect(store.documents.get('parent')?.trashedAt).toBeTruthy();
    expect(store.documents.get('child')?.trashedAt).toBeTruthy();
    expect(store.documents.get('child')?.title).toBe('새 제목');
  });

  it('열린 단일 문서의 대기 중인 본문을 저장한 뒤 휴지통으로 옮긴다', async () => {
    const store = setup({ url: '/s/team/d/leaf' });
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    const editor = await pasteDraft();
    expect(editor.textContent).toContain('대기 중인 입력');
    expect(store.documents.get('leaf')?.blocks.map(blockPlainText).join('')).toBe('본문');
    drop(leaf, sidebar().getByRole('link', { name: '휴지통' }));

    await screen.findByRole('heading', { name: '팀 문서' });
    expect(store.documents.get('leaf')?.trashedAt).toBeTruthy();
    expect(store.documents.get('leaf')?.blocks.map(blockPlainText).join('')).toContain('대기 중인 입력');
  });

  it('열린 문서의 저장에 실패하면 편집 화면에 남고 휴지통으로 옮기지 않는다', async () => {
    const store = setup({ url: '/s/team/d/leaf', failSave: true });
    const leaf = await sidebar().findByRole('link', { name: '메모' });
    const editor = await pasteDraft();
    drop(leaf, sidebar().getByRole('link', { name: '휴지통' }));

    expect(await screen.findByText(/본문을 저장하지 못했습니다/)).toBeDefined();
    expect(editor.isConnected).toBe(true);
    expect(editor.textContent).toContain('대기 중인 입력');
    expect(store.documents.get('leaf')?.trashedAt).toBeNull();
    expect(screen.queryByText(/휴지통으로 옮겼습니다/)).toBeNull();
  });
});
