import { MembershipConflictError } from '../../../../application/membership/ports/MembershipConflictError';
import type { MembershipGateway } from '../../../../application/membership/ports/MembershipGateway';
import type { Membership, MembershipInvite, MembershipRole } from '../../../../domain/membership/membership';
import type { HttpClient } from '../shared/httpClient';
import { ProblemError, query, segment } from '../shared/httpClient';
import { list, obj, oneOf, optStr, str } from '../shared/json';
export function toMembership(value: unknown): Membership {
  const source = obj(value, 'membership');
  return {
    id: str(source, 'id'),
    space: str(source, 'space'),
    login: str(source, 'login'),
    avatarUrl: str(source, 'avatarUrl'),
    role: oneOf(source, 'role', ['editor', 'commenter'] as const),
    state: oneOf(source, 'state', ['pending', 'active'] as const),
    userId: optStr(source, 'userId'),
    invitedAt: str(source, 'invitedAt'),
    joinedAt: optStr(source, 'joinedAt'),
  };
}
export class HttpMembershipGateway implements MembershipGateway {
  constructor(private readonly http: HttpClient) {}
  async list(space: string): Promise<Membership[]> {
    return list(await this.http.get(`/api/memberships${query({ space })}`), toMembership);
  }
  async invite(invite: MembershipInvite): Promise<Membership> {
    try {
      return toMembership(
        await this.http.post('/api/memberships', {
          space: invite.space,
          login: invite.login,
          role: invite.role,
        }),
      );
    } catch (error) {
      if (
        error instanceof ProblemError &&
        error.code === 'conflict' &&
        typeof error.body.membershipId === 'string'
      ) {
        throw new MembershipConflictError(error.body.membershipId, error.message, error.fix);
      }
      throw error;
    }
  }

  async changeRole(id: string, role: MembershipRole): Promise<Membership> {
    return toMembership(await this.http.patch(`/api/memberships/${segment(id)}`, { role }));
  }
  async revoke(id: string): Promise<void> {
    await this.http.delete(`/api/memberships/${segment(id)}`);
  }
}
