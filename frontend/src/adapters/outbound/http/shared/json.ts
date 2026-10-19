import { ApiError } from '../../../../application/shared/ports/errors';

/**
 * Small readers that turn untyped JSON into domain values. A missing required field means the server
 * and client disagree on the contract, which is reported as an ApiError instead of rendering garbage.
 */
export type Json = Readonly<Record<string, unknown>>;

export function contractError(detail: string): ApiError {
  return new ApiError('unknown', 0, `서버 응답 형식이 올바르지 않습니다 (${detail}).`);
}

export function obj(value: unknown, what = 'object'): Json {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) throw contractError(what);
  return value as Json;
}

export function list<T>(value: unknown, item: (entry: unknown) => T, what = 'array'): T[] {
  if (!Array.isArray(value)) throw contractError(what);
  return value.map(item);
}

export function str(source: Json, key: string): string {
  const value = source[key];
  if (typeof value !== 'string') throw contractError(key);
  return value;
}

export function optStr(source: Json, key: string): string | null {
  const value = source[key];
  return typeof value === 'string' ? value : null;
}

export function strOr(source: Json, key: string, fallback: string): string {
  return optStr(source, key) ?? fallback;
}

export function num(source: Json, key: string): number {
  const value = source[key];
  if (typeof value !== 'number' || Number.isNaN(value)) throw contractError(key);
  return value;
}

export function optNum(source: Json, key: string): number | null {
  const value = source[key];
  return typeof value === 'number' && !Number.isNaN(value) ? value : null;
}

export function bool(source: Json, key: string): boolean {
  return source[key] === true;
}

export function oneOf<T extends string>(source: Json, key: string, values: readonly T[]): T {
  const value = source[key];
  if (typeof value !== 'string' || !(values as readonly string[]).includes(value)) throw contractError(key);
  return value as T;
}

export function optOneOf<T extends string>(source: Json, key: string, values: readonly T[]): T | null {
  const value = source[key];
  return typeof value === 'string' && (values as readonly string[]).includes(value) ? (value as T) : null;
}

export function strings(source: Json, key: string): string[] {
  const value = source[key];
  if (value === undefined || value === null) return [];
  return list(value, (entry) => {
    if (typeof entry !== 'string') throw contractError(key);
    return entry;
  });
}

export function numbers(source: Json, key: string): number[] {
  const value = source[key];
  if (value === undefined || value === null) return [];
  return list(value, (entry) => {
    if (typeof entry !== 'number') throw contractError(key);
    return entry;
  });
}
