// @vitest-environment jsdom
import { useState } from 'react';
import { afterEach, expect, it } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { DesignNodeInspector } from './DesignNodeInspector';
import { designRows, lookupRow } from './designLocation';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { scopedAnnotation } from '../../../../../../domain/flow/editDesignProperties';
import { addDesignElement } from '../../../../../../domain/flow/addDesignElement';
import { newDesignFlow } from '../../../../../../domain/flow/newDesignFlow';
import { stringValue } from '../../../../../../domain/flow/editDesign';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';
import type { FlowKind } from '../../../../../../domain/document/block';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
afterEach(cleanup);

function Editor({ kind, upload }: { kind: FlowKind; upload?: (file: File) => Promise<string> }) {
  const frame = { width: 390, height: 844 },
    sc = 'home0001';
  const [first] = useState(() => {
    let index = 0;
    const initial: FlowDesignBlock[] = [
      ...newDesignFlow('config01', sc),
      {
        type: 'flowDesign',
        id: 'wait0001',
        design: { kind: 'state', id: 'wait', screen: sc, name: '대기', changes: [] },
      },
    ];
    return addDesignElement(
      initial,
      { screen: sc },
      kind,
      frame,
      undefined,
      () => 'item' + String(++index).padStart(4, '0'),
    );
  });
  const [state, setState] = useState<string>();
  const [blocks, setBlocks] = useState(first.blocks);
  const scene = resolveDesignScene(blocks, sc, state);
  const rows = designRows(scene.root, blocks),
    row = lookupRow(rows, first.target);
  if (!row) throw Error('missing test layer');
  const ann = scopedAnnotation(blocks, sc, state, row.address);
  const values = scene.inputs.get(designAddressKey(row.address));
  return (
    <>
      <button onClick={() => setState(state ? undefined : 'wait')}>상태 전환</button>
      <output aria-label="저장한 문구">{stringValue(values?.value)}</output>
      <DesignNodeInspector
        blocks={blocks}
        screen={sc}
        state={state}
        row={row}
        rows={rows}
        inputs={scene.inputs}
        annotation={ann}
        number={1}
        frame={frame}
        uploadImage={upload}
        select={() => undefined}
        mutate={(change) => {
          setBlocks(change(blocks));
          return null;
        }}
      />
    </>
  );
}
it('shows the F-13 controls and edits the selected state without changing the base element', () => {
  render(<Editor kind="button" />);
  expect(screen.getByRole('heading', { name: '요소 속성' })).toBeTruthy();
  expect(screen.getByRole('checkbox', { name: '위치 잠금' })).toBeTruthy();
  expect(screen.getByRole('status', { name: '' }).textContent).toBe('설명 없음');
  const base = screen.getByLabelText<HTMLInputElement>('버튼 글자').value;
  fireEvent.click(screen.getByRole('button', { name: '상태 전환' }));
  fireEvent.change(screen.getByLabelText('버튼 글자'), { target: { value: '진행 중' } });
  fireEvent.change(screen.getByLabelText('모양'), { target: { value: 'disabled' } });
  fireEvent.change(screen.getByLabelText('설명 (보기 모드의 (i) 내용)'), {
    target: { value: '저장 요청을 기다립니다.' },
  });
  expect(screen.getByLabelText('저장한 문구').textContent).toBe('진행 중');
  fireEvent.click(screen.getByRole('button', { name: '+ 이동 추가' }));
  fireEvent.change(screen.getByLabelText('가는 화면'), { target: { value: 'home0001' } });
  fireEvent.change(screen.getByLabelText('목적 상태'), { target: { value: 'wait' } });
  fireEvent.change(screen.getByLabelText('조건'), { target: { value: '누르면' } });
  fireEvent.click(screen.getByRole('button', { name: '상태 전환' }));
  expect(screen.getByLabelText<HTMLInputElement>('버튼 글자').value).toBe(base);
  expect(screen.queryByLabelText('조건')).toBeNull();
  expect(screen.getByLabelText<HTMLTextAreaElement>('설명 (보기 모드의 (i) 내용)').value).toBe('');
  fireEvent.click(screen.getByRole('button', { name: '상태 전환' }));
  expect(screen.getByLabelText<HTMLSelectElement>('목적 상태').value).toBe('wait');
});
it('accepts a document-scoped upload from the element panel and displays the persisted attachment value', async () => {
  const id = 'a8531642-891a-4952-a8c8-daed77c13b55';
  const view = render(<Editor kind="image" upload={() => Promise.resolve(id)} />);
  const file = view.container.querySelector<HTMLInputElement>('input[type=file]');
  if (!file) throw Error('upload unavailable');
  fireEvent.change(file, { target: { files: [new File(['image'], 'logo.png', { type: 'image/png' })] } });
  await waitFor(() => expect(screen.getByLabelText('저장한 문구').textContent).toBe('asset:' + id));
});
