// @vitest-environment jsdom
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { DocumentEditorSession } from '../../../../../application/document/editorSession';
import type { DocumentGateway } from '../../../../../application/document/ports/DocumentGateway';
import type { InputInterval, InputTracePayload } from '../../../../../domain/statistics/statistics';
import { emptyEditStatistics, mergeInputIntervals } from '../../../../../domain/statistics/statistics';
import type { Block } from '../../../../../domain/document/block';
import { withoutHash } from '../../../../../domain/document/block';
import { document as fixture, drawing, paragraph, stored } from '../../../../../test-support/fixtures';
import { useEditorInputTime } from './useEditorInputTime';

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

function setup() {
  let doc = fixture();
  const agentWrites = (blocks: readonly Block[]) => {
    doc = { ...doc, version: doc.version + 1, blocks: blocks.map((block) => stored(block)) };
  };
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
    get: () => Promise.resolve(doc),
    saveContent: (_id, version, blocks, trace) => {
      if (version !== doc.version)
        return Promise.resolve({ kind: 'conflict', currentVersion: doc.version, currentDocument: doc });
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
  return { session, statistics, gateway, agentWrites };
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

function drawingSetup() {
  vi.useFakeTimers();
  const context = setup();
  const hook = renderHook(() => useEditorInputTime(context.session, 'doc-1', context.statistics));
  const draw = (seconds: number) => {
    inputAt(seconds);
    act(() => {
      hook.result.current.drawing.mark('dialog-1', new Date());
    });
  };
  const typeBody = (seconds: number, text: string) => {
    inputAt(seconds);
    act(() => {
      hook.result.current.mark('body');
      context.session.markEdited([paragraph('aaaaaaaaaa', text)]);
    });
  };
  const withDrawing = () => [paragraph('aaaaaaaaaa', 'body'), drawing('drawing001')];
  const reserve = () => {
    const reservation = hook.result.current.drawing.reserve('dialog-1');
    if (reservation === null) throw new Error('drawing input was not recorded');
    const placed = drawing('drawing001');
    return {
      reservation,
      placement: { blockId: placed.id, svgAssetId: placed.svgAssetId, sceneAssetId: placed.sceneAssetId },
    };
  };
  return { ...context, hook, draw, typeBody, withDrawing, reserve };
}

it('adopts the real drawing time only with the save that holds the drawing, frozen when save was pressed', async () => {
  const { session, statistics, hook, draw, typeBody, withDrawing, reserve } = drawingSetup();
  draw(0);
  draw(60);
  draw(110);
  typeBody(115, 'body');
  await act(() => session.saveContent(session.currentBlocks()));
  expect((await statistics.get()).openCount).toBe(1);
  inputAt(120);
  const drawingInput = reserve();
  draw(125);
  await act(() => session.saveContent(withDrawing(), { drawingInput }));
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 115000 });
});

it('a cancelled drawing adopts nothing even while the body around it saves', async () => {
  const { session, statistics, hook, draw, typeBody } = drawingSetup();
  draw(0);
  draw(60);
  typeBody(100, 'first');
  await act(() => session.saveContent(session.currentBlocks()));
  act(() => {
    hook.result.current.drawing.reserve('dialog-1');
    hook.result.current.drawing.discard('dialog-1');
  });
  typeBody(110, 'second');
  await act(() => session.saveContent(session.currentBlocks()));
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 10000 });
});

it('an applied drawing whose save failed is adopted by the later save that carries it', async () => {
  const { session, statistics, gateway, hook, draw, withDrawing, reserve } = drawingSetup();
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  let offline = true;
  gateway.saveContent = (...args) => (offline ? Promise.reject(new Error('offline')) : save(...args));
  draw(0);
  draw(30);
  const drawingInput = reserve();
  expect(await act(() => session.saveContent(withDrawing(), { drawingInput }))).toBe('error');
  offline = false;
  inputAt(40);
  await act(() => session.saveContent(withDrawing()));
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 30000 });
});

it('nothing is adopted while writes are closed, and the drawing input waits instead of being lost', async () => {
  const { session, statistics, hook, draw, withDrawing, reserve } = drawingSetup();
  draw(0);
  draw(20);
  const drawingInput = reserve();
  session.setWritable(false);
  expect(await act(() => session.saveContent(withDrawing(), { drawingInput }))).toBe('error');
  expect((await statistics.get()).openCount).toBe(0);
  session.setWritable(true);
  await act(() => session.saveContent(withDrawing()));
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 20000 });
});

