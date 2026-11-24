// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, renderHook, screen, within } from '@testing-library/react';
import { useRef, useState } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { designRows } from './designLocation';
import { DesignCanvasView } from './DesignCanvas';
import {
  DesignEditCanvas,
  DesignMultiSelection,
  type DesignCanvasCommands,
  useDesignCanvasSelection,
} from './DesignEditCanvas';

const data: FlowDesignBlock[] = [
  {
    type: 'flowDesign',
    id: 'screen000',
    design: {
      kind: 'screen',
      id: 's',
      code: 'F-01',
      name: '화면',
      frame: { width: 360, height: 640 },
      root: {
        kind: 'box',
        id: 'root',
        layout: { mode: 'free' },
        size: { y: { mode: 'fixed', px: 640 } },
        children: [
          {
            kind: 'text',
            id: 'a',
            name: '제목',
            text: '제목',
            position: { mode: 'absolute', x: { start: 20 }, y: { start: 24 } },
            size: { x: { mode: 'fixed', px: 60 }, y: { mode: 'fixed', px: 28 } },
          },
          {
            kind: 'box',
            id: 'b',
            name: '버튼',
            layout: { mode: 'free' },
            position: { mode: 'absolute', x: { start: 120 }, y: { start: 24 } },
            size: { x: { mode: 'fixed', px: 72 }, y: { mode: 'fixed', px: 32 } },
          },
        ],
      },
    },
  },
];
function View() {
  const [blocks, setBlocks] = useState(data),
    [address, setAddress] = useState<DesignAddress>(),
    [pending, ask] = useState<readonly DesignAddress[]>();
  const [past, setPast] = useState<FlowDesignBlock[][]>([]),
    [future, setFuture] = useState<FlowDesignBlock[][]>([]);
  const choice = useDesignCanvasSelection('s', undefined, address, setAddress);
  const scene = resolveDesignScene(blocks, 's'),
    rows = designRows(scene.root, blocks),
    commands = useRef<DesignCanvasCommands>(null);
  return (
    <>
      <DesignEditCanvas
        ref={commands}
        editable
        rows={rows}
        scope={{ screen: 's' }}
        blocks={blocks}
        selection={choice.addresses}
        onSelect={choice.change}
        onRequestDelete={ask}
        onMessage={(s) => {
          throw Error(s);
        }}
        mutate={(change) => {
          const next = change(blocks);
          setPast([...past, blocks]);
          setFuture([]);
          setBlocks(next);
          return null;
        }}
        onUndo={() => {
          const v = past.at(-1);
          if (v) {
            setFuture([...future, blocks]);
            setPast(past.slice(0, -1));
            setBlocks(v);
          }
        }}
        onRedo={() => {
          const v = future.at(-1);
          if (v) {
            setPast([...past, blocks]);
            setFuture(future.slice(0, -1));
            setBlocks(v);
          }
        }}
      >
        <DesignCanvasView scene={scene} blocks={blocks} screen="s" selection={address} assetUrl={() => ''} />
      </DesignEditCanvas>
      {choice.addresses.length > 1 && (
        <DesignMultiSelection selection={choice.addresses} rows={rows} commands={commands} />
      )}
      {pending && (
        <button
          onClick={() => {
            commands.current?.remove(pending);
            ask(undefined);
          }}
        >
          삭제 확인
        </button>
      )}
    </>
  );
}
function rect(el: HTMLElement) {
  const layer = el.hasAttribute('data-design-address'),
    x = layer ? parseFloat(el.style.left) || 0 : 0,
    y = layer ? parseFloat(el.style.top) || 0 : 0;
  const w = layer ? parseFloat(el.style.width) || 360 : 360,
    h = layer ? parseFloat(el.style.height) || 640 : 640;
  return { x, y, w, h };
}
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockImplementation(function (this: HTMLElement) {
    return rect(this).w;
  });
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockImplementation(function (this: HTMLElement) {
    return rect(this).h;
  });
  vi.spyOn(Element.prototype, 'clientHeight', 'get').mockImplementation(function (this: HTMLElement) {
    return rect(this).h;
  });
  vi.spyOn(Element.prototype, 'clientWidth', 'get').mockImplementation(function (this: HTMLElement) {
    return rect(this).w;
  });
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
    const r = rect(this);
    return new DOMRect(r.x / 2, r.y / 2, r.w / 2, r.h / 2);
  });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
