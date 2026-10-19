import type { Me } from '../../../domain/session/me';

export interface SessionGateway {
  /** Throws ApiError('unauthenticated') when there is no session. */
  me(): Promise<Me>;
  logout(): Promise<void>;
}
