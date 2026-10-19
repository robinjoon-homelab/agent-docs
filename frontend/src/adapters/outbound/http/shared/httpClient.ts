import type { ApiErrorCode } from '../../../../application/shared/ports/errors';
import { ApiError } from '../../../../application/shared/ports/errors';

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

export type HttpClientOptions = {
  readonly fetch: FetchLike;
  /** Reads `document.cookie` in the browser; injected so tests stay DOM-free. */
  readonly cookies: () => string;
};

/** A failed request with the parsed problem body, for gateways that need more than the code. */
export class ProblemError extends ApiError {
  readonly body: Readonly<Record<string, unknown>>;

  constructor(
    code: ApiErrorCode,
    status: number,
    message: string,
    fix: string | null,
    body: Record<string, unknown>,
  ) {
    super(code, status, message, fix);
    this.name = 'ProblemError';
    this.body = body;
  }
}

const CODES: readonly ApiErrorCode[] = [
  'not_found',
  'invalid',
  'conflict',
  'forbidden',
  'rate_limited',
  'unauthenticated',
];

function codeForStatus(status: number): ApiErrorCode {
  if (status === 401) return 'unauthenticated';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'not_found';
  if (status === 409) return 'conflict';
  if (status === 429) return 'rate_limited';
  if (status === 400 || status === 422) return 'invalid';
  return 'unknown';
}

const DEFAULT_MESSAGE: Record<ApiErrorCode, string> = {
  not_found: '찾을 수 없습니다.',
  invalid: '요청 값을 확인하세요.',
  conflict: '다른 변경과 충돌했습니다.',
  forbidden: '권한이 없습니다.',
  rate_limited: '요청이 너무 많습니다. 잠시 후 다시 시도하세요.',
  unauthenticated: '로그인이 필요합니다.',
  network: '서버에 연결할 수 없습니다.',
  unknown: '알 수 없는 오류가 발생했습니다.',
};

async function readBody(response: Response): Promise<Record<string, unknown>> {
  const type = response.headers.get('content-type') ?? '';
  if (!type.includes('json')) return {};
  try {
    const parsed: unknown = await response.json();
    return typeof parsed === 'object' && parsed !== null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

/** Maps an RFC 9457 problem response to a ProblemError; the `code` field wins over the HTTP status. */
export async function problemFrom(response: Response): Promise<ProblemError> {
  const body = await readBody(response);
  const declared = typeof body.code === 'string' ? (body.code as ApiErrorCode) : null;
  const code = declared !== null && CODES.includes(declared) ? declared : codeForStatus(response.status);
  const detail = typeof body.detail === 'string' ? body.detail : null;
  const title = typeof body.title === 'string' ? body.title : null;
  const fix = typeof body.fix === 'string' ? body.fix : null;
  return new ProblemError(code, response.status, detail ?? title ?? DEFAULT_MESSAGE[code], fix, body);
}

export function csrfToken(cookieHeader: string): string | null {
  for (const part of cookieHeader.split(';')) {
    const [name, ...rest] = part.trim().split('=');
    if (name === 'XSRF-TOKEN') return decodeURIComponent(rest.join('='));
  }
  return null;
}

type Body = { readonly json: unknown } | { readonly form: FormData } | null;

/**
 * fetch wrapper for the same-origin API: JSON in and out, the Spring Security CSRF header on mutating
 * requests, and failures mapped to ApiError.
 */
export class HttpClient {
  private readonly options: HttpClientOptions;

  constructor(options: HttpClientOptions) {
    this.options = options;
  }

  get(path: string): Promise<unknown> {
    return this.request('GET', path, null);
  }

  post(path: string, json?: unknown): Promise<unknown> {
    return this.request('POST', path, json === undefined ? null : { json });
  }

  put(path: string, json: unknown): Promise<unknown> {
    return this.request('PUT', path, { json });
  }

  patch(path: string, json: unknown): Promise<unknown> {
    return this.request('PATCH', path, { json });
  }

  delete(path: string): Promise<unknown> {
    return this.request('DELETE', path, null);
  }

  postForm(path: string, form: FormData): Promise<unknown> {
    return this.request('POST', path, { form });
  }

  private headers(method: string, body: Body): Headers {
    const headers = new Headers({ Accept: 'application/json' });
    if (body !== null && 'json' in body) headers.set('Content-Type', 'application/json');
    if (method !== 'GET') {
      const token = csrfToken(this.options.cookies());
      if (token !== null) headers.set('X-XSRF-TOKEN', token);
    }
    return headers;
  }

  private async request(method: string, path: string, body: Body): Promise<unknown> {
    // Redirects are not followed: for API calls a redirect means the session is gone and Spring
    // Security is sending the browser to the login flow.
    const init: RequestInit = {
      method,
      headers: this.headers(method, body),
      credentials: 'same-origin',
      redirect: 'manual',
    };
    if (body !== null) init.body = 'json' in body ? JSON.stringify(body.json) : body.form;
    let response: Response;
    try {
      response = await this.options.fetch(path, init);
    } catch (error) {
      throw new ApiError(
        'network',
        0,
        error instanceof Error ? `서버에 연결할 수 없습니다: ${error.message}` : DEFAULT_MESSAGE.network,
      );
    }
    if (response.type === 'opaqueredirect' || (response.status >= 300 && response.status < 400)) {
      throw new ApiError('unauthenticated', 401, DEFAULT_MESSAGE.unauthenticated);
    }
    if (!response.ok) throw await problemFrom(response);
    if (!(response.headers.get('content-type') ?? '').includes('json')) return undefined;
    const text = await response.text();
    return text === '' ? undefined : (JSON.parse(text) as unknown);
  }
}

export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== null && value !== undefined && value !== '') search.set(key, String(value));
  }
  const text = search.toString();
  return text === '' ? '' : `?${text}`;
}

export function segment(value: string): string {
  return encodeURIComponent(value);
}
