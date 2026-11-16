// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { ServicesProvider, type Services } from '../../../../../config/ServicesContext';
import type { MembershipUseCases } from '../../../../../application/membership/membershipUseCases';
import { MembershipConflictError } from '../../../../../application/membership/ports/MembershipConflictError';
import { ApiError } from '../../../../../application/shared/ports/errors';
import type { Membership } from '../../../../../domain/membership/membership';
import { SpaceMembersSection } from './SpaceMembersSection';
afterEach(cleanup);
function member(): Membership {
  return {
    id: 'existing',
    space: 'space-uuid',
    login: 'old-login',
    avatarUrl: '',
    role: 'editor',
    state: 'pending',
    userId: null,
    invitedAt: '2026-11-16T00:00:00Z',
    joinedAt: null,
  };
}
function show(memberships: MembershipUseCases) {
  const services = { memberships } as unknown as Services;
  return render(
    <ServicesProvider services={services}>
      <SpaceMembersSection spaceId="space-uuid" />
    </ServicesProvider>,
  );
}
it('reloads a duplicate membership by ID and exposes its current renamed row for role editing', async () => {
  let rows = [member()];
  show({
    list: () => Promise.resolve(rows),
    invite: () => {
      rows = [
        {
          ...member(),
          login: 'renamed',
          role: 'commenter',
          state: 'active',
          userId: 'user-uuid',
          joinedAt: '2026-11-16T00:10:00Z',
        },
      ];
      return Promise.reject(
        new MembershipConflictError('existing', 'already invited', 'change the existing membership role'),
      );
    },
    changeRole: () => Promise.resolve(rows[0] ?? member()),
    revoke: () => Promise.resolve(),
  });
  await screen.findByText('@old-login');
  fireEvent.change(screen.getByRole('textbox', { name: 'GitHub 로그인' }), {
    target: { value: 'old-login' },
  });
  fireEvent.click(screen.getByRole('button', { name: '초대' }));
  await waitFor(() =>
    expect(screen.getByRole('listitem', { name: '기존 멤버 renamed' }).textContent).toContain('기존 초대'),
  );
  expect(screen.queryByText('@old-login')).toBeNull();
  expect(screen.getAllByRole('listitem')).toHaveLength(1);
  expect(screen.getByRole<HTMLSelectElement>('combobox', { name: 'renamed 역할' }).value).toBe('commenter');
  expect(screen.getByRole<HTMLInputElement>('textbox', { name: 'GitHub 로그인' }).value).toBe('old-login');
  expect(screen.getByRole('alert').textContent).toContain('change the existing membership role');
});
it('refreshes the owner list when a role change finds a revoked membership', async () => {
  let rows = [member()];
  show({
    list: () => Promise.resolve(rows),
    invite: () => Promise.resolve(member()),
    changeRole: () => {
      rows = [];
      return Promise.reject(new ApiError('not_found', 404, '이미 회수된 멤버입니다.'));
    },
    revoke: () => Promise.resolve(),
  });
  await screen.findByText('@old-login');
  fireEvent.change(screen.getByRole('combobox', { name: 'old-login 역할' }), {
    target: { value: 'commenter' },
  });
  await screen.findByText('초대한 멤버가 없습니다.');
  expect(screen.queryByText('@old-login')).toBeNull();
  expect(screen.queryAllByRole('listitem')).toHaveLength(0);
  expect(screen.getByRole('alert').textContent).toBe('이미 회수된 멤버입니다.');
});
