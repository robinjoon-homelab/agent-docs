import type {
  ConnectionGateway,
  ConnectionPatch,
  OAuthRequestGateway,
} from '../../../../application/connection/ports/ConnectionGateway';
import { ApiError } from '../../../../application/shared/ports/errors';
import type {
  Approval,
  Connection,
  IssuedToken,
  OAuthRequest,
  ScriptTokenRequest,
} from '../../../../domain/connection/connection';
import type { MemoryStore } from '../shared/store';
import { respond } from '../shared/store';

const DAY = 24 * 60 * 60 * 1000;

export class MemoryConnectionGateway implements ConnectionGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  list(): Promise<Connection[]> {
    return respond(() => this.store.connections);
  }

  issueScriptToken(request: ScriptTokenRequest): Promise<IssuedToken> {
    return respond(() => {
      const connection: Connection = {
        id: this.store.nextId('conn'),
        kind: 'script',
        name: request.name,
        clientId: null,
        clientHost: null,
        spaceIds: [...request.spaceIds],
        scope: request.scope,
        createdAt: this.store.now(),
        lastUsedAt: null,
        expiresAt: new Date(Date.now() + request.expiresInDays * DAY).toISOString(),
        revokedAt: null,
      };
      this.store.connections.push(connection);
      return { connection, token: `adt_fake_${connection.id}_${Math.random().toString(36).slice(2, 10)}` };
    });
  }

  update(connectionId: string, patch: ConnectionPatch): Promise<Connection> {
    return respond(() => {
      const current = this.find(connectionId);
      const updated: Connection = {
        ...current,
        scope: patch.scope ?? current.scope,
        spaceIds: patch.spaceIds === undefined ? current.spaceIds : [...patch.spaceIds],
      };
      this.store.connections = this.store.connections.map((c) => (c.id === connectionId ? updated : c));
      return updated;
    });
  }

  revoke(connectionId: string): Promise<void> {
    return respond(() => {
      const revokedAt = this.store.now();
      this.find(connectionId);
      this.store.connections = this.store.connections.map((c) =>
        c.id === connectionId ? { ...c, revokedAt } : c,
      );
    });
  }

  private find(connectionId: string): Connection {
    const connection = this.store.connections.find((candidate) => candidate.id === connectionId);
    if (connection === undefined) throw new ApiError('not_found', 404, '연결을 찾을 수 없습니다.');
    return connection;
  }
}

export class MemoryOAuthRequestGateway implements OAuthRequestGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  get(requestId: string): Promise<OAuthRequest> {
    return respond(() => this.find(requestId));
  }

  approve(requestId: string, approval: Approval): Promise<string> {
    return respond(() => {
      const request = this.find(requestId);
      this.store.connections.push({
        id: this.store.nextId('conn'),
        kind: 'agent',
        name: request.client.name,
        clientId: request.client.id,
        clientHost: request.client.host,
        spaceIds: [...approval.spaceIds],
        scope: approval.scope,
        createdAt: this.store.now(),
        lastUsedAt: null,
        expiresAt: new Date(Date.now() + approval.expiresInDays * DAY).toISOString(),
        revokedAt: null,
      });
      this.store.oauthRequests.delete(requestId);
      return '/settings/agents?approved=1';
    });
  }

  deny(requestId: string): Promise<string> {
    return respond(() => {
      this.find(requestId);
      this.store.oauthRequests.delete(requestId);
      return '/settings/agents?denied=1';
    });
  }

  private find(requestId: string): OAuthRequest {
    const request = this.store.oauthRequests.get(requestId);
    if (request === undefined)
      throw new ApiError('not_found', 404, '연결 요청을 찾을 수 없거나 만료되었습니다.');
    return request;
  }
}
