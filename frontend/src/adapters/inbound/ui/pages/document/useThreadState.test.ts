// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { ThreadUseCases } from '../../../../../application/thread/threadUseCases';
import { ok } from '../../../../../application/shared/result';
import type { Thread } from '../../../../../domain/thread/thread';
import { thread } from '../../../../../test-support/fixtures';
import { useThreadState } from './useThreadState';

afterEach(cleanup);

function deferred<T>() {
  let complete: (value: T) => void = () => {
    throw new Error('promise not initialized');
  };
  const promise = new Promise<T>((resolve) => {
    complete = resolve;
  });
  return { promise, resolve: (value: T) => complete(value) };
}

function service(list: ThreadUseCases['list'], changes: Partial<ThreadUseCases> = {}): ThreadUseCases {
  const unused = () => Promise.reject(new Error('unexpected operation'));
  return {
    list,
    start: unused,
    reply: unused,
    resolve: unused,
    reopen: unused,
    reanchor: unused,
    editComment: unused,
    deleteComment: unused,
    openAcross: unused,
    ...changes,
  };
}

describe('document thread refresh', () => {
  it('keeps a newly created thread when an older list arrives and refreshes its anchor', async () => {
    const original = thread();
    const created = thread({ id: 'new-thread' });
    const stale = deferred<Thread[]>();
    const fresh = deferred<Thread[]>();
    const responses = [stale.promise, fresh.promise];
    const api = service(() => responses.shift() ?? Promise.resolve([]), {
      start: () => Promise.resolve(ok(created)),
    });
    const { result } = renderHook(() => useThreadState('doc-1', [original], api));
    let refresh = Promise.resolve();
    act(() => {
      refresh = result.current.reload();
    });
    await act(async () => {
      await result.current.start({ kind: 'document' }, 'New comment');
    });
    await act(async () => {
      stale.resolve([original]);
      await stale.promise;
    });
    expect(result.current.threads.map((item) => item.id)).toEqual([original.id, created.id]);
    const detached = { ...created, resolution: { state: 'detached' as const } };
    await act(async () => {
      fresh.resolve([original, detached]);
      await refresh;
    });
    expect(result.current.threads).toEqual([original, detached]);
  });

  it('keeps a resolved status while rejecting a stale list and adopts the latest anchor', async () => {
    const original = thread();
    const resolved = thread({ status: 'resolved' });
    const stale = deferred<Thread[]>();
    const fresh = deferred<Thread[]>();
    const responses = [stale.promise, fresh.promise];
    const api = service(() => responses.shift() ?? Promise.resolve([]), {
      resolve: () => Promise.resolve(resolved),
    });
    const { result } = renderHook(() => useThreadState('doc-1', [original], api));
    let refresh = Promise.resolve();
    act(() => {
      refresh = result.current.reload();
    });
    await act(async () => {
      await result.current.actions(() => undefined).resolve(original);
    });
    await act(async () => {
      stale.resolve([original]);
      await stale.promise;
    });
    expect(result.current.threads[0]?.status).toBe('resolved');
    const latest = { ...resolved, resolution: { state: 'detached' as const } };
    await act(async () => {
      fresh.resolve([latest]);
      await refresh;
    });
    expect(result.current.threads).toEqual([latest]);
  });

  it('keeps the newest list when overlapping reloads finish in reverse order', async () => {
    const original = thread();
    const latest = thread({ resolution: { state: 'detached' } });
    const older = deferred<Thread[]>();
    const newer = deferred<Thread[]>();
    const responses = [older.promise, newer.promise];
    const api = service(() => responses.shift() ?? Promise.resolve([]));
    const { result } = renderHook(() => useThreadState('doc-1', [original], api));
    let first = Promise.resolve();
    let second = Promise.resolve();
    act(() => {
      first = result.current.reload();
      second = result.current.reload();
    });
    await act(async () => {
      newer.resolve([latest]);
      await second;
    });
    await act(async () => {
      older.resolve([original]);
      await first;
    });
    expect(result.current.threads).toEqual([latest]);
  });
});
