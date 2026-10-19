import type { SessionGateway } from '../../../../application/session/ports/SessionGateway';
import { isApiError } from '../../../../application/shared/ports/errors';
import type { Me } from '../../../../domain/session/me';
import type { HttpClient } from '../shared/httpClient';
import { bool, obj, str, strOr } from '../shared/json';

export function toMe(value: unknown): Me {
  const source = obj(value, 'me');
  const login = str(source, 'login');
  return {
    userId: str(source, 'userId'),
    login,
    name: strOr(source, 'name', login),
    avatarUrl: strOr(source, 'avatarUrl', ''),
    owner: bool(source, 'owner'),
  };
}

export class HttpSessionGateway implements SessionGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async me(): Promise<Me> {
    return toMe(await this.http.get('/api/me'));
  }

  /** Spring Security answers logout with a redirect, which the client reports as "unauthenticated". */
  async logout(): Promise<void> {
    try {
      await this.http.post('/logout');
    } catch (error) {
      if (!isApiError(error, 'unauthenticated')) throw error;
    }
  }
}
