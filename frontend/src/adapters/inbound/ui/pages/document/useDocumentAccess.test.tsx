// @vitest-environment jsdom
import { DocumentAccessBanner } from './DocumentAccessBanner';
import { useLayoutEffect, useMemo, useRef, useState } from 'react';
import { SpacePermissionsProvider, useSpacePermissions } from '../../app/SpacePermissionsContext';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { DocumentEditorSession } from '../../../../../application/document/editorSession';
import type {
  DocumentGateway,
  MetaPatch,
  SaveContentResult,
} from '../../../../../application/document/ports/DocumentGateway';
import { createDocumentEventsUseCases } from '../../../../../application/document/documentEventsUseCases';
import {
  HttpDocumentEventsGateway,
  type DocumentEventSource,
} from '../../../../outbound/http/document/HttpDocumentEventsGateway';
import { document, paragraph, stored } from '../../../../../test-support/fixtures';
import type { AgentDocument } from '../../../../../domain/document/document';
import type { Block } from '../../../../../domain/document/block';
import type { Space } from '../../../../../domain/space/space';
import { permissionInSpace } from '../../../../../domain/space/permission';
import { ApiError } from '../../../../../application/shared/ports/errors';
import { useDocumentAccess } from './useDocumentAccess';
import { useDocumentEvents } from './useDocumentEvents';
afterEach(cleanup);
class Source implements DocumentEventSource {
  closed = false;
  listeners = new Map<string, (event: { readonly data?: string }) => void>();
  addEventListener(type: string, listener: (event: { readonly data?: string }) => void) {
    this.listeners.set(type, listener);
  }
  close() {
    this.closed = true;
  }
  dispatch(type: string, data: unknown) {
    this.listeners.get(type)?.({ data: JSON.stringify(data) });
  }
}
class Server {
  doc = document({
    permission: 'edit',
    blocks: [stored(paragraph('aaaaaaaaaa', 'base')), stored(paragraph('bbbbbbbbbb', 'other'))],
  });
  visible = true;
  offline = false;
  waiting: Promise<void> | null = null;
  async get(): Promise<AgentDocument> {
    if (this.waiting !== null) await this.waiting;
    if (this.offline) throw new ApiError('network', 0, 'merge unavailable');
    if (!this.visible) throw new ApiError('not_found', 404, 'hidden');
    return this.doc;
  }
  saveContent(_id: string, version: number, blocks: readonly Block[]): Promise<SaveContentResult> {
    if (version !== this.doc.version)
      return Promise.resolve({
        kind: 'conflict',
        currentVersion: this.doc.version,
        currentDocument: this.doc,
      });
    this.doc = { ...this.doc, version: version + 1, blocks: blocks.map((block) => stored(block)) };
    return Promise.resolve({
      kind: 'saved',
      version: this.doc.version,
      revision: this.doc.revision,
      blocks: this.doc.blocks,
    });
  }
  patch(_id: string, _version: number, patch: MetaPatch): Promise<AgentDocument> {
    this.doc = { ...this.doc, ...patch };
    return Promise.resolve(this.doc);
  }
  finalize(): Promise<SaveContentResult> {
    this.doc = { ...this.doc, revision: this.doc.revision + 1 };
    return Promise.resolve({
      kind: 'saved',
      version: this.doc.version,
      revision: this.doc.revision,
      blocks: this.doc.blocks,
    });
  }
}
function setup(owner = false) {
  const server = new Server();
  const initial = server.doc;
  const session = new DocumentEditorSession(
    server as unknown as DocumentGateway,
    { now: () => new Date('2026-11-16T00:00:00Z') },
    initial,
  );
  const source = new Source();
  const events = createDocumentEventsUseCases(new HttpDocumentEventsGateway(() => source));
  const hook = renderHook(() => {
    const close = useRef<() => void>(() => {});
    const access = useDocumentAccess(initial, owner, session, () => server.get(), {
      revoked: () => close.current(),
    });
    const stop = useDocumentEvents(initial.id, events, {
      revoked: access.revoked !== null,
      refreshDocument: access.refresh,
      reloadThreads: () => Promise.resolve(),
      onPermission: access.receivePermission,
      onRevoked: access.revoke,
      onError: () => {},
    });
    useLayoutEffect(() => {
      close.current = stop;
    }, [stop]);
    return access;
  });
  return { server, session, source, hook };
}
describe('document access protocol', () => {
  it('retains body and metadata input after downgrade and terminal owner-session revocation', async () => {
    const { server, session, source, hook } = setup(true);
    const draft = [paragraph('aaaaaaaaaa', 'my unsaved input'), paragraph('bbbbbbbbbb', 'other')];
    session.markEdited(draft);
    session.markMetaEdited({ title: 'my title' });
    server.doc = { ...server.doc, permission: 'comment' };
    await act(async () => {
      source.dispatch('permission', { permission: 'comment' });
      expect(session.isWritable()).toBe(false);
      expect(await session.saveContent(draft)).toBe('error');
      expect(await session.flushMeta()).toBe(false);
      await session.finalize();
    });
    expect(hook.result.current.permission).toBe('comment');
    expect(server.doc.version).toBe(3);
    expect(session.currentBlocks()).toEqual(draft);
    expect(session.getState().meta.title).toBe('my title');
    act(() => {
      source.dispatch('revoked', { reason: 'unauthenticated' });
    });
    server.visible = false;
    act(() => {
      source.dispatch('invalidate', {});
      source.dispatch('permission', { permission: 'owner' });
    });
    expect(source.closed).toBe(true);
    expect(session.isWritable()).toBe(false);
    expect(hook.result.current.permission).toBeNull();
    expect(hook.result.current.revoked).toBe('unauthenticated');
    expect(session.currentBlocks()).toEqual(draft);
    expect(session.getState()).toMatchObject({ version: 3, meta: { title: 'my title' } });
  });
  it('waits for the latest baseline before resuming and preserves local input through remote rebasing', async () => {
    const { server, session, source, hook } = setup();
    const draft = [paragraph('aaaaaaaaaa', 'my draft'), paragraph('bbbbbbbbbb', 'other')];
    session.markEdited(draft);
    server.doc = { ...server.doc, permission: 'comment' };
    await act(async () => {
      source.dispatch('permission', { permission: 'comment' });
      await session.idle();
    });
    server.doc = {
      ...server.doc,
      version: 10,
      permission: 'edit',
      blocks: [stored(paragraph('aaaaaaaaaa', 'base')), stored(paragraph('bbbbbbbbbb', 'remote change'))],
    };
    let release = () => {};
    server.waiting = new Promise<void>((resolve) => {
      release = resolve;
    });
    act(() => {
      source.dispatch('permission', { permission: 'edit' });
    });
    expect(session.isWritable()).toBe(false);
    expect(hook.result.current.resuming).toBe(true);
    expect(hook.result.current.currentAccess().basisReady).toBe(false);
    await act(async () => {
      release();
      await server.waiting;
      await session.idle();
    });
    expect(session.getState().version).toBe(10);
    expect(hook.result.current.currentAccess().basisReady).toBe(true);
    expect(session.isWritable()).toBe(true);
    expect(session.currentBlocks()).toEqual([
      paragraph('aaaaaaaaaa', 'my draft'),
      paragraph('bbbbbbbbbb', 'remote change'),
    ]);
    expect(server.doc.version).toBe(10);
    await act(async () => {
      expect(await session.saveContent(session.currentBlocks())).toBe('saved');
    });
    expect(server.doc.version).toBe(11);
  });
});

