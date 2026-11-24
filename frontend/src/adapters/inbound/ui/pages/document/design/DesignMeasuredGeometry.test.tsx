// @vitest-environment jsdom
import { useState } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { object } from '../../../../../../domain/flow/editDesign';
import { designRows, lookupRow } from './designLocation';
import { DesignNodeInspector } from './DesignNodeInspector';
afterEach(cleanup);
function GeometryEditor({ position }: { position?: DesignObject }) {
  const [state, setState] = useState<string | undefined>('saving');
  const [blocks, setBlocks] = useState<readonly FlowDesignBlock[]>([
    {
      id: 'config01',
      type: 'flowDesign',
      design: {
        kind: 'settings',
        id: 'config',
        modelVersion: 4,
        frame: 'phone',
        idPrefix: 'M',
        nextScreen: 2,
      },
    },
    {
      id: 'screen01',
      type: 'flowDesign',
      design: {
        kind: 'screen',
        id: 'home',
        code: 'M-01',
        name: '메모',
        root: {
          kind: 'box',
          id: 'root',
          layout: { mode: 'row' },
          children: [
            {
              kind: 'instance',
              id: 'save',
              component: 'primary-button',
              ...(position ? { position } : {}),
              boxSizing: 'content-box',
              flexItem: { grow: 1, basis: { percent: 0 }, shrink: 1 },
              size: { x: { mode: 'fill', min: 40, max: 500 }, y: { mode: 'fixed', px: 52 } },
            },
          ],
        },
      },
    },
    {
      id: 'busy0001',
      type: 'flowDesign',
      design: { kind: 'state', id: 'saving', name: '저장 중', screen: 'home', changes: [] },
    },
    {
      id: 'button01',
      type: 'flowDesign',
      design: {
        kind: 'component',
        id: 'primary-button',
        name: '기본 버튼',
        props: [],
        root: { id: 'button', kind: 'box', layout: { mode: 'row' }, children: [] },
      },
    },
  ]);
  const scene = resolveDesignScene(blocks, 'home', state),
    rows = designRows(scene.root, blocks);
  const row = lookupRow(rows, { owner: 'home', node: 'save' });
  if (!row) throw Error('Missing button');
  const node = row.occurrence.node,
    size = object(object(node.size)?.x);
  const w = size?.mode === 'fixed' && typeof size.px === 'number' ? size.px + 8 : 350;
  return (
    <>
      <button onClick={() => setState(state ? undefined : 'saving')}>상태 전환</button>
      <output aria-label="가로 규칙">{JSON.stringify(size)}</output>
      <output aria-label="부모 배치 규칙">{JSON.stringify(node.flexItem)}</output>
      <output aria-label="가로 앵커">{JSON.stringify(object(node.position)?.x)}</output>
      <DesignNodeInspector
        blocks={blocks}
        screen="home"
        state={state}
        row={row}
        rows={rows}
        inputs={scene.inputs}
        frame={{ width: 390, height: 844 }}
        measuredRect={{
          x: 20,
          y: 739.9999,
          w,
          h: 52,
          parent: { width: 400, height: 844 },
          contentInset: { x: 8, y: 0 },
        }}
        canResizeMeasured
        mutate={(change) => {
          setBlocks(change(blocks));
          return null;
        }}
        select={() => undefined}
      />
    </>
  );
}
function number(label: string) {
  return screen.getByRole<HTMLInputElement>('spinbutton', { name: label });
}
function change(label: string, value: string) {
  const field = number(label);
  fireEvent.change(field, { target: { value } });
  fireEvent.blur(field);
}
it('shows both auto-layout coordinates read-only and writes a measured fill width to the selected instance state just like the east handle', () => {
  render(<GeometryEditor />);
  expect([number('X').value, number('Y').value, number('가로').value, number('세로').value]).toEqual([
    '20',
    '740',
    '350',
    '52',
  ]);
  expect(number('X').readOnly && number('Y').readOnly).toBe(true);
  expect(number('가로').readOnly || number('세로').readOnly).toBe(false);
  expect(screen.getByText('X와 Y는 자동 배치가 정한 읽기 전용 좌표입니다.')).toBeTruthy();
  change('가로', '310');
  expect(number('가로').value).toBe('310');
  expect(JSON.parse(screen.getByLabelText('가로 규칙').textContent)).toEqual({
    mode: 'fixed',
    px: 302,
    min: 40,
    max: 500,
  });
  expect(JSON.parse(screen.getByLabelText('부모 배치 규칙').textContent)).toEqual({
    grow: 0,
    shrink: 0,
    basis: 'auto',
  });
  fireEvent.click(screen.getByRole('button', { name: '상태 전환' }));
  expect(number('가로').value).toBe('350');
  expect(JSON.parse(screen.getByLabelText('가로 규칙').textContent)).toEqual({
    mode: 'fill',
    min: 40,
    max: 500,
  });
});
it('edits the measured position without replacing a trailing percentage anchor with a leading px anchor', () => {
  render(<GeometryEditor position={{ mode: 'absolute', x: { end: { percent: 10 } }, y: { start: 20 } }} />);
  expect(number('X').value).toBe('20');
  expect(number('X').readOnly).toBe(false);
  change('X', '40');
  expect(JSON.parse(screen.getByLabelText('가로 앵커').textContent)).toEqual({
    end: { percent: 5 },
  });
  fireEvent.click(screen.getByRole('button', { name: '상태 전환' }));
  expect(JSON.parse(screen.getByLabelText('가로 앵커').textContent)).toEqual({
    end: { percent: 10 },
  });
});
