export type MembershipRole = 'editor' | 'commenter';
export type Membership = {
  readonly id: string;
  readonly space: string;
  readonly login: string;
  readonly avatarUrl: string;
  readonly role: MembershipRole;
  readonly state: 'pending' | 'active';
  readonly userId: string | null;
  readonly invitedAt: string;
  readonly joinedAt: string | null;
};
export type MembershipInvite = {
  readonly space: string;
  readonly login: string;
  readonly role: MembershipRole;
};
export const MEMBERSHIP_ROLE_LABEL: Record<MembershipRole, string> = {
  editor: '편집자',
  commenter: '댓글 작성자',
};
export function normalizeInvite(invite: MembershipInvite): MembershipInvite {
  return { ...invite, login: invite.login.trim() };
}
export function validInvite(invite: MembershipInvite): boolean {
  return (
    invite.space.trim() !== '' &&
    /^(?=.{1,39}$)[a-z0-9]+(?:-[a-z0-9]+)*$/i.test(invite.login) &&
    (['editor', 'commenter'] as readonly string[]).includes(invite.role)
  );
}