function offline(gateway: Partial<DocumentGateway>) {
  const save = gateway.saveContent;
  if (save === undefined) throw new Error('missing save');
  const state = { offline: true };
  gateway.saveContent = (...args) => (state.offline ? Promise.reject(new Error('offline')) : save(...args));
  return state;
}

it('a drawing removed after its save failed lends no time to the body save that follows', async () => {
  const { session, statistics, gateway, hook, draw, typeBody, withDrawing, reserve } = drawingSetup();
  const network = offline(gateway);
  draw(0);
  draw(30);
  expect(await act(() => session.saveContent(withDrawing(), { drawingInput: reserve() }))).toBe('error');
  network.offline = false;
  typeBody(40, 'the drawing is deleted');
  expect(await act(() => session.saveContent(session.currentBlocks()))).toBe('saved');
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 0 });
});

it('a drawing dropped for the other side of a conflict lends no time to the merged save', async () => {
  const { session, statistics, hook, draw, withDrawing, reserve, agentWrites } = drawingSetup();
  await act(() =>
    session.saveContent([paragraph('aaaaaaaaaa', 'body'), drawing('drawing001', 'svg-old', 'scene-old')]),
  );
  draw(0);
  draw(30);
  agentWrites([paragraph('aaaaaaaaaa', 'body'), drawing('drawing001', 'svg-agent', 'scene-agent')]);
  expect(await act(() => session.saveContent(withDrawing(), { drawingInput: reserve() }))).toBe('conflict');
  inputAt(40);
  act(() => {
    hook.result.current.mark('body');
    session.markEdited([paragraph('aaaaaaaaaa', 'my other change'), drawing('drawing001')]);
  });
  const resolved = await act(() => session.resolveConflicts(new Map([['drawing001', 'theirs' as const]])));
  expect(resolved).toEqual({
    blocks: [paragraph('aaaaaaaaaa', 'my other change'), drawing('drawing001', 'svg-agent', 'scene-agent')],
    outcome: 'saved',
  });
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 0 });
});

it('a drawing that survives a stale save is adopted by the retry that still holds it', async () => {
  const { session, statistics, hook, draw, reserve, agentWrites } = drawingSetup();
  await act(() => session.saveContent([paragraph('aaaaaaaaaa', 'body'), paragraph('bbbbbbbbbb', 'b')]));
  draw(0);
  draw(30);
  agentWrites([paragraph('aaaaaaaaaa', 'body'), paragraph('bbbbbbbbbb', 'agent')]);
  const blocks = [paragraph('aaaaaaaaaa', 'body'), paragraph('bbbbbbbbbb', 'b'), drawing('drawing001')];
  expect(await act(() => session.saveContent(blocks, { drawingInput: reserve() }))).toBe('saved');
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 30000 });
});

it('a failed drawing that is gone when nothing changed is dropped, so a later edit cannot adopt it', async () => {
  const { session, statistics, gateway, hook, draw, typeBody, withDrawing, reserve } = drawingSetup();
  await act(() => session.saveContent([paragraph('aaaaaaaaaa', 'body')]));
  const network = offline(gateway);
  draw(0);
  draw(30);
  expect(await act(() => session.saveContent(withDrawing(), { drawingInput: reserve() }))).toBe('error');
  network.offline = false;
  expect(await act(() => session.saveContent([paragraph('aaaaaaaaaa', 'body')]))).toBe('saved');
  typeBody(60, 'changed');
  await act(() => session.saveContent(session.currentBlocks()));
  hook.unmount();
  expect((await statistics.get()).human).toEqual({ count: 1, milliseconds: 0 });
});

it('a sent drawing save that succeeds late keeps its time and foreground even after the drawing was deleted', async () => {
  const { session, statistics, gateway, hook, draw, withDrawing, reserve } = drawingSetup();
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
  draw(0);
  draw(30);
  const saving = session.saveContent(withDrawing(), { drawingInput: reserve() });
  await act(() => Promise.resolve());
  act(() => {
    session.markEdited([paragraph('aaaaaaaaaa', 'body')]);
    window.dispatchEvent(new Event('pagehide'));
  });
  expect(await statistics.get()).toMatchObject({ human: { count: 0 }, openCount: 0 });
  await act(async () => {
    release();
    await saving;
  });
  expect(await statistics.get()).toMatchObject({ human: { count: 1, milliseconds: 30000 }, openCount: 0 });
  hook.unmount();
});
