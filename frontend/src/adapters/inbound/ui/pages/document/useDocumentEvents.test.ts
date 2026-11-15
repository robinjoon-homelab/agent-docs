// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { DocumentEventsUseCases } from '../../../../../application/document/documentEventsUseCases';
import { useDocumentEvents } from './useDocumentEvents';

afterEach(cleanup);
class Events implements DocumentEventsUseCases {
  private readonly listeners = new Map<string, () => void>();
  watch(id: string, listener: () => void): () => void {
    this.listeners.set(id, listener);
    return () => {
      this.listeners.delete(id);
    };
  }
  emit(id: string): void {
    this.listeners.get(id)?.();
  }
}
function deferred() {
  let resolve = () => {};
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('document refresh hook', () => {
  it('drains a new signal after a pending query and stops on navigation', async () => {
    const events = new Events();
    const pending = deferred();
    let server = 'first';
    let document = '';
    let threads = '';
    let slow = true;
    const refresh = {
      refreshDocument: async () => {
        const value = server;
        if (slow) {
          slow = false;
          await pending.promise;
        }
        document = value;
      },
      reloadThreads: () => {
        threads = server;
        return Promise.resolve();
      },
      onError: (error: unknown) => {
        throw error;
      },
    };
    const { unmount } = renderHook(() => useDocumentEvents('one', events, refresh));
    act(() => {
      events.emit('one');
    });
    server = 'latest';
    act(() => {
      events.emit('one');
    });
    await act(async () => {
      pending.resolve();
      await pending.promise;
    });
    expect(document).toBe('latest');
    expect(threads).toBe('latest');
    unmount();
    server = 'gone';
    await act(async () => {
      events.emit('one');
      await Promise.resolve();
    });
    expect(document).toBe('latest');
  });
  it('reports a failed refresh', async () => {
    const events = new Events();
    let error: unknown = null;
    const refresh = {
      refreshDocument: () => Promise.reject(new Error('offline')),
      reloadThreads: () => Promise.resolve(),
      onError: (value: unknown) => {
        error = value;
      },
    };
    renderHook(() => useDocumentEvents('one', events, refresh));
    await act(async () => {
      events.emit('one');
      await Promise.resolve();
    });
    expect(error).toEqual(new Error('offline'));
  });
});
