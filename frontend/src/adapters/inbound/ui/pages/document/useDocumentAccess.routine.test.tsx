// @vitest-environment jsdom
import { useState, useSyncExternalStore } from 'react';
import { MemoryRouter } from 'react-router';
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { DocumentEditorSession } from '../../../../../application/document/editorSession';
import type {
  DocumentGateway,
  SaveContentResult,
} from '../../../../../application/document/ports/DocumentGateway';
import { ApiError } from '../../../../../application/shared/ports/errors';
import { document, paragraph, stored } from '../../../../../test-support/fixtures';
import type { AgentDocument } from '../../../../../domain/document/document';
import type { Block } from '../../../../../domain/document/block';
import type { Space } from '../../../../../domain/space/space';
import { permissionInSpace } from '../../../../../domain/space/permission';
import { SpacePermissionsProvider, useSpacePermissions } from '../../app/SpacePermissionsContext';
import { ToastProvider, useToast } from '../../common/Toast';
import { errorMessage } from '../../common/States';
import { ConflictPanel } from './ConflictPanel';
import { DocumentAccessBanner } from './DocumentAccessBanner';
import { DocumentHeader } from './DocumentHeader';
import { useDocumentAccess } from './useDocumentAccess';
afterEach(cleanup);
class Server {
  doc = document({
    permission: 'owner',
    blocks: [stored(paragraph('aaaaaaaaaa', 'base')), stored(paragraph('bbbbbbbbbb', 'other'))],
  });
  offline = false;
  saveOffline = false;
  get(): Promise<AgentDocument> {
    return this.offline
      ? Promise.reject(new ApiError('network', 0, 'merge unavailable'))
      : Promise.resolve(this.doc);
  }
  saveContent(_id: string, version: number, blocks: readonly Block[]): Promise<SaveContentResult> {
    if (this.saveOffline) return Promise.reject(new ApiError('network', 0, 'save unavailable'));
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
}
function space(permission: Space['permission'] = 'owner'): Space {
  return {
    id: 'sp-1',
    slug: 'team',
    name: 'Team',
    permission,
    description: '',
    links: [],
    createdAt: '',
    archivedAt: null,
    lastActivityAt: null,
    documentCount: 0,
  };
}
function open(server: Server) {
  const initial = server.doc;
  const session = new DocumentEditorSession(
    server as unknown as DocumentGateway,
    { now: () => new Date('2026-11-16T00:00:00Z') },
    initial,
  );
  session.markEdited([paragraph('aaaaaaaaaa', 'local draft'), paragraph('bbbbbbbbbb', 'other')]);
  return { initial, session };
}
function Probe({
  initial,
  session,
  server,
  faults,
}: {
  initial: AgentDocument;
  session: DocumentEditorSession;
  server: Server;
  faults: { stage: 'document' | 'space' | null };
}) {
  const toast = useToast();
  const [drawing, setDrawing] = useState('');
  const state = useSyncExternalStore(session.subscribe, session.getState);
  const access = useDocumentAccess(
    initial,
    true,
    session,
    () =>
      faults.stage === 'document'
        ? Promise.reject(new ApiError('network', 0, 'document unavailable'))
        : Promise.resolve(server.doc),
    {
      onError: (failure) => toast(errorMessage(failure)),
      spaces: {
        get: () =>
          faults.stage === 'space'
            ? Promise.reject(new ApiError('unknown', 503, 'space unavailable'))
            : Promise.resolve(space(server.doc.permission)),
        update: () => {},
      },
    },
  );
  const first = session.currentBlocks()[0];
  return (
    <>
      <button onClick={() => void access.refresh()}>원격 새로고침</button>
      <button onClick={() => void access.receivePermission('comment')}>댓글만</button>
      <button onClick={() => void access.receivePermission('edit')}>편집 허용</button>
      <p data-testid="permission">{access.permission}</p>
      <p data-testid="verified">{String(access.verified)}</p>
      <p data-testid="baseline">{state.version}</p>
      <DocumentHeader
        document={initial}
        crumbs={[]}
        state={state}
        editable={access.writable}
        onPatch={() => {}}
        onInput={() => {}}
        onCancelInput={() => {}}
        onTransfer={() => {}}
        onTrash={() => {}}
        onRetrySave={() => {
          if (session.isWritable()) void session.saveContent(session.currentBlocks());
        }}
      />
      <textarea
        aria-label="본문"
        readOnly={!access.writable}
        value={first?.type === 'paragraph' ? first.text : ''}
        onChange={(event) =>
          session.markEdited([
            paragraph('aaaaaaaaaa', event.target.value),
            ...session.currentBlocks().slice(1),
          ])
        }
      />
      <textarea
        aria-label="로컬 그림 입력"
        readOnly={!access.writable}
        value={drawing}
        onChange={(event) => setDrawing(event.target.value)}
      />
      <DocumentAccessBanner access={access} onCopy={() => {}} />
      {state.status === 'conflict' && access.verified ? (
        <ConflictPanel
          conflicts={state.conflicts}
          mine={session.currentBlocks()}
          onResolve={async (choices) => {
            if (!access.beginConflictResolution()) return;
            const result = await session.resolveConflicts(choices);
            if (result.outcome === 'saved') await access.refresh();
          }}
        />
      ) : null}
    </>
  );
}
function show(
  initial: AgentDocument,
  session: DocumentEditorSession,
  server: Server,
  faults: { stage: 'document' | 'space' | null },
) {
  return render(
    <MemoryRouter>
      <ToastProvider>
        <Probe initial={initial} session={session} server={server} faults={faults} />
      </ToastProvider>
    </MemoryRouter>,
  );
}
it('keeps owner editing enabled during a routine same-block conflict while the session pauses saving', async () => {
  const server = new Server();
  const { initial, session } = open(server);
  show(initial, session, server, { stage: null });
  fireEvent.change(screen.getByRole('textbox', { name: '로컬 그림 입력' }), {
    target: { value: 'user changed local sketch' },
  });
  server.doc = {
    ...server.doc,
    version: 4,
    blocks: [stored(paragraph('aaaaaaaaaa', 'remote edit')), stored(paragraph('bbbbbbbbbb', 'other'))],
  };
  fireEvent.click(screen.getByRole('button', { name: '원격 새로고침' }));
  await screen.findByRole('alert', { name: '저장 충돌' });
  expect(session.isWritable()).toBe(true);
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문' }).readOnly).toBe(false);
  expect(screen.queryByRole('button', { name: '입력 복사' })).toBeNull();
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '로컬 그림 입력' }).value).toBe(
    'user changed local sketch',
  );
  expect(await session.saveContent(session.currentBlocks())).toBe('conflict');
  expect(server.doc.version).toBe(4);
  expect(session.currentBlocks()[0]).toEqual(paragraph('aaaaaaaaaa', 'local draft'));
});
it.each(['document', 'space', 'merge'] as const)(
  'keeps known owner editing and local input after a routine %s failure',
  async (stage) => {
    const server = new Server();
    const { initial, session } = open(server);
    const faults: { stage: 'document' | 'space' | null } = { stage: stage === 'merge' ? null : stage };
    server.offline = stage === 'merge';
    show(initial, session, server, faults);
    fireEvent.change(screen.getByRole('textbox', { name: '로컬 그림 입력' }), {
      target: { value: `typed sketch ${stage}` },
    });
    server.doc = {
      ...server.doc,
      version: 4,
      blocks: [stored(paragraph('aaaaaaaaaa', 'base')), stored(paragraph('bbbbbbbbbb', 'remote other'))],
    };
    fireEvent.click(screen.getByRole('button', { name: '원격 새로고침' }));
    await screen.findByText(`${stage} unavailable`);
    if (stage === 'space') await waitFor(() => expect(session.getState().version).toBe(4));
    expect(session.isWritable()).toBe(true);
    expect(screen.getByTestId('permission').textContent).toBe('owner');
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문' }).readOnly).toBe(false);
    expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '로컬 그림 입력' }).value).toBe(
      `typed sketch ${stage}`,
    );
    expect(screen.queryByRole('button', { name: '권한 다시 확인' })).toBeNull();
    expect(screen.queryByRole('button', { name: '입력 복사' })).toBeNull();
    faults.stage = null;
    server.offline = false;
    fireEvent.click(screen.getByRole('button', { name: '원격 새로고침' }));
    await waitFor(() => expect(session.getState().version).toBe(4));
    expect(session.currentBlocks()).toEqual([
      paragraph('aaaaaaaaaa', 'local draft'),
      paragraph('bbbbbbbbbb', 'remote other'),
    ]);
  },
);
it('keeps UI and session writable after a recovered conflict save fails and retries the preserved merged input', async () => {
  const server = new Server();
  const { initial, session } = open(server);
  show(initial, session, server, { stage: null });
  fireEvent.change(screen.getByRole('textbox', { name: '로컬 그림 입력' }), {
    target: { value: 'sketch edited before downgrade' },
  });
  server.doc = { ...server.doc, permission: 'comment' };
  fireEvent.click(screen.getByRole('button', { name: '댓글만' }));
  await waitFor(() => {
    expect(screen.getByTestId('permission').textContent).toBe('comment');
    expect(screen.getByTestId('verified').textContent).toBe('true');
  });
  server.doc = {
    ...server.doc,
    permission: 'edit',
    version: 4,
    blocks: [stored(paragraph('aaaaaaaaaa', 'remote edit')), stored(paragraph('bbbbbbbbbb', 'other'))],
  };
  fireEvent.click(screen.getByRole('button', { name: '편집 허용' }));
  await screen.findByRole('alert', { name: '저장 충돌' });
  expect(session.isWritable()).toBe(false);
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문' }).readOnly).toBe(true);
  server.saveOffline = true;
  fireEvent.click(screen.getByRole('radio', { name: /내 버전/ }));
  fireEvent.click(screen.getByRole('button', { name: '선택대로 저장' }));
  await screen.findByRole('button', { name: '다시 저장' });
  expect(session.getState().status).toBe('error');
  expect(session.isWritable()).toBe(true);
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문' }).readOnly).toBe(false);
  expect(session.currentBlocks()[0]).toEqual(paragraph('aaaaaaaaaa', 'local draft'));
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '로컬 그림 입력' }).value).toBe(
    'sketch edited before downgrade',
  );
  server.saveOffline = false;
  fireEvent.click(screen.getByRole('button', { name: '다시 저장' }));
  await waitFor(() => expect(session.getState().status).toBe('saved'));
  expect(server.doc.version).toBe(5);
  expect(server.doc.blocks[0]).toMatchObject(paragraph('aaaaaaaaaa', 'local draft'));
  expect(screen.queryByRole('button', { name: '다시 저장' })).toBeNull();
});
it('ignores a late best-effort space response after document revocation', async () => {
  const server = new Server();
  const { initial, session } = open(server);
  let release: (value: Space) => void = () => {};
  const pending = new Promise<Space>((resolve) => {
    release = resolve;
  });
  const hook = renderHook(
    () => {
      const live = useSpacePermissions();
      const access = useDocumentAccess(initial, true, session, () => Promise.resolve(server.doc), {
        spaces: { get: () => pending, update: live.update },
      });
      return { access, navigation: permissionInSpace(space(), true, live.grants) };
    },
    { wrapper: SpacePermissionsProvider },
  );
  await act(async () => {
    await hook.result.current.access.refresh();
  });
  act(() => {
    hook.result.current.access.revoke('not_found');
  });
  await act(async () => {
    release(space('comment'));
    await pending;
  });
  expect(hook.result.current.navigation).toBe('owner');
  expect(hook.result.current.access.permission).toBeNull();
  expect(session.isWritable()).toBe(false);
});

