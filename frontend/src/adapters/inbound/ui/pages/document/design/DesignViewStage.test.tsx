// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { useState } from 'react';
import type { DesignObject, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { DesignCanvasView } from './DesignCanvas';
import { DesignViewStage } from './DesignViewStage';

const block = (id: string, design: DesignObject): FlowDesignBlock => ({ id, type: 'flowDesign', design });
const entry = block('screen01', {
  kind: 'screen',
  id: 'form',
  code: 'M-01',
  name: '메모',
  root: {
    kind: 'box',
    id: 'root',
    layout: { mode: 'column' },
    children: [
      { kind: 'text', id: 'quiet', text: '장식' },
      {
        kind: 'box',
        id: 'save',
        role: 'button',
        accessibleName: '저장',
        layout: { mode: 'column' },
        children: [{ kind: 'text', id: 'label', text: '저장 문구' }],
      },
    ],
  },
});
const address: DesignAddress = { owner: 'form', node: 'save' };
const blocks = [
  block('settings', { kind: 'settings', id: 'settings', frame: 'phone' }),
  entry,
  block('anno01', {
    id: 'annotation',
    kind: 'annotation',
    screen: 'form',
    target: { owner: 'form', node: 'save' },
    name: '저장 버튼',
  }),
];
const scene = resolveDesignScene(blocks, 'form');
const markers = [{ target: address, name: '저장 버튼', destination: 'M-02', comments: 2 }];
function Stage({ editing = false }: { editing?: boolean }) {
  const [result, message] = useState('');
  const [highlighted, highlight] = useState<DesignAddress | null>(null);
  return (
    <>
      <button onClick={() => highlight(address)}>주요 요소 강조</button>
      <DesignViewStage
        scene={scene}
        blocks={blocks}
        screen={entry}
        editing={editing}
        markers={markers}
        highlighted={highlighted}
        onHighlight={highlight}
        onInfo={(a) => message('설명 ' + a.node)}
        onComment={(a) => message('댓글 ' + (a?.node ?? '화면'))}
        back={() => message('이전 화면')}
        forward={() => message('다음 화면')}
      >
        <DesignCanvasView
          scene={scene}
          blocks={blocks}
          screen="form"
          assetUrl={() => ''}
          onActivate={(a) => message('이동 ' + a.node)}
        />
      </DesignViewStage>
      <output>{result}</output>
    </>
  );
}
function bounds(x: number, y: number, width: number, height: number): DOMRect {
  return { x, y, width, height, left: x, top: y, right: x + width, bottom: y + height, toJSON: () => ({}) };
}
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
    return this.dataset.designNode === 'save' ? bounds(40, 100, 90, 30) : bounds(10, 60, 390, 844);
  });
  vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(390);
  vi.spyOn(HTMLElement.prototype, 'offsetHeight', 'get').mockReturnValue(844);
  vi.spyOn(Element.prototype, 'clientWidth', 'get').mockReturnValue(390);
  vi.spyOn(Element.prototype, 'clientHeight', 'get').mockReturnValue(844);
  vi.spyOn(Element.prototype, 'scrollHeight', 'get').mockReturnValue(1044);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

it('keeps decorative clicks inert and separates visiting, opening information, and commenting on the same anchor', () => {
  const { container } = render(<Stage />);
  fireEvent.click(screen.getByText('장식'));
  expect(container.querySelector('output')?.textContent).toBe('');
  const label = screen.getByText('저장 문구');
  fireEvent.mouseOver(label);
  const info = screen.getByRole('button', { name: '저장 버튼 설명' });
  expect(screen.getByText('→ M-02')).toBeTruthy();
  const marker = info.closest('.design-node-marker');
  expect(marker?.getAttribute('style')).toContain('left: 30px');
  fireEvent.click(info);
  expect(container.querySelector('output')?.textContent).toBe('설명 save');
  fireEvent.click(label);
  expect(container.querySelector('output')?.textContent).toBe('이동 save');
  fireEvent.click(screen.getByRole('button', { name: '저장 버튼 댓글 2개' }));
  expect(container.querySelector('output')?.textContent).toBe('댓글 save');
});

it('toggles all numbers, highlights a row from the panel, and zooms the whole phone frame', () => {
  const { container } = render(<Stage />);
  const zoom = within(screen.getByRole('group', { name: '배율' }));
  expect(container.querySelector('.chrome-phone')).toBeTruthy();
  expect(zoom.getByRole('button', { name: '맞춤' }).getAttribute('aria-pressed')).toBe('true');
  expect(container.querySelector('.design-annotation-number')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '번호 모두 표시' }));
  expect(container.querySelector('.design-annotation-number')?.textContent).toBe('1');
  expect(screen.queryByRole('button', { name: '저장 버튼 설명' })).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '주요 요소 강조' }));
  expect(screen.getByRole('button', { name: '저장 버튼 설명' })).toBeTruthy();
  fireEvent.click(zoom.getByRole('button', { name: '50%' }));
  expect(container.querySelector<HTMLElement>('.flow-device')?.style.transform).toBe('scale(0.5)');
  expect(container.querySelector('.flow-frame-caption')?.textContent).toContain('390×844 · 50%');
  fireEvent.click(screen.getByRole('button', { name: '앞으로' }));
  expect(container.querySelector('output')?.textContent).toBe('다음 화면');
});

it('updates the scrolling hint and the editing fold as the real canvas scrolls', async () => {
  const { container, rerender } = render(<Stage />);
  const canvas = container.querySelector<HTMLElement>('.design-canvas');
  if (!canvas) throw Error('Missing canvas');
  expect(screen.getByText('↓ 아래로 스크롤해 더 보기')).toBeTruthy();
  canvas.scrollTop = 200;
  fireEvent.scroll(canvas);
  await waitFor(() => expect(screen.queryByText('↓ 아래로 스크롤해 더 보기')).toBeNull());
  rerender(<Stage editing />);
  expect(container.querySelector('.design-edit-clearance')).toBeTruthy();
  expect(container.querySelector<HTMLElement>('.flow-boundary')?.style.top).toBe('644px');
  expect(screen.queryByRole('button', { name: '번호 모두 표시' })).toBeNull();
});
