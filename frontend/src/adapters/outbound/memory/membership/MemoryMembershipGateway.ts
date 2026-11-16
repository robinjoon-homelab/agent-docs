import { MembershipConflictError } from '../../../../application/membership/ports/MembershipConflictError';
import type { MembershipGateway } from '../../../../application/membership/ports/MembershipGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type { Membership, MembershipInvite, MembershipRole } from '../../../../domain/membership/membership';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';
export class MemoryMembershipGateway implements MembershipGateway {
  constructor(private readonly store: MemoryStore) {}
  list(space: string): Promise<Membership[]> {
    return respond(() => {
      this.owner();
      return this.store.memberships
        .filter((m) => m.space === space && m.revokedAt === null)
        .map(({ membership }) => membership);
    });
  }
  invite(invite: MembershipInvite): Promise<Membership> {
    return respond(() => {
      this.owner();
      if (invite.login.toLowerCase() === this.store.me.login.toLowerCase())
        throw new ApiError('invalid', 400, '소유자는 초대할 수 없습니다.');
      const existing = this.store.memberships.find(
        (m) =>
          m.space === invite.space &&
          m.revokedAt === null &&
          m.membership.login.toLowerCase() === invite.login.toLowerCase(),
      );
      if (existing !== undefined)
        throw new MembershipConflictError(
          existing.membership.id,
          '이미 초대한 멤버입니다.',
          '기존 멤버의 역할을 변경하세요.',
        );
      const membership: Membership = {
        ...invite,
        id: this.store.nextId('membership'),
        avatarUrl: `https://github.com/${invite.login}.png`,
        state: 'pending',
        userId: null,
        invitedAt: this.store.now(),
        joinedAt: null,
      };
      this.store.memberships.push({ space: invite.space, membership, revokedAt: null });
      return membership;
    });
  }
  changeRole(id: string, role: MembershipRole): Promise<Membership> {
    return respond(() => {
      this.owner();
      const record = this.store.memberships.find((m) => m.membership.id === id && m.revokedAt === null);
      if (!record) throw new ApiError('not_found', 404, '멤버를 찾을 수 없습니다.');
      record.membership = { ...record.membership, role };
      return record.membership;
    });
  }
  revoke(id: string): Promise<void> {
    return respond(() => {
      this.owner();
      const record = this.store.memberships.find((m) => m.membership.id === id);
      if (!record) throw new ApiError('not_found', 404, '멤버를 찾을 수 없습니다.');
      record.revokedAt ??= this.store.now();
    });
  }
  private owner(): void {
    if (!this.store.me.owner) throw new ApiError('forbidden', 403, '소유자만 멤버를 관리할 수 있습니다.');
  }
}
