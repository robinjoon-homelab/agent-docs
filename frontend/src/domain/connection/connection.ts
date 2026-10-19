import { daysUntil } from '../shared/time';

export type ConnectionScope = 'read' | 'comment' | 'edit';
export type ConnectionKind = 'agent' | 'script';

export type Connection = {
  readonly id: string;
  readonly kind: ConnectionKind;
  readonly name: string;
  readonly clientId: string | null;
  readonly clientHost: string | null;
  /** Spaces this connection may use. Names come from the space list; the server sends ids only. */
  readonly spaceIds: readonly string[];
  readonly scope: ConnectionScope;
  readonly createdAt: string;
  readonly lastUsedAt: string | null;
  readonly expiresAt: string;
  readonly revokedAt: string | null;
};

export type IssuedToken = { readonly connection: Connection; readonly token: string };

export type ScriptTokenRequest = {
  readonly name: string;
  readonly spaceIds: readonly string[];
  readonly scope: ConnectionScope;
  readonly expiresInDays: number;
};

export type OAuthClientKind = 'metadata_document' | 'registered';

export type OAuthRequest = {
  readonly id: string;
  readonly client: {
    readonly id: string;
    readonly name: string;
    /** Host of the client_id URL; null for dynamically registered (DCR) clients. */
    readonly host: string | null;
    readonly kind: OAuthClientKind;
  };
  readonly redirectHost: string;
  readonly requestedScope: ConnectionScope | null;
  readonly expiresAt: string;
};

/** What the approval screen shows most prominently: the host, else the client's name, else its id. */
export function requestingClientLabel(request: OAuthRequest): string {
  if (request.client.host !== null) return request.client.host;
  return request.client.name.trim() !== '' ? request.client.name : request.client.id;
}

export type Approval = {
  readonly spaceIds: readonly string[];
  readonly scope: ConnectionScope;
  readonly expiresInDays: number;
};

export const CONNECTION_SCOPES: readonly ConnectionScope[] = ['read', 'comment', 'edit'];

export const SCOPE_LABEL: Record<ConnectionScope, string> = { read: '읽기', comment: '댓글', edit: '편집' };

export const CONNECTION_KIND_LABEL: Record<ConnectionKind, string> = {
  agent: '에이전트(OAuth)',
  script: '스크립트 토큰',
};

export const DEFAULT_EXPIRY_DAYS = 90;
export const MAX_EXPIRY_DAYS = 365;
export const EXPIRY_WARNING_DAYS = 7;

export type ExpiryState = 'expired' | 'soon' | 'ok';

export function expiryState(expiresAt: string, now: Date): ExpiryState {
  const days = daysUntil(expiresAt, now);
  if (days <= 0) return 'expired';
  return days < EXPIRY_WARNING_DAYS ? 'soon' : 'ok';
}

export function isActive(connection: Connection, now: Date): boolean {
  return connection.revokedAt === null && expiryState(connection.expiresAt, now) !== 'expired';
}

export type GrantError = 'no_spaces' | 'expiry_invalid' | 'name_required';

export const GRANT_ERROR_TEXT: Record<GrantError, string> = {
  no_spaces: '허용할 스페이스를 하나 이상 고르세요.',
  expiry_invalid: `만료 기간은 1~${MAX_EXPIRY_DAYS}일이어야 합니다.`,
  name_required: '이름을 입력하세요.',
};

export function validateApproval(approval: Approval): GrantError[] {
  const errors: GrantError[] = [];
  if (approval.spaceIds.length === 0) errors.push('no_spaces');
  if (
    !Number.isInteger(approval.expiresInDays) ||
    approval.expiresInDays < 1 ||
    approval.expiresInDays > MAX_EXPIRY_DAYS
  ) {
    errors.push('expiry_invalid');
  }
  return errors;
}

export function validateScriptToken(request: ScriptTokenRequest): GrantError[] {
  const errors = validateApproval(request);
  return request.name.trim() === '' ? ['name_required', ...errors] : errors;
}

export const UNKNOWN_SPACE_NAME = '삭제되었거나 볼 수 없는 스페이스';

/** Names of the spaces a connection may use, in grant order, joined from the space list. */
export function grantedSpaceNames(
  connection: Connection,
  spaces: readonly { readonly id: string; readonly name: string }[],
): string[] {
  const names = new Map(spaces.map((space) => [space.id, space.name]));
  return connection.spaceIds.map((id) => names.get(id) ?? UNKNOWN_SPACE_NAME);
}

export function allowsSpace(connection: Connection, spaceId: string): boolean {
  return connection.spaceIds.includes(spaceId);
}

export type McpSetup = { readonly add: readonly string[]; readonly login: readonly string[] };

/**
 * Commands shown on the agent connections screen, built from the origin the app is served from. Both register the
 * server for every directory: Claude Code with user scope, Codex in its global config.
 */
export function mcpSetupCommands(origin: string): McpSetup {
  const endpoint = `${origin.replace(/\/+$/, '')}/mcp`;
  return {
    add: [
      `claude mcp add --scope user --transport http agent-docs ${endpoint}`,
      `codex mcp add agent-docs --url ${endpoint}`,
    ],
    login: ['claude mcp login agent-docs', 'codex mcp login agent-docs'],
  };
}
