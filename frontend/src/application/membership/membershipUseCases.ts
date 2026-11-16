import type { Membership, MembershipInvite, MembershipRole } from '../../domain/membership/membership';
import { normalizeInvite, validInvite } from '../../domain/membership/membership';
import { ApiError } from '../shared/ports/errors';
import type { MembershipGateway } from './ports/MembershipGateway';
export interface MembershipUseCases {
  list(space: string): Promise<Membership[]>;
  invite(invite: MembershipInvite): Promise<Membership>;
  changeRole(id: string, role: MembershipRole): Promise<Membership>;
  revoke(id: string): Promise<void>;
}
export function createMembershipUseCases(gateway: MembershipGateway): MembershipUseCases {
  return {
    list: (space) => gateway.list(space),
    async invite(invite) {
      const normalized = normalizeInvite(invite);
      if (!validInvite(normalized))
        throw new ApiError(
          'invalid',
          400,
          'GitHub 로그인과 역할을 확인하세요. 로그인은 영문·숫자·안쪽 하이픈으로 39자 이하입니다.',
        );
      return gateway.invite(normalized);
    },
    changeRole: (id, role) => gateway.changeRole(id, role),
    revoke: (id) => gateway.revoke(id),
  };
}
