// @vitest-environment jsdom
import { useMemo, useState } from 'react';
import { act, cleanup, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { ApiError } from '../../../../application/shared/ports/errors';
import { document } from '../../../../test-support/fixtures';
import { useDocumentAccess } from '../pages/document/useDocumentAccess';
import { RetainedDocumentContext, type RetainedDocument } from './RetainedDocumentContext';
import { useLoad } from './useLoad';
afterEach(cleanup);
class Session {
  writable = true;
  constructor(readonly id: string) {}
  setWritable(writable: boolean) {
    this.writable = writable;
  }
  refreshRemote() {
    return Promise.resolve();
  }
  getState() {
    return { status: 'pending' };
  }
}
function Details({ id, loader }: { id: string; loader: () => Promise<string> }) {
  const result = useLoad(`details:${id}`, loader);
  return result.state === 'error' ? <p role="alert">조회 실패</p> : <p>{result.data ?? 'loading'}</p>;
}
function Opened({ id, loader }: { id: string; loader: () => Promise<string> }) {
  const session = useMemo(() => new Session(id), [id]);
  const doc = document({ id, permission: 'edit' });
  const access = useDocumentAccess(doc, false, session, () => Promise.resolve(doc));
  const [draft, setDraft] = useState('unsaved input');
  return (
    <RetainedDocumentContext
      value={{
        documentId: id,
        revoked: access.revoked !== null,
        canLoad: () => access.currentAccess().revoked === null,
        unauthenticated: () => access.revoke('unauthenticated'),
      }}
    >
      <p role="status">
        {id}:{access.revoked ?? 'active'}:{session.getState().status}
      </p>
      <textarea
        aria-label="초안"
        readOnly={!session.writable}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
      />
      <Details id={id} loader={loader} />
    </RetainedDocumentContext>
  );
}
it('keeps an opened document draft mounted and read-only when its auxiliary query returns 401', async () => {
  render(
    <Opened id="opened" loader={() => Promise.reject(new ApiError('unauthenticated', 401, 'expired'))} />,
  );
  await screen.findByRole('alert');
  expect(screen.getByRole('status').textContent).toBe('opened:unauthenticated:pending');
  const draft = screen.getByRole<HTMLTextAreaElement>('textbox', { name: '초안' });
  expect(draft.readOnly).toBe(true);
  expect(draft.value).toBe('unsaved input');
});
it('does not expose new data once the open document has been revoked', async () => {
  const retention: RetainedDocument = {
    documentId: 'opened',
    revoked: true,
    canLoad: () => false,
    unauthenticated: () => {},
  };
  const hook = renderHook(() => useLoad('new-query', () => Promise.resolve('must not appear')), {
    wrapper: ({ children }) => (
      <RetainedDocumentContext value={retention}>{children}</RetainedDocumentContext>
    ),
  });
  await act(async () => {
    await Promise.resolve();
  });
  expect(hook.result.current.state).toBe('loading');
  expect(hook.result.current.data).toBeNull();
});
it('keeps the next workspace active and preserves its visible draft after an old auxiliary request returns 401', async () => {
  let reject: (error: Error) => void = () => {};
  const pending = new Promise<string>((_resolve, fail) => {
    reject = fail;
  });
  const view = render(<Opened key="old" id="old" loader={() => pending} />);
  view.rerender(<Opened key="new" id="new" loader={() => Promise.resolve('new document data')} />);
  await screen.findByText('new document data');
  fireEvent.change(screen.getByRole('textbox', { name: '초안' }), { target: { value: 'new local draft' } });
  await act(async () => {
    reject(new ApiError('unauthenticated', 401, 'old request expired'));
    await Promise.resolve();
  });
  expect(screen.getByRole('status').textContent).toBe('new:active:pending');
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '초안' }).value).toBe('new local draft');
  expect(screen.getByText('new document data')).toBeDefined();
});
it('keeps ordinary loads unchanged outside the retained workspace', async () => {
  const hook = renderHook(() => useLoad('initial', () => Promise.resolve('fresh data')));
  await act(async () => {
    await Promise.resolve();
  });
  expect(hook.result.current.data).toBe('fresh data');
});
