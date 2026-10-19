import { useCallback, useEffect, useRef, useState } from 'react';
import { isApiError } from '../../../../application/shared/ports/errors';
import { loginHref } from '../../../../domain/session/me';

export type Load<T> =
  | { readonly state: 'loading'; readonly data: null; readonly error: null }
  | { readonly state: 'ready'; readonly data: T; readonly error: null }
  | { readonly state: 'error'; readonly data: null; readonly error: unknown };

export type LoadResult<T> = Load<T> & {
  readonly reload: () => void;
  /** Replaces the loaded value after a mutation without another request. */
  readonly setData: (update: (current: T) => T) => void;
};

/** Sends the browser to the login page when the session has expired. */
export function redirectIfUnauthenticated(error: unknown): boolean {
  if (!isApiError(error, 'unauthenticated')) return false;
  const here = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  window.location.assign(loginHref(here));
  return true;
}

/**
 * Loads data for a screen. `key` identifies the request: changing it reloads, so it must cover every
 * input the loader reads. The loader runs again on `reload()`; results of stale requests are dropped.
 */
export function useLoad<T>(key: string, loader: () => Promise<T>): LoadResult<T> {
  const [load, setLoad] = useState<{ key: string; version: number; value: Load<T> }>({
    key,
    version: 0,
    value: { state: 'loading', data: null, error: null },
  });
  const [version, setVersion] = useState(0);
  const latestLoader = useRef(loader);

  useEffect(() => {
    latestLoader.current = loader;
  });

  if (load.key !== key || load.version !== version) {
    setLoad({ key, version, value: { state: 'loading', data: null, error: null } });
  }

  useEffect(() => {
    let current = true;
    latestLoader.current().then(
      (data) => {
        if (current) setLoad({ key, version, value: { state: 'ready', data, error: null } });
      },
      (error: unknown) => {
        if (!current || redirectIfUnauthenticated(error)) return;
        setLoad({ key, version, value: { state: 'error', data: null, error } });
      },
    );
    return () => {
      current = false;
    };
  }, [key, version]);

  const reload = useCallback(() => setVersion((value) => value + 1), []);
  const setData = useCallback((update: (current: T) => T) => {
    setLoad((previous) =>
      previous.value.state === 'ready'
        ? { ...previous, value: { state: 'ready', data: update(previous.value.data), error: null } }
        : previous,
    );
  }, []);

  return { ...load.value, reload, setData };
}
