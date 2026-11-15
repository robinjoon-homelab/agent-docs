// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { DocumentEditorSession } from '../../../../../application/document/editorSession';
import type { DocumentGateway } from '../../../../../application/document/ports/DocumentGateway';
import type { InputInterval, InputTracePayload } from '../../../../../domain/statistics/statistics';
import { emptyEditStatistics, mergeInputIntervals } from '../../../../../domain/statistics/statistics';
import { withoutHash } from '../../../../../domain/document/block';
import { document as fixture, paragraph, stored } from '../../../../../test-support/fixtures';
import { useEditorInputTime } from './useEditorInputTime';

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function setup() {
  let doc = fixture();
  const intervals = new Map<string, { inputs: readonly InputInterval[]; closed: boolean }>();
  const accept = (trace?: InputTracePayload) => {
    if (trace === undefined) return;
    for (const child of 'traces' in trace ? trace.traces : [trace]) {
      const previous = intervals.get(child.tabId);
      intervals.set(child.tabId, {
        inputs: mergeInputIntervals([...(previous?.inputs ?? []), ...child.intervals]),
        closed: false,
      });
    }
  };
  const gateway: Partial<DocumentGateway> = {
    patch: (_id, _version, patch, trace) => {
      const changed = Object.entries(patch).some(
        ([key, value]) => JSON.stringify(doc[key as keyof typeof patch]) !== JSON.stringify(value),
      );
      if (changed) {
        doc = { ...doc, ...patch, version: doc.version + 1 };
        accept(trace);
      }
      return Promise.resolve(doc);
    },
    saveContent: (_id, _version, blocks, trace) => {
      const changed = JSON.stringify(doc.blocks.map(withoutHash)) !== JSON.stringify(blocks);
      if (changed) {
        doc = { ...doc, version: doc.version + 1, blocks: blocks.map((block) => stored(block)) };
        accept(trace);
      }
      return Promise.resolve({
        kind: 'saved',
        version: doc.version,
        revision: doc.revision,
        blocks: doc.blocks,
      });
    },
  };
  const statistics = {
    get: () =>
      Promise.resolve({
        ...emptyEditStatistics(),
        human: {
          count: [...intervals.values()].filter((value) => value.closed).flatMap((value) => value.inputs)
            .length,
          milliseconds: [...intervals.values()]
            .filter((value) => value.closed)
            .flatMap((value) => value.inputs)
            .reduce(
              (total, value) => total + Date.parse(value.lastInputAt) - Date.parse(value.firstInputAt),
              0,
            ),
        },
        openCount: [...intervals.values()].filter((value) => !value.closed).length,
      }),
    close: (_id: string, tabId: string, last: string) => {
      const value = intervals.get(tabId);
      if (value?.inputs.at(-1)?.lastInputAt === last) value.closed = true;
      return Promise.resolve(true);
    },
  };
  const session = new DocumentEditorSession(gateway as DocumentGateway, { now: () => new Date() }, doc);
  return { session, statistics, gateway };
}

it('separates foreground segments and closes only successfully saved input, ignoring no-op input', async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
  const { session, statistics } = setup();
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  const input = (text: string) =>
    act(() => {
      result.current.mark('body');
      session.markEdited([paragraph('aaaaaaaaaa', text)]);
    });
  input('first');
  await act(() => session.saveContent(session.currentBlocks()));
  vi.setSystemTime(new Date('2026-01-01T00:00:10Z'));
  input('second');
  await act(() => session.saveContent(session.currentBlocks()));
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 10000 });
  vi.setSystemTime(new Date('2026-01-01T01:00:00Z'));
  input('third');
  await act(() => session.saveContent(session.currentBlocks()));
  vi.setSystemTime(new Date('2026-01-01T01:00:40Z'));
  input('third');
  await act(() => session.saveContent(session.currentBlocks()));
  unmount();
  expect(await statistics.get()).toMatchObject({ human: { count: 2, milliseconds: 10000 }, openCount: 0 });
});

it('retains the original segment for a write in flight when the page hides', async () => {
  const { session, statistics, gateway } = setup();
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  let release: () => void = () => undefined;
  const delay = new Promise<void>((resolve) => {
    release = resolve;
  });
  gateway.saveContent = async (...args) => {
    await delay;
    return save(...args);
  };
  const { result } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'first')]);
  });
  const saving = session.saveContent(session.currentBlocks());
  await act(() => Promise.resolve());
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  expect(await statistics.get()).toMatchObject({ human: { count: 0 }, openCount: 0 });
  await act(async () => {
    release();
    await saving;
  });
  expect(await statistics.get()).toMatchObject({ human: { count: 1, milliseconds: 0 }, openCount: 0 });
});

const inputAt = (seconds: number) => vi.setSystemTime(new Date(Date.UTC(2026, 0, 1, 0, 0, seconds)));

