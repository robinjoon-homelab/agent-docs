import type { Me } from '../../domain/session/me';
import { isApiError } from '../shared/ports/errors';
import type { SessionGateway } from './ports/SessionGateway';

export interface SessionUseCases {
  /** The signed-in user, or null when the browser has no session. */
  currentUser(): Promise<Me | null>;
  logout(): Promise<void>;
}

export function createSessionUseCases(gateway: SessionGateway): SessionUseCases {
  return {
    async currentUser() {
      try {
        return await gateway.me();
      } catch (error) {
        if (isApiError(error, 'unauthenticated')) return null;
        throw error;
      }
    },
    logout: () => gateway.logout(),
  };
}
