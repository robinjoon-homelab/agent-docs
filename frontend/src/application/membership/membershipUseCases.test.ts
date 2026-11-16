import { describe, expect, it } from 'vitest';
import { seededStore } from '../../adapters/outbound/memory/shared/seed';
import { MemoryMembershipGateway } from '../../adapters/outbound/memory/membership/MemoryMembershipGateway';
import { createMembershipUseCases } from './membershipUseCases';
describe('membership use cases', () => {
  it('normalizes invitations, changes roles and retains revoked records without simulating acceptance', async () => {
    const store = seededStore();
    const cases = createMembershipUseCases(new MemoryMembershipGateway(store));
    const invited = await cases.invite({
      space: store.spaces[0]?.id ?? '',
      login: '  bob  ',
      role: 'editor',
    });
    expect([invited.login, invited.state, invited.userId]).toEqual(['bob', 'pending', null]);
    expect((await cases.changeRole(invited.id, 'commenter')).role).toBe('commenter');
    await cases.revoke(invited.id);
    expect(await cases.list(invited.space)).toEqual([]);
    expect(store.memberships[0]?.membership.id).toBe(invited.id);
    expect(store.memberships[0]?.revokedAt).not.toBeNull();
    await expect(cases.changeRole(invited.id, 'editor')).rejects.toMatchObject({ code: 'not_found' });
    await cases.revoke(invited.id);
  });
  it('rejects invalid logins, duplicates and non-owner management', async () => {
    const store = seededStore();
    const cases = createMembershipUseCases(new MemoryMembershipGateway(store));
    const space = store.spaces[0]?.id ?? '';
    for (const login of ['', '-bob', 'bob-', 'a--b', 'a'.repeat(40)])
      await expect(cases.invite({ space, login, role: 'editor' })).rejects.toMatchObject({ code: 'invalid' });
    expect(store.memberships).toEqual([]);
    await cases.invite({ space, login: 'bob', role: 'commenter' });
    await expect(cases.invite({ space, login: 'BOB', role: 'editor' })).rejects.toMatchObject({
      code: 'conflict',
    });
    store.me = { ...store.me, owner: false };
    await expect(cases.list(space)).rejects.toMatchObject({ code: 'forbidden' });
    expect(store.memberships).toHaveLength(1);
  });
});
