// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { stringValue } from '../../../../../../domain/flow/editDesign';
import { newDesignFlow } from '../../../../../../domain/flow/newDesignFlow';
import { createDesignBlock } from '../../../../../../domain/flow/editDesignStructure';
import { addNamedDesignScreen, DesignNoteEditor, DesignScreenProperties } from './DesignScreenProperties';
import type { DesignMutation } from './useDesignEditHistory';
import type { DesignLocation } from './designLocation';
afterEach(cleanup);
function Workspace() {
  const [blocks, setBlocks] = useState<FlowDesignBlock[]>(() =>
    createDesignBlock(newDesignFlow('settings1', 'mobile'), 'screen', '완료', 'done', 'mobile'),
  );
  const [current, setCurrent] = useState('mobile');
  const active = blocks.find((b) => b.id === current);
  if (!active) throw Error('Missing active screen');
  const mutate: DesignMutation = (change) => {
    setBlocks(change(blocks));
    return null;
  };
  const go = (l: DesignLocation) => setCurrent(l.screen);
  return (
    <>
      <div aria-label="화면 코드">
        {blocks
          .filter((b) => b.design.kind === 'screen')
          .map((b) => stringValue(b.design.code))
          .join(',')}
      </div>
      <button type="button" onClick={() => addNamedDesignScreen(mutate, go, active)}>
        화면 추가
      </button>
      <DesignScreenProperties
        blocks={blocks}
        screen={active}
        mutate={mutate}
        go={go}
        threadCount={3}
        elementCount={0}
        annotationCount={0}
        missingCount={0}
      />
      {active.design.frame === 'note' && <DesignNoteEditor screen={active} mutate={mutate} />}
    </>
  );
}
it('adds and duplicates after the current screen and confirms before removing a screen that has comments', () => {
  render(<Workspace />);
  fireEvent.change(screen.getByRole('textbox', { name: /화면 ID 접두어/ }), { target: { value: 'A' } });
  fireEvent.click(screen.getByRole('button', { name: '화면 추가' }));
  expect(screen.getByLabelText('화면 코드').textContent).toBe('M-01,A-03,M-02');
  fireEvent.click(screen.getByRole('button', { name: '화면 복제' }));
  expect(screen.getByLabelText('화면 코드').textContent).toBe('M-01,A-03,A-04,M-02');
  fireEvent.click(screen.getByRole('button', { name: '뒤로 ›' }));
  expect(screen.getByLabelText('화면 코드').textContent).toBe('M-01,A-03,M-02,A-04');
  fireEvent.click(screen.getByRole('button', { name: '화면 삭제' }));
  const prompt = screen.getByRole('alertdialog', { name: '화면 삭제 확인' });
  expect(prompt.textContent).toContain('댓글 3개');
  fireEvent.click(within(prompt).getByRole('button', { name: '취소' }));
  expect(screen.getByLabelText('화면 코드').textContent).toContain('A-04');
  fireEvent.click(screen.getByRole('button', { name: '화면 삭제' }));
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '삭제' }));
  expect(screen.getByLabelText('화면 코드').textContent).toBe('M-01,A-03,M-02');
});
it('switches an empty screen to a note and edits its body via the canvas as well as the inspector', () => {
  render(<Workspace />);
  fireEvent.change(screen.getByRole('combobox', { name: '기기 틀 (이 화면)' }), {
    target: { value: 'note' },
  });
  fireEvent.change(screen.getByRole('textbox', { name: '글 페이지 본문' }), {
    target: { value: '검토할 화면입니다.' },
  });
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: /화면 설명/ }).value).toBe(
    '검토할 화면입니다.',
  );
  fireEvent.change(screen.getByRole('textbox', { name: /화면 설명/ }), { target: { value: '변경된 설명' } });
  expect(screen.getByRole<HTMLTextAreaElement>('textbox', { name: '글 페이지 본문' }).value).toBe(
    '변경된 설명',
  );
});
