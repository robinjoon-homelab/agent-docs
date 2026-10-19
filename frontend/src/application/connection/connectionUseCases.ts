import type {
  Approval,
  Connection,
  GrantError,
  IssuedToken,
  OAuthRequest,
  ScriptTokenRequest,
} from '../../domain/connection/connection';
import { validateApproval, validateScriptToken } from '../../domain/connection/connection';
import type { Clock } from '../shared/ports/Clock';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { ConnectionGateway, ConnectionPatch, OAuthRequestGateway } from './ports/ConnectionGateway';

export interface ConnectionUseCases {
  /** Active connections first (not revoked), each group by most recent use. */
  list(): Promise<Connection[]>;
  issueScriptToken(request: ScriptTokenRequest): Promise<Result<IssuedToken, GrantError[]>>;
  update(connectionId: string, patch: ConnectionPatch): Promise<Connection>;
  revoke(connectionId: string): Promise<void>;
  getRequest(requestId: string): Promise<OAuthRequest>;
  approve(requestId: string, approval: Approval): Promise<Result<string, GrantError[]>>;
  deny(requestId: string): Promise<string>;
}

function lastTouched(connection: Connection): string {
  return connection.lastUsedAt ?? connection.createdAt;
}

export function createConnectionUseCases(
  connections: ConnectionGateway,
  requests: OAuthRequestGateway,
  clock: Clock,
): ConnectionUseCases {
  return {
    async list() {
      const now = clock.now().toISOString();
      const all = await connections.list();
      const active = (connection: Connection): boolean =>
        connection.revokedAt === null && connection.expiresAt > now;
      return [...all].sort(
        (a, b) => Number(active(b)) - Number(active(a)) || lastTouched(b).localeCompare(lastTouched(a)),
      );
    },
    async issueScriptToken(request) {
      const normalized = { ...request, name: request.name.trim() };
      const errors = validateScriptToken(normalized);
      if (errors.length > 0) return failure(errors);
      return ok(await connections.issueScriptToken(normalized));
    },
    update: (connectionId, patch) => connections.update(connectionId, patch),
    revoke: (connectionId) => connections.revoke(connectionId),
    getRequest: (requestId) => requests.get(requestId),
    async approve(requestId, approval) {
      const errors = validateApproval(approval);
      if (errors.length > 0) return failure(errors);
      return ok(await requests.approve(requestId, approval));
    },
    deny: (requestId) => requests.deny(requestId),
  };
}
