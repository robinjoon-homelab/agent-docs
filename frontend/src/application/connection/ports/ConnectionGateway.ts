import type {
  Approval,
  Connection,
  ConnectionScope,
  IssuedToken,
  OAuthRequest,
  ScriptTokenRequest,
} from '../../../domain/connection/connection';

export type ConnectionPatch = { readonly spaceIds?: readonly string[]; readonly scope?: ConnectionScope };

export interface ConnectionGateway {
  list(): Promise<Connection[]>;
  issueScriptToken(request: ScriptTokenRequest): Promise<IssuedToken>;
  update(connectionId: string, patch: ConnectionPatch): Promise<Connection>;
  revoke(connectionId: string): Promise<void>;
}

export interface OAuthRequestGateway {
  get(requestId: string): Promise<OAuthRequest>;
  /** Returns the URL to send the browser to (back to the MCP client). */
  approve(requestId: string, approval: Approval): Promise<string>;
  deny(requestId: string): Promise<string>;
}