it('treats an actual grant change found by routine refresh as a safe permission transition', async () => {
  const server = new Server();
  const { initial, session } = open(server);
  show(initial, session, server, { stage: null });
  fireEvent.change(screen.getByRole('textbox', { name: '로컬 그림 입력' }), {
    target: { value: 'entered before permission change' },
  });
  server.doc = { ...server.doc, permission: 'comment' };
  fireEvent.click(screen.getByRole('button', { name: '원격 새로고침' }));
  await waitFor(() => {
    expect(screen.getByTestId('permission').textContent).toBe('comment');
    expect(screen.getByTestId('verified').textContent).toBe('true');
  });
  expect(session.isWritable()).toBe(false);
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '본문' }).readOnly).toBe(true);
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '로컬 그림 입력' }).value).toBe(
    'entered before permission change',
  );
  expect(screen.getByRole('button', { name: '입력 복사' })).toBeDefined();
  expect(session.currentBlocks()[0]).toEqual(paragraph('aaaaaaaaaa', 'local draft'));
});
it.each(['not_found', 'unauthenticated'] as const)(
  'still closes writing on actual routine document %s',
  async (code) => {
    const server = new Server();
    const { initial, session } = open(server);
    const hook = renderHook(() =>
      useDocumentAccess(initial, true, session, () =>
        Promise.reject(new ApiError(code, code === 'not_found' ? 404 : 401, 'access ended')),
      ),
    );
    await act(async () => {
      await hook.result.current.refresh();
    });
    expect(hook.result.current.revoked).toBe(code);
    expect(hook.result.current.permission).toBeNull();
    expect(hook.result.current.writable).toBe(false);
    expect(session.isWritable()).toBe(false);
    expect(session.currentBlocks()[0]).toEqual(paragraph('aaaaaaaaaa', 'local draft'));
  },
);