function space(id: string, permission: Space['permission']): Space {
  return {
    id,
    slug: id,
    name: id,
    permission,
    description: '',
    links: [],
    createdAt: '2026-11-16T00:00:00Z',
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
  };
}
it('shares only authoritative current space permissions after a move', async () => {
  const server = new Server();
  const initial = server.doc;
  const original = space(initial.space.id, 'owner');
  const current = space('new-space', 'edit');
  let response = { ...original, permission: 'comment' as const } as Space;
  const session = new DocumentEditorSession(
    server as unknown as DocumentGateway,
    { now: () => new Date('2026-11-16T00:00:00Z') },
    initial,
  );
  const hook = renderHook(
    () => {
      const live = useSpacePermissions();
      const access = useDocumentAccess(initial, false, session, () => server.get(), {
        spaces: { get: () => Promise.resolve(response), update: live.update },
      });
      return {
        access,
        originalPermission: permissionInSpace(original, true, live.grants),
        currentPermission: permissionInSpace(current, false, live.grants),
      };
    },
    { wrapper: SpacePermissionsProvider },
  );
  server.doc = {
    ...server.doc,
    permission: 'comment',
    space: { id: current.id, slug: current.slug, name: current.name },
  };
  await act(async () => {
    await hook.result.current.access.receivePermission('comment');
  });
  expect(session.isWritable()).toBe(false);
  expect(hook.result.current.access.permission).toBe('comment');
  expect(hook.result.current.originalPermission).toBe('owner');
  expect(hook.result.current.currentPermission).toBe('edit');
  response = { ...current, permission: 'read' };
  await act(async () => {
    await hook.result.current.access.receivePermission('comment');
  });
  expect(hook.result.current.originalPermission).toBe('owner');
  expect(hook.result.current.currentPermission).toBe('read');
});
it('a document not_found revocation leaves the owner space grant intact', () => {
  const server = new Server();
  const current = space(server.doc.space.id, 'owner');
  const session = new DocumentEditorSession(
    server as unknown as DocumentGateway,
    { now: () => new Date('2026-11-16T00:00:00Z') },
    server.doc,
  );
  const hook = renderHook(
    () => {
      const live = useSpacePermissions();
      const access = useDocumentAccess(server.doc, true, session, () => server.get(), {
        spaces: { get: () => Promise.resolve(current), update: live.update },
      });
      return { access, spacePermission: permissionInSpace(current, true, live.grants) };
    },
    { wrapper: SpacePermissionsProvider },
  );
  act(() => {
    hook.result.current.access.revoke('not_found');
  });
  expect(hook.result.current.access.revoked).toBe('not_found');
  expect(hook.result.current.access.permission).toBeNull();
  expect(session.isWritable()).toBe(false);
  expect(hook.result.current.spacePermission).toBe('owner');
});

