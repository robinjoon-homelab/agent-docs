import { describe, expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpMembershipGateway } from './HttpMembershipGateway';
const member = {
  id: 'm1',
  space: 'space-uuid',
  login: 'bob',
  avatarUrl: 'https://github.com/bob.png',
  role: 'editor',
  state: 'pending',
  userId: null,
  invitedAt: '2026-11-16T00:00:00Z',
  joinedAt: null,
};
function response(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
describe('membership HTTP', () => {
  it('uses the internal space UUID and exact mutation payloads with CSRF', async () => {
    const http = new HttpClient({
      cookies: () => 'XSRF-TOKEN=csrf',
      fetch: (url, init) => {
        if (init?.method === 'GET' && url === '/api/memberships?space=space-uuid')
          return Promise.resolve(response([member]));
        if (new Headers(init?.headers).get('X-XSRF-TOKEN') !== 'csrf')
          return Promise.reject(new Error('missing CSRF'));
        if (
          url === '/api/memberships' &&
          init?.method === 'POST' &&
          init.body === JSON.stringify({ space: member.space, login: 'bob', role: 'editor' })
        )
          return Promise.resolve(response(member, 201));
        if (url === '/api/memberships/m1' && init?.method === 'PATCH' && init.body === '{"role":"commenter"}')
          return Promise.resolve(response({ ...member, role: 'commenter' }));
        if (url === '/api/memberships/m1' && init?.method === 'DELETE' && init.body === undefined)
          return Promise.resolve(response(null, 204));
        return Promise.reject(new Error('wrong HTTP contract'));
      },
    });
    const gateway = new HttpMembershipGateway(http);
    expect(await gateway.list(member.space)).toEqual([member]);
    expect(await gateway.invite({ space: member.space, login: 'bob', role: 'editor' })).toEqual(member);
    expect((await gateway.changeRole('m1', 'commenter')).role).toBe('commenter');
    await expect(gateway.revoke('m1')).resolves.toBeUndefined();
  });
  it('preserves duplicate conflict guidance and forbidden results', async () => {
    const gateway = (status: number, body: unknown) =>
      new HttpMembershipGateway(
        new HttpClient({ cookies: () => '', fetch: () => Promise.resolve(response(body, status)) }),
      );
    await expect(
      gateway(409, {
        code: 'conflict',
        detail: 'duplicate',
        fix: 'change existing role',
        membershipId: 'm1',
      }).invite({ space: member.space, login: 'bob', role: 'editor' }),
    ).rejects.toMatchObject({
      code: 'conflict',
      membershipId: 'm1',
      fix: 'change existing role',
      body: { membershipId: 'm1' },
    });
    await expect(gateway(403, { code: 'forbidden' }).list(member.space)).rejects.toMatchObject({
      code: 'forbidden',
    });
  });
});
