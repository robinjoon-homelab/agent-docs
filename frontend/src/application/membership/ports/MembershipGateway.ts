import type { Membership, MembershipInvite, MembershipRole } from '../../../domain/membership/membership';
export interface MembershipGateway {
  list(space: string): Promise<Membership[]>;
  invite(invite: MembershipInvite): Promise<Membership>;
  changeRole(id: string, role: MembershipRole): Promise<Membership>;
  revoke(id: string): Promise<void>;
}
