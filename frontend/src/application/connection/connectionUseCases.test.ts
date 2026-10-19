import { describe, expect, it } from 'vitest';
import type { Connection } from '../../domain/connection/connection';
import type { Clock } from '../shared/ports/Clock';
import { createConnectionUseCases } from './connectionUseCases';
import type { ConnectionGateway, OAuthRequestGateway } from './ports/ConnectionGateway';

const clock: Clock = { now: () => new Date('2026-10-18T00:00:00Z') };

function connection(overrides: Partial<Connection>): Connection {
  return {
    id: 'c',
    kind: 'agent',
    name: 'Claude Code',
    clientId: 'client',
    clientHost: 'claude.ai',
    spaceIds: [],
    scope: 'edit',
    createdAt: '2026-10-01T00:00:00Z',
    lastUsedAt: null,
    expiresAt: '2027-01-01T00:00:00Z',
    revokedAt: null,
    ...overrides,
  };
}

describe('connection use cases', () => {
  it('lists active connections first, most recently used first', async () => {
    const connections = {
      list: () =>
        Promise.resolve([
          connection({
            id: 'revoked',
            revokedAt: '2026-10-10T00:00:00Z',
            lastUsedAt: '2026-10-17T00:00:00Z',
          }),
          connection({ id: 'idle', lastUsedAt: null }),
          connection({ id: 'busy', lastUsedAt: '2026-10-16T00:00:00Z' }),
          connection({ id: 'expired', expiresAt: '2026-10-01T00:00:00Z' }),
        ]),
    } as unknown as ConnectionGateway;
    const useCases = createConnectionUseCases(connections, {} as OAuthRequestGateway, clock);
    expect((await useCases.list()).map((c) => c.id)).toEqual(['busy', 'idle', 'revoked', 'expired']);
  });

  it('approves only with at least one space and returns where to send the browser', async () => {
    const requests = {
      approve: () => Promise.resolve('https://claude.ai/callback?code=x'),
    } as unknown as OAuthRequestGateway;
    const useCases = createConnectionUseCases({} as ConnectionGateway, requests, clock);
    expect(await useCases.approve('r1', { spaceIds: [], scope: 'read', expiresInDays: 90 })).toEqual({
      ok: false,
      error: ['no_spaces'],
    });
    expect(await useCases.approve('r1', { spaceIds: ['s1'], scope: 'read', expiresInDays: 90 })).toEqual({
      ok: true,
      value: 'https://claude.ai/callback?code=x',
    });
  });
});