it('closes the stream synchronously on an opened document requery 401 and retains its draft', async () => {
  const { server, session, source, hook } = setup(true);
  const draft = [paragraph('aaaaaaaaaa', 'cached input')];
  session.markEdited(draft);
  server.get = () => Promise.reject(new ApiError('unauthenticated', 401, 'expired'));
  await act(async () => {
    await hook.result.current.refresh();
    expect(source.closed).toBe(true);
    expect(session.isWritable()).toBe(false);
  });
  expect(hook.result.current.revoked).toBe('unauthenticated');
  expect(session.currentBlocks()).toEqual(draft);
});

function Workspace({ server }: { server: Server }) {
  const session = useMemo(
    () =>
      new DocumentEditorSession(
        server as unknown as DocumentGateway,
        { now: () => new Date('2026-11-16T00:00:00Z') },
        server.doc,
      ),
    [server],
  );
  const live = useSpacePermissions();
  const access = useDocumentAccess(server.doc, false, session, () => server.get(), {
    spaces: { get: () => Promise.resolve(space(server.doc.space.id, 'edit')), update: live.update },
  });
  const [draft, setDraft] = useState('');
  return (
    <>
      <p role="status">
        {server.doc.id}:{access.revoked ?? 'active'}
      </p>
      <p>스페이스 권한:{permissionInSpace(space(server.doc.space.id, 'edit'), false, live.grants)}</p>
      <button onClick={() => void access.refresh()}>현재 조회</button>
      <textarea aria-label="초안" value={draft} onChange={(event) => setDraft(event.target.value)} />
    </>
  );
}
it('keeps the new workspace and its visible draft after the old route request returns a late 401', async () => {
  const old = new Server();
  old.doc = { ...old.doc, id: 'old' };
  const current = new Server();
  current.doc = { ...current.doc, id: 'new' };
  let reject: (error: Error) => void = () => {};
  old.waiting = new Promise<void>((_resolve, fail) => {
    reject = fail;
  });
  const view = render(
    <SpacePermissionsProvider>
      <Workspace key="old" server={old} />
    </SpacePermissionsProvider>,
  );
  fireEvent.click(screen.getByRole('button', { name: '현재 조회' }));
  view.rerender(
    <SpacePermissionsProvider>
      <Workspace key="new" server={current} />
    </SpacePermissionsProvider>,
  );
  fireEvent.change(screen.getByRole('textbox', { name: '초안' }), { target: { value: 'new unsaved draft' } });
  await act(async () => {
    reject(new ApiError('unauthenticated', 401, 'old request expired'));
    await Promise.resolve();
  });
  expect(screen.getByRole('status').textContent).toBe('new:active');
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '초안' }).value).toBe('new unsaved draft');
  expect(screen.getByText('스페이스 권한:edit')).toBeDefined();
});