const layer = (id: string) => document.querySelector<HTMLElement>('[data-design-node="' + id + '"]');
const editor = () => screen.getByLabelText('요소 편집 캔버스');
const press = (key: string, extra = {}) => fireEvent.keyDown(editor(), { key, ...extra });
function item(id: string) {
  const n = layer(id);
  if (!n) throw Error('Missing item ' + id);
  return n;
}
it('selects on the canvas, displays eight handles, and commits one zoom-correct resize before undo', () => {
  render(<View />);
  fireEvent.click(item('a'));
  expect(screen.getAllByRole('button', { name: /크기 조절$/ })).toHaveLength(8);
  const right = screen.getByRole('button', { name: '오른쪽 크기 조절' });
  expect(screen.getByRole('group', { name: '요소 도구' })).toBeTruthy();
  fireEvent.pointerDown(right, { button: 0, pointerId: 1, clientX: 40, clientY: 20 });
  fireEvent.pointerMove(editor(), { pointerId: 1, clientX: 50, clientY: 20, altKey: true });
  expect(document.querySelector('.flow-coordinates')?.textContent).toContain('80×28');
  fireEvent.pointerUp(editor(), { pointerId: 1, clientX: 50, clientY: 20, altKey: true });
  expect(item('a').style.width).toBe('80px');
  press('z', { ctrlKey: true });
  expect(item('a').style.width).toBe('60px');
});
it('marquee-selects intersecting siblings, groups and nudges them, and confirms a deletion with one undo', () => {
  render(<View />);
  fireEvent.pointerDown(item('root'), { button: 0, pointerId: 1, clientX: 2, clientY: 3 });
  fireEvent.pointerMove(editor(), { pointerId: 1, clientX: 99, clientY: 40 });
  fireEvent.pointerUp(editor(), { pointerId: 1, clientX: 99, clientY: 40 });
  expect(screen.getByRole('heading', { name: '요소 2개 선택' })).toBeTruthy();
  press('g', { metaKey: true });
  expect(document.querySelector('.design-selection-label')?.textContent).toContain('묶음');
  press('ArrowRight', { shiftKey: true });
  expect(item('a').style.left).toBe('30px');
  expect(item('b').style.left).toBe('130px');
  press('Delete');
  expect(item('a')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '삭제 확인' }));
  expect(layer('a')).toBeNull();
  expect(layer('b')).toBeNull();
  press('z', { metaKey: true });
  expect(item('a').style.left).toBe('30px');
});
it('adds and removes with shift-click and pastes fresh IDs with successive offsets', () => {
  render(<View />);
  fireEvent.click(item('a'));
  fireEvent.click(item('b'), { shiftKey: true });
  expect(screen.getByRole('heading', { name: '요소 2개 선택' })).toBeTruthy();
  fireEvent.click(item('b'), { shiftKey: true });
  expect(screen.queryByRole('heading', { name: '요소 2개 선택' })).toBeNull();
  press('c', { ctrlKey: true });
  press('v', { ctrlKey: true });
  press('v', { ctrlKey: true });
  const copies = [...editor().querySelectorAll<HTMLElement>('[data-design-kind=text]')];
  expect(copies.map((n) => n.style.left)).toEqual(['20px', '36px', '52px']);
  expect(new Set(copies.map((n) => n.dataset.designNode)).size).toBe(3);
  const toolbar = screen.getByRole('group', { name: '요소 도구' });
  fireEvent.click(within(toolbar).getByRole('button', { name: '뒤로 보내기' }));
  expect(editor().querySelector<HTMLElement>('[data-design-kind=text]')?.style.left).toBe('52px');
});

it('applies the most recent pointer selection while the viewer link is still changing', () => {
  const first = { owner: 's', node: 'a' },
    second = { owner: 's', node: 'b' };
  const { result, rerender } = renderHook(
    ({ address }) => useDesignCanvasSelection('s', undefined, address, () => undefined),
    { initialProps: { address: second } },
  );
  act(() => result.current.change([first, second]));
  expect(result.current.addresses).toEqual([first, second]);
  rerender({ address: first });
  act(() => result.current.change([second]));
  expect(result.current.addresses).toEqual([second]);
  rerender({ address: second });
  rerender({ address: first });
  expect(result.current.addresses).toEqual([first]);
});
