// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { SessionProvider } from '../../app/SessionContext';
import { DocumentPermissionContext } from './DocumentPermissionContext';
import { ThreadCollaboration } from './ThreadCollaboration';
import { thread } from '../../../../../test-support/fixtures';
afterEach(cleanup);
it('lets a member editor decide suggestions and removes those controls on downgrade or revocation', () => {
  const session = {
    me: { userId: 'member', login: 'bob', name: 'Bob', avatarUrl: '', owner: false },
    inboxVersion: 0,
    refreshInbox: () => {},
  };
  const value = thread({
    suggestions: [
      {
        commentId: 'c1',
        anchor: { kind: 'block', blockId: 'aaaaaaaaaa' },
        hash: 'h',
        before: 'old',
        after: 'new',
        decision: null,
      },
    ],
  });
  const child = <ThreadCollaboration thread={value} login="bob" actions={{}} />;
  const view = render(
    <SessionProvider session={session}>
      <DocumentPermissionContext value="edit">{child}</DocumentPermissionContext>
    </SessionProvider>,
  );
  expect(screen.getByRole('button', { name: '수락' })).toBeDefined();
  view.rerender(
    <SessionProvider session={session}>
      <DocumentPermissionContext value="comment">{child}</DocumentPermissionContext>
    </SessionProvider>,
  );
  expect(screen.queryByRole('button', { name: '수락' })).toBeNull();
  expect(screen.getByRole<HTMLButtonElement>('button', { name: '👍 반응' }).disabled).toBe(false);
  view.rerender(
    <SessionProvider session={{ ...session, me: { ...session.me, owner: true } }}>
      <DocumentPermissionContext value={null}>{child}</DocumentPermissionContext>
    </SessionProvider>,
  );
  expect(screen.queryByRole('button', { name: '수락' })).toBeNull();
  expect(screen.getByRole<HTMLButtonElement>('button', { name: '👍 반응' }).disabled).toBe(true);
});
