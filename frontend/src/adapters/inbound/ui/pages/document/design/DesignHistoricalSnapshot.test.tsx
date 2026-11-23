// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { ServicesProvider } from '../../../../../../config/ServicesContext';
import { createFakeServices } from '../../../../../../config/fakeServices';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { thread } from '../../../../../../test-support/fixtures';
import { FlowSnapshot } from '../FlowThreadHistory';

afterEach(cleanup);
function blocks(text: string): FlowDesignBlock[] {
  return [
    { type: 'flowDesign', id: 's', design: { kind: 'settings', modelVersion: 4, id: 'set', frame: 'phone' } },
    {
      type: 'flowDesign',
      id: 'page',
      design: {
        kind: 'screen',
        id: 'screen',
        name: '결제',
        root: { kind: 'instance', id: 'button', component: 'shared' },
      },
    },
    {
      type: 'flowDesign',
      id: 'shared',
      design: { kind: 'component', id: 'shared', root: { kind: 'text', id: 'label', text } },
    },
    {
      type: 'flowDesign',
      id: 'state-block',
      design: { kind: 'state', id: 'busy', screen: 'screen', name: '대기 중', changes: [] },
    },
    {
      type: 'flowDesign',
      id: 'ann',
      design: {
        kind: 'annotation',
        id: 'save',
        name: '결제 버튼',
        screen: 'screen',
        target: { owner: 'screen', node: 'button' },
      },
    },
  ];
}
it('renders the historical revision’s component in the anchored state and returns to its current anchor', () => {
  const jump = vi.fn();
  const { container } = render(
    <ServicesProvider services={createFakeServices()}>
      <FlowSnapshot
        blocks={blocks('옛 문구')}
        current={blocks('새 문구')}
        blockId="ann"
        thread={thread({ anchor: { kind: 'block', blockId: 'ann', viewedStateId: 'state-block' } })}
        revision={4}
        onClose={vi.fn()}
        onSelectScreen={jump}
        threadCard={<p>이전 안내입니다.</p>}
        threads={[]}
        onInfo={vi.fn()}
        onComments={vi.fn()}
        onPanelCollapse={vi.fn()}
      />
    </ServicesProvider>,
  );
  expect(screen.getByText('옛 문구')).toBeTruthy();
  expect(screen.queryByText('새 문구')).toBeNull();
  expect(container.querySelector('[data-design-screen="screen"][data-design-state="busy"]')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '현재 요소 보기' }));
  expect(jump).toHaveBeenCalledWith('ann', 'state-block');
});
