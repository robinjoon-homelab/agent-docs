import type {
  ConnectionGateway,
  ConnectionPatch,
  OAuthRequestGateway,
} from '../../../../application/connection/ports/ConnectionGateway';
import type {
  Approval,
  Connection,
  ConnectionScope,
  IssuedToken,
  OAuthRequest,
  ScriptTokenRequest,
} from '../../../../domain/connection/connection';
import { CONNECTION_SCOPES } from '../../../../domain/connection/connection';
import type { HttpClient } from '../shared/httpClient';
import { segment } from '../shared/httpClient';
import { list, obj, oneOf, optOneOf, optStr, str, strOr, strings } from '../shared/json';

export function toConnection(value: unknown): Connection {
  const source = obj(value, 'connection');
  return {
    id: str(source, 'id'),
    kind: oneOf(source, 'kind', ['agent', 'script'] as const),
    name: strOr(source, 'name', ''),
    clientId: optStr(source, 'clientId'),
    clientHost: optStr(source, 'clientHost'),
    spaceIds: strings(source, 'spaceIds'),
    scope: oneOf(source, 'scope', CONNECTION_SCOPES),
    createdAt: str(source, 'createdAt'),
    lastUsedAt: optStr(source, 'lastUsedAt'),
    expiresAt: str(source, 'expiresAt'),
    revokedAt: optStr(source, 'revokedAt'),
  };
}

export function toOAuthRequest(value: unknown): OAuthRequest {
  const source = obj(value, 'oauthRequest');
  const client = obj(source.client, 'client');
  return {
    id: str(source, 'id'),
    client: {
      id: str(client, 'id'),
      name: strOr(client, 'name', ''),
      host: optStr(client, 'host'),
      kind: oneOf(client, 'kind', ['metadata_document', 'registered'] as const),
    },
    redirectHost: str(source, 'redirectHost'),
    requestedScope: optOneOf<ConnectionScope>(source, 'requestedScope', CONNECTION_SCOPES),
    expiresAt: str(source, 'expiresAt'),
  };
}

function redirectOf(value: unknown): string {
  return str(obj(value, 'redirect'), 'redirectTo');
}

export class HttpConnectionGateway implements ConnectionGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async list(): Promise<Connection[]> {
    return list(await this.http.get('/api/connections'), toConnection);
  }

  async issueScriptToken(request: ScriptTokenRequest): Promise<IssuedToken> {
    const source = obj(await this.http.post('/api/connections', request));
    return { connection: toConnection(source.connection), token: str(source, 'token') };
  }

  async update(connectionId: string, patch: ConnectionPatch): Promise<Connection> {
    return toConnection(await this.http.patch(`/api/connections/${segment(connectionId)}`, patch));
  }

  async revoke(connectionId: string): Promise<void> {
    await this.http.delete(`/api/connections/${segment(connectionId)}`);
  }
}

export class HttpOAuthRequestGateway implements OAuthRequestGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async get(requestId: string): Promise<OAuthRequest> {
    return toOAuthRequest(await this.http.get(`/api/oauth/requests/${segment(requestId)}`));
  }

  async approve(requestId: string, approval: Approval): Promise<string> {
    return redirectOf(await this.http.post(`/api/oauth/requests/${segment(requestId)}/approve`, approval));
  }

  async deny(requestId: string): Promise<string> {
    return redirectOf(await this.http.post(`/api/oauth/requests/${segment(requestId)}/deny`));
  }
}
