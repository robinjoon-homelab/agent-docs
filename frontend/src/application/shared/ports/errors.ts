/** Error codes from the backend's RFC 9457 problem responses, plus client-side transport failures. */
export type ApiErrorCode =
  | 'not_found'
  | 'invalid'
  | 'conflict'
  | 'forbidden'
  | 'rate_limited'
  | 'unauthenticated'
  | 'network'
  | 'unknown';

/** What every gateway throws when a request fails. */
export class ApiError extends Error {
  readonly code: ApiErrorCode;
  readonly status: number;
  /** Optional hint from the server on how to fix the request. */
  readonly fix: string | null;

  constructor(code: ApiErrorCode, status: number, message: string, fix: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fix = fix;
  }
}

export function isApiError(error: unknown, code?: ApiErrorCode): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}

/** Not found and forbidden look the same to the user so a hidden space does not leak its existence. */
export function isNotVisible(error: unknown): boolean {
  return isApiError(error, 'not_found') || isApiError(error, 'forbidden');
}