it.each([119, 120, 300])('preserves a %i-second gap inside a single successful body batch', async (gap) => {
  vi.useFakeTimers();
  const { session, statistics } = setup();
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'first')]);
  });
  inputAt(gap);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'second')]);
  });
  await act(() => session.saveContent(session.currentBlocks()));
  unmount();
  expect((await statistics.get()).human).toEqual(
    gap < 120 ? { count: 1, milliseconds: gap * 1000 } : { count: 2, milliseconds: 0 },
  );
});

it('combines field intervals in time order without filling a 120-second gap', async () => {
  vi.useFakeTimers();
  const { session, statistics } = setup();
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('title');
    session.markMetaEdited({ title: 'first' });
  });
  inputAt(60);
  act(() => {
    result.current.mark('tags');
    session.markMetaEdited({ tags: ['middle'] });
  });
  inputAt(120);
  act(() => {
    result.current.mark('title');
    session.markMetaEdited({ title: 'last' });
  });
  inputAt(240);
  act(() => {
    result.current.mark('assignee');
    session.markMetaEdited({ assignee: 'owner' });
  });
  await act(() => session.flushMeta());
  unmount();
  expect((await statistics.get()).human).toEqual({ count: 2, milliseconds: 120000 });
});

it('adopts only the submitted field when another field still awaits its own save', async () => {
  vi.useFakeTimers();
  const { session, statistics } = setup();
  const { result } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('title');
    session.markMetaEdited({ title: 'saved title' });
  });
  inputAt(300);
  act(() => {
    result.current.mark('tags');
    session.markMetaEdited({ tags: ['pending'] });
  });
  await act(() => session.patchMeta({ title: 'saved title' }));
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 0 });
  await act(() => session.patchMeta({ tags: ['pending'] }));
  expect((await statistics.get()).human).toEqual({ count: 2, milliseconds: 0 });
});

it('keeps later in-flight input out of the earlier success and adopts it with its own late batch', async () => {
  vi.useFakeTimers();
  const { session, statistics, gateway } = setup();
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  let release: () => void = () => undefined;
  const delay = new Promise<void>((resolve) => {
    release = resolve;
  });
  gateway.saveContent = async (...args) => {
    await delay;
    return save(...args);
  };
  const { result } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'first')]);
  });
  const saving = session.saveContent(session.currentBlocks());
  await act(() => Promise.resolve());
  inputAt(60);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'later')]);
  });
  inputAt(300);
  await act(async () => {
    release();
    await saving;
  });
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 0 });
  await act(() => session.saveContent(session.currentBlocks()));
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 60000 });
});

it('keeps failed input unadopted until a changed retry preserves all of its intervals', async () => {
  vi.useFakeTimers();
  const { session, statistics, gateway } = setup();
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  gateway.saveContent = () => Promise.reject(new Error('offline'));
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'first')]);
  });
  await act(() => session.saveContent(session.currentBlocks()));
  expect(await statistics.get()).toMatchObject({ human: { count: 0, milliseconds: 0 }, openCount: 0 });
  inputAt(300);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'retry')]);
  });
  gateway.saveContent = save;
  await act(() => session.saveContent(session.currentBlocks()));
  unmount();
  expect((await statistics.get()).human).toEqual({ count: 2, milliseconds: 0 });
});

it('clears no-op marks so a later changed batch cannot count their time', async () => {
  vi.useFakeTimers();
  const { session, statistics } = setup();
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  for (const [seconds, text] of [
    [0, 'first'],
    [300, 'first'],
    [360, 'changed'],
  ] as const) {
    inputAt(seconds);
    act(() => {
      result.current.mark('body');
      session.markEdited([paragraph('aaaaaaaaaa', text)]);
    });
    await act(() => session.saveContent(session.currentBlocks()));
  }
  unmount();
  expect((await statistics.get()).human).toEqual({ count: 2, milliseconds: 0 });
});

it('keeps an in-flight request on its old foreground scope after new foreground input arrives', async () => {
  vi.useFakeTimers();
  const { session, statistics, gateway } = setup();
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  let release: () => void = () => undefined;
  const delay = new Promise<void>((resolve) => {
    release = resolve;
  });
  gateway.saveContent = async (...args) => {
    await delay;
    return save(...args);
  };
  const { result, unmount } = renderHook(() => useEditorInputTime(session, 'doc-1', statistics));
  inputAt(0);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'old scope')]);
  });
  const saving = session.saveContent(session.currentBlocks());
  await act(() => Promise.resolve());
  inputAt(60);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'old scope pending')]);
  });
  inputAt(70);
  act(() => {
    window.dispatchEvent(new Event('pagehide'));
  });
  inputAt(360);
  act(() => {
    result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'new scope')]);
  });
  inputAt(480);
  await act(async () => {
    release();
    await saving;
  });
  expect(await statistics.get()).toMatchObject({ human: { count: 1, milliseconds: 0 }, openCount: 0 });
  await act(() => session.saveContent(session.currentBlocks()));
  unmount();
  expect(await statistics.get()).toMatchObject({ human: { count: 2, milliseconds: 60000 }, openCount: 0 });
});
