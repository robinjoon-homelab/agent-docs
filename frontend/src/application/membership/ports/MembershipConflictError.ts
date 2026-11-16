import { ApiError } from '../../shared/ports/errors';
/** A duplicate invitation identifies the existing membership, including after a GitHub rename. */
export class MembershipConflictError extends ApiError {
  readonly body: Readonly<{ membershipId: string }>;
  constructor(
    readonly membershipId: string,
    message: string,
    fix: string | null = null,
  ) {
    super('conflict', 409, message, fix);
    this.name = 'MembershipConflictError';
    this.body = { membershipId };
  }
}