type FailedStage = 'document' | 'space' | 'merge';
function RecoveryProbe({
  server,
  session,
  failure,
}: {
  server: Server;
  session: DocumentEditorSession;
  failure: { stage: FailedStage | null };
}) {
  const [drawing, setDrawing] = useState('');
  const access = useDocumentAccess(
    document({ permission: 'edit' }),
    false,
    session,
    () =>
      failure.stage === 'document'
        ? Promise.reject(new ApiError('network', 0, 'document unavailable'))
        : Promise.resolve(server.doc),
    {
      spaces: {
        get: () =>
          failure.stage === 'space'
            ? Promise.reject(new ApiError('unknown', 503, 'space unavailable'))
            : Promise.resolve(space(server.doc.space.id, 'edit')),
        update: () => {},
      },
    },
  );
  return (
    <>
      <button onClick={() => void access.receivePermission('edit')}>편집 권한 알림</button>
      <button onClick={() => void access.receivePermission('comment')}>댓글 권한 알림</button>
      <p data-testid="verification">{access.verified ? 'verified' : 'pending'}</p>
      <p data-testid="effective-permission">{access.permission}</p>
      <textarea
        aria-label="본문 초안"
        readOnly={!access.writable}
        value={JSON.stringify(session.currentBlocks())}
        onChange={() => {}}
      />
      <textarea
        aria-label="그림 초안"
        readOnly={!access.writable}
        value={drawing}
        onChange={(event) => setDrawing(event.target.value)}
      />
      <DocumentAccessBanner access={access} onCopy={() => {}} />
    </>
  );
}
it.each(['document', 'space', 'merge'] as const)(
  'keeps input read-only after %s recovery failure and resumes on an explicit same-grant retry',
  async (stage) => {
    const server = new Server();
    const initial = server.doc;
    const session = new DocumentEditorSession(
      server as unknown as DocumentGateway,
      { now: () => new Date('2026-11-16T00:00:00Z') },
      initial,
    );
    const draft = [paragraph('aaaaaaaaaa', 'my draft'), paragraph('bbbbbbbbbb', 'other')];
    session.markEdited(draft);
    session.markMetaEdited({ title: 'my title' });
    const failure: { stage: FailedStage | null } = { stage: null };
    render(<RecoveryProbe server={server} session={session} failure={failure} />);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '그림 초안' }).readOnly).toBe(false);
    fireEvent.change(screen.getByRole('textbox', { name: '그림 초안' }), {
      target: { value: `local sketch ${stage}` },
    });
    server.doc = { ...server.doc, permission: 'comment' };
    fireEvent.click(screen.getByRole('button', { name: '댓글 권한 알림' }));
    await waitFor(() => {
      expect(screen.getByTestId('effective-permission').textContent).toBe('comment');
      expect(screen.getByTestId('verification').textContent).toBe('verified');
    });
    server.doc = {
      ...server.doc,
      permission: 'edit',
      version: 10,
      blocks: [stored(paragraph('aaaaaaaaaa', 'base')), stored(paragraph('bbbbbbbbbb', 'remote change'))],
    };
    failure.stage = stage;
    server.offline = stage === 'merge';
    fireEvent.click(screen.getByRole('button', { name: '편집 권한 알림' }));
    await screen.findByRole('alert');
    expect(screen.getByTestId('effective-permission').textContent).toBe('comment');
    expect(session.isWritable()).toBe(false);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문 초안' }).readOnly).toBe(true);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '그림 초안' }).value).toBe(
      `local sketch ${stage}`,
    );
    expect(screen.getByRole('button', { name: '입력 복사' })).toBeDefined();
    expect(session.currentBlocks()).toEqual(draft);
    expect(session.getState().meta.title).toBe('my title');
    failure.stage = null;
    server.offline = false;
    fireEvent.click(screen.getByRole('button', { name: '권한 다시 확인' }));
    await waitFor(() => expect(screen.getByTestId('effective-permission').textContent).toBe('edit'));
    expect(session.isWritable()).toBe(true);
    expect(session.getState().version).toBe(10);
    expect(session.getState().meta.title).toBe('my title');
    expect(session.currentBlocks()).toEqual([
      paragraph('aaaaaaaaaa', 'my draft'),
      paragraph('bbbbbbbbbb', 'remote change'),
    ]);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문 초안' }).readOnly).toBe(false);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '그림 초안' }).value).toBe(
      `local sketch ${stage}`,
    );
    expect(screen.queryByRole('alert')).toBeNull();
  },
);
it.each(['document', 'merge'] as const)(
  'cannot reopen writing when an old %s success finishes after revocation',
  async (stage) => {
    const server = new Server();
    const initial = { ...server.doc, permission: 'comment' as const };
    const session = new DocumentEditorSession(
      server as unknown as DocumentGateway,
      { now: () => new Date('2026-11-16T00:00:00Z') },
      initial,
    );
    const draft = [paragraph('aaaaaaaaaa', 'retained input')];
    session.markEdited(draft);
    let release: () => void = () => {};
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    if (stage === 'merge') server.waiting = pending;
    const hook = renderHook(() =>
      useDocumentAccess(
        initial,
        false,
        session,
        async () => {
          if (stage === 'document') await pending;
          return server.doc;
        },
        { spaces: { get: () => Promise.resolve(space(server.doc.space.id, 'edit')), update: () => {} } },
      ),
    );
    let recovery = Promise.resolve();
    await act(async () => {
      recovery = hook.result.current.receivePermission('edit');
      await Promise.resolve();
    });
    act(() => {
      hook.result.current.revoke('not_found');
    });
    await act(async () => {
      release();
      await recovery;
    });
    expect(hook.result.current.permission).toBeNull();
    expect(hook.result.current.writable).toBe(false);
    expect(hook.result.current.revoked).toBe('not_found');
    expect(session.isWritable()).toBe(false);
    expect(session.currentBlocks()).toEqual(draft);
  },
);
