import { expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { findDesignNode, object, records, setDesignAction } from './editDesign';
import {
  arrangeDesignSiblings,
  copyDesignSiblings,
  markDesignSiblings,
  pasteDesignSiblings,
} from './editDesignSelection';
const pos = (x: number) => ({ mode: 'absolute', x: { start: x }, y: { start: 0 } });
const box = (id: string, children: DesignObject[]) => ({
  id,
  kind: 'box',
  layout: { mode: 'free' },
  children,
});
const block = (id: string, design: DesignObject): FlowDesignBlock => ({ id, type: 'flowDesign', design });
const parent = { owner: 'source', node: 'root' },
  dest = { owner: 'dest', node: 'root' };
const bs = [
  block('a', {
    kind: 'screen',
    id: 'source',
    root: box('root', [
      { kind: 'instance', id: 'save', position: pos(5), component: 'button' },
      box('panel', [{ id: 'note', kind: 'text', text: '예시' }]),
    ]),
  }),
  block('b', { kind: 'screen', id: 'dest', root: box('root', []) }),
  block('button', {
    kind: 'component',
    id: 'button',
    root: box('body', [{ kind: 'text', id: 'label', text: '저장' }]),
  }),
  block('annotation1', {
    kind: 'annotation',
    id: 'annotation1',
    screen: 'source',
    target: {
      owner: 'button',
      node: 'label',
      via: [{ owner: 'source', instance: 'save', component: 'button' }],
    },
    description: '현재 저장 버튼',
  }),
  block('annotation2', {
    kind: 'annotation',
    id: 'annotation2',
    screen: 'source',
    target: { owner: 'source', node: 'note' },
    description: '글자 설명',
  }),
];
it('다른 화면에 붙인 그룹·인스턴스의 ID와 설명을 새 소유 범위로 옮기고 원본·원본 대상은 보존한다', () => {
  let next = 0;
  const grouped = markDesignSiblings(bs, parent, ['save', 'panel'], 'group', 'same');
  const clip = copyDesignSiblings(grouped, parent, ['save', 'panel']);
  const output = pasteDesignSiblings(grouped, dest, clip, () => String(++next));
  const [instance, container] = records(findDesignNode(output.blocks, dest)?.children);
  expect(instance).toMatchObject({
    component: 'button',
    group: '4',
    position: { mode: 'absolute', x: { start: 21 }, y: { start: 16 } },
  });
  expect(container).toMatchObject({ group: '4', children: [{ id: '3', text: '예시' }] });
  expect(output.blocks.slice(-2).map((b) => b.design.target)).toEqual([
    { owner: 'button', node: 'label', via: [{ owner: 'dest', instance: '1', component: 'button' }] },
    { owner: 'dest', node: '3' },
  ]);
  expect(output.blocks[2]).toEqual(bs[2]);
  expect(output.blocks[3]).toEqual(bs[3]);
});
it('자유 레이어를 측정한 픽셀 범위로 정렬하되 잠근 레이어가 포함되면 이동하지 않는다', () => {
  const source = block('a', {
    id: 'source',
    kind: 'screen',
    root: box('root', [
      { id: '1', kind: 'text', text: 'A', position: pos(5) },
      { id: '2', kind: 'text', text: 'B', position: pos(80) },
    ]),
  });
  const frames = new Map([
    ['1', { x: 5, y: 0, w: 10, h: 10 }],
    ['2', { x: 80, y: 0, w: 40, h: 10 }],
  ]);
  const aligned = arrangeDesignSiblings([source], parent, ['1', '2'], 'right', frames);
  expect(object(object(findDesignNode(aligned, { owner: 'source', node: '1' })?.position)?.x)?.start).toBe(
    110,
  );
  const locked = markDesignSiblings([source], parent, ['1'], 'locked', true);
  expect(() => arrangeDesignSiblings(locked, parent, ['1', '2'], 'right', frames)).toThrow('잠기지 않은');
});
it('유효한 이동 목적지만 추가하고 오래된 삭제 요청은 문서에 적용하지 않는다', () => {
  const action = { when: '누르면', to: { screen: 'dest' }, mode: 'same' };
  const next = setDesignAction(bs, 'annotation1', { add: action });
  expect(next[3]?.design.actions).toEqual([action]);
  expect(() =>
    setDesignAction(next, 'annotation1', { remove: 0, expected: { ...action, mode: 'error' } }),
  ).toThrow('바뀌었습니다.');
  expect(() =>
    setDesignAction(bs, 'annotation1', { add: { ...action, to: { screen: 'dest', state: 'foreign' } } }),
  ).toThrow();
});
