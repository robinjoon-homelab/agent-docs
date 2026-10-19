import { describe, expect, it } from 'vitest';
import type { Me } from '../../domain/session/me';
import { ApiError } from '../shared/ports/errors';
import type { SessionGateway } from './ports/SessionGateway';
import { createSessionUseCases } from './sessionUseCases';

const me: Me = { userId: 'u1', login: 'octocat', name: 'Octo', avatarUrl: '', owner: true };

function gateway(result: Me | ApiError): SessionGateway {
  return {
    me: () => (result instanceof ApiError ? Promise.reject(result) : Promise.resolve(result)),
    logout: () => Promise.resolve(),
  };
}

describe('currentUser', () => {
  it('returns the signed-in user', async () => {
    expect(await createSessionUseCases(gateway(me)).currentUser()).toEqual(me);
  });

  it('returns null without a session and rethrows other failures', async () => {
    const anonymous = createSessionUseCases(gateway(new ApiError('unauthenticated', 401, '')));
    expect(await anonymous.currentUser()).toBeNull();
    const broken = createSessionUseCases(gateway(new ApiError('network', 0, 'offline')));
    await expect(broken.currentUser()).rejects.toThrow('offline');
  });
});
