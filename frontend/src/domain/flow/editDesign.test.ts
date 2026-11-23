import { describe, expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { resolveDesignScene, designAddressKey } from './designScene';
import {
  editDesignNode,
  editDesignPrecision,
  findDesignNode,
  object,
  records,
  reorderDesignChild,
  setDesignToken,
} from './editDesign';
import {
  createDesignBlock,
  addDesignChild,
  makeDesignChild,
  addDesignAnnotation,
  setDesignFrame,
} from './editDesignStructure';
const block = (external: string, design: DesignObject): FlowDesignBlock => ({
  id: external,
  type: 'flowDesign',
  design,
});
const fixture = () => [
  block('settings001', {
    id: 'cfg',
    kind: 'settings',
    modelVersion: 4,
    frame: 'desktop',
    idPrefix: 'F',
    nextScreen: 2,
  }),
  block('tokens0001', {
    id: 'theme',
    kind: 'tokens',
    entries: [{ id: 'ink', name: '강조색', type: 'color', value: '#003344' }],
  }),
  block('button0001', {
    id: 'button',
    kind: 'component',
    name: '공통 버튼',
    props: [{ id: 'caption', name: '문구', type: 'text', initial: '저장' }],
    root: {
      id: 'button-root',
      kind: 'box',
      layout: { mode: 'row' },
      style: { fill: { token: 'ink' } },
      children: [{ id: 'label', kind: 'text', text: { prop: 'caption' } }],
    },
  }),
  block('screen0001', {
    id: 'memo',
    kind: 'screen',
    code: 'F-01',
    name: '메모',
    root: {
      id: 'form',
      kind: 'box',
      layout: { mode: 'column', gap: 5 },
      children: [
        { id: 'save', kind: 'instance', component: 'button' },
        { id: 'copy', kind: 'instance', component: 'button' },
      ],
    },
  }),
  block('state00001', { kind: 'state', id: 'saving', screen: 'memo', name: '저장 중', changes: [] }),
];
const form = { owner: 'memo', node: 'form' },
  save = { owner: 'memo', node: 'save' },
  copy = { owner: 'memo', node: 'copy' };
const nested = {
  owner: 'button',
  node: 'label',
  via: [{ owner: 'memo', instance: 'save', component: 'button' }],
};
describe('v4 시각 편집의 저장 범위', () => {
  it('사람이 고친 인스턴스 문구와 공통색을 유지하며 선택된 인스턴스만 내부 글자 속성을 바꾼다', () => {
    let b = editDesignNode(fixture(), { screen: 'memo' }, save, {
      kind: 'input',
      prop: 'caption',
      value: '초안 저장',
    });
    b = setDesignToken(b, 'theme', 'ink', '#224466');
    b = editDesignPrecision(b, { screen: 'memo' }, nested, {
      op: 'leaf',
      path: 'style.text.weight',
      value: 700,
    });
    const scene = resolveDesignScene(b, 'memo');
    const first = scene.root?.children[0],
      second = scene.root?.children[1];
    expect(first?.children[0]?.node.text).toBe('초안 저장');
    expect(second?.children[0]?.node.text).toBe('저장');
    expect(first?.children[0]?.node.style).toMatchObject({ text: { weight: 700 } });
    expect(object(object(second?.children[0]?.node.style)?.text)?.weight).toBeUndefined();
    expect(first?.node.style).toMatchObject({ fill: '#224466' });
    expect(second?.node.style).toMatchObject({ fill: '#224466' });
    expect(b[2]).toEqual(fixture()[2]);
  });
  it('상태의 순서와 문구·스크롤 차이를 저장하고 기본 화면과 다른 인스턴스는 보존한다', () => {
    const base = fixture(),
      scope = { screen: 'memo', state: 'saving' };
    let next = reorderDesignChild(base, scope, form, copy, save, true);
    next = editDesignNode(next, scope, save, { kind: 'input', prop: 'caption', value: '저장 중…' });
    next = editDesignNode(next, scope, form, { kind: 'leaf', path: 'scroll.y', value: 'end' });
    expect(next.slice(0, 4)).toEqual(base.slice(0, 4));
    expect(records(next[4]?.design.changes).map((op) => op.op)).toEqual(['move', 'input', 'leaf']);
    const projected = resolveDesignScene(next, 'memo', 'saving');
    expect(projected.root?.children[0]?.mounts).toContainEqual(copy);
    expect(projected.inputs.get(designAddressKey(save))?.caption).toBe('저장 중…');
    expect(resolveDesignScene(next, 'memo').inputs.get(designAddressKey(save))?.caption).toBe('저장');
  });
  it('상태에서 같은 유효 필드만 되풀이해 바꾸어도 가장 최신 값이 남는다', () => {
    const scope = { screen: 'memo', state: 'saving' };
    let next = editDesignNode(fixture(), scope, form, { kind: 'leaf', path: 'layout.gap', value: 10 });
    next = editDesignNode(next, scope, form, { kind: 'leaf', path: 'layout.gap', value: 12 });
    expect(next[4]?.design.changes).toEqual([{ op: 'leaf', target: form, path: 'layout.gap', value: 12 }]);
    expect(() =>
      editDesignNode(next, { screen: 'wrong', state: 'saving' }, form, {
        kind: 'leaf',
        path: 'layout.gap',
        value: 4,
      }),
    ).toThrow();
  });
  it('화면 생성 시 다음 번호와 저장 ID를 지정하고 상태 전용 새 요소·주석은 해당 상태에 붙인다', () => {
    let next = createDesignBlock(fixture(), 'screen', '검토', 'new-screen', 'memo');
    next = createDesignBlock(next, 'state', '확인', 'new-state', 'new-screen');
    const newRoot = { owner: 'new-screen', node: 'root' };
    next = addDesignChild(
      next,
      'new-screen',
      'new-state',
      newRoot,
      makeDesignChild('text', 'extra', '이 상태에서만', next),
    );
    next = addDesignAnnotation(
      next,
      'new-screen',
      'new-state',
      { owner: 'new-state', node: 'extra' },
      'note',
      '설명',
      '추가한 안내',
    );
    next = setDesignFrame(next, 'new-screen', 'new-state', 600, 400);
    expect(next[0]?.design.nextScreen).toBe(3);
    expect(next.find((b) => b.id === 'new-screen')?.design.code).toBe('F-02');
    expect(findDesignNode(next, { owner: 'new-state', node: 'extra' })?.text).toBe('이 상태에서만');
    expect(next.at(-1)?.design).toMatchObject({
      kind: 'annotation',
      state: 'new-state',
      target: { owner: 'new-state', node: 'extra' },
    });
    expect(resolveDesignScene(next, 'new-screen').root?.children).toHaveLength(0);
    expect(resolveDesignScene(next, 'new-screen', 'new-state').frame).toEqual({ width: 600, height: 400 });
  });
  it('형제 가운데 선택한 항목만 이동하고 그룹을 바꾼 오래된 주소는 거절한다', () => {
    const next = reorderDesignChild(fixture(), { screen: 'memo' }, form, copy, save, true);
    expect(records(findDesignNode(next, form)?.children).map((n) => n.id)).toEqual(['copy', 'save']);
    expect(() =>
      editDesignNode(
        next,
        { screen: 'memo' },
        { ...nested, via: [{ owner: 'memo', instance: 'save', component: 'new-origin' }] },
        { kind: 'leaf', path: 'text', value: '잘못된 대상' },
      ),
    ).toThrow();
  });
});
