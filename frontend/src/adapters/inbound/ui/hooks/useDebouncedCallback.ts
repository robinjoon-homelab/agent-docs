import { useEffect, useRef, useState } from 'react';

export type Debounced<A extends unknown[]> = {
  readonly call: (...args: A) => void;
  /** Runs a pending call now (e.g. before leaving the page). */
  readonly flush: () => void;
  readonly cancel: () => void;
};

/** Returns a stable function that calls the latest `callback` after `delay` ms of silence. */
export function useDebouncedCallback<A extends unknown[]>(
  callback: (...args: A) => void,
  delay: number,
): Debounced<A> {
  const latest = useRef(callback);

  useEffect(() => {
    latest.current = callback;
  });

  const [debounced] = useState<Debounced<A>>(() => {
    let timer: ReturnType<typeof setTimeout> | null = null;
    let pending: A | null = null;
    const cancel = (): void => {
      if (timer !== null) clearTimeout(timer);
      timer = null;
      pending = null;
    };
    const flush = (): void => {
      const args = pending;
      cancel();
      if (args !== null) latest.current(...args);
    };
    const call = (...args: A): void => {
      pending = args;
      if (timer !== null) clearTimeout(timer);
      timer = setTimeout(flush, delay);
    };
    return { call, flush, cancel };
  });

  useEffect(() => () => debounced.flush(), [debounced]);

  return debounced;
}
