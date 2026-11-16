// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import { RequireOwner } from './ServiceRoutes';
import { SessionProvider } from './SessionContext';
afterEach(cleanup);
it('keeps owner-only screens out of member routes', () => {
  const me = { userId: 'm', login: 'member', name: 'Member', avatarUrl: '', owner: false };
  const session = { me, inboxVersion: 0, refreshInbox: () => {} };
  const view = render(
    <MemoryRouter>
      <SessionProvider session={session}>
        <RequireOwner>
          <p>멤버 관리 및 연결 승인</p>
        </RequireOwner>
      </SessionProvider>
    </MemoryRouter>,
  );
  expect(screen.queryByText('멤버 관리 및 연결 승인')).toBeNull();
  expect(screen.getByRole('heading').textContent).toBe('찾을 수 없습니다');
  view.rerender(
    <MemoryRouter>
      <SessionProvider session={{ ...session, me: { ...me, owner: true } }}>
        <RequireOwner>
          <p>멤버 관리 및 연결 승인</p>
        </RequireOwner>
      </SessionProvider>
    </MemoryRouter>,
  );
  expect(screen.getByText('멤버 관리 및 연결 승인')).toBeDefined();
});
