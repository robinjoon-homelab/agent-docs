import { assert, describe, expect, it } from 'vitest';
import type { FlowKind } from '../document/block';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { addDesignElement } from './addDesignElement';
import { designAddressKey } from './designAddress';
import { resolveDesignScene } from './designScene';
import { records, sourceBlock, stringValue } from './editDesign';
import {
  canSetNote,
  deleteDesignScreen,
  duplicateDesignScreen,
  moveDesignScreen,
  setScreenPreset,
} from './editDesignScreens';
import { createDesignBlock } from './editDesignStructure';
import { restoreDesignEdit } from './editDesignUndo';
import { KINDS } from './flow';
import { newDesignFlow } from './newDesignFlow';

function allocate() {
  let n = 0;
  return () => 'allocated' + String(++n);
}
const block = (id: string, design: DesignObject): FlowDesignBlock => ({ id, type: 'flowDesign', design });
function data() {
  return [
    ...newDesignFlow('settings1', 'first', 'phone', 'Q'),
    block('second01', {
      id: 'other',
      kind: 'screen',
      code: 'Q-06',
      name: '다음',
      frame: 'note',
      description: '글',
    }),
  ];
}
const seq = (bs: readonly FlowDesignBlock[]) =>
  bs.filter((b) => b.design.kind === 'screen').map((b) => b.design.code);

describe('design screen management', () => {
  it('inserts after the current screen, reorders one place and never reuses an issued serial after deletion', () => {
    let bs = createDesignBlock(data(), 'screen', '새 화면', 'third', 'first');
    expect(seq(bs)).toEqual(['Q-01', 'Q-07', 'Q-06']);
    bs = moveDesignScreen(bs, 'third', 1);
    expect(seq(bs)).toEqual(['Q-01', 'Q-06', 'Q-07']);
    bs = deleteDesignScreen(bs, 'third').blocks;
    bs = bs.map((b) => (b.design.kind === 'settings' ? { ...b, design: { ...b.design, idPrefix: '' } } : b));
    bs = createDesignBlock(bs, 'screen', '다른 이름', 'fourth', 'first');
    expect(seq(bs)).toEqual(['Q-01', 'S-08', 'Q-06']);
    expect(deleteDesignScreen(bs, 'first').screen).toBe('fourth');
  });
  it('reserves the number after undo, including an intervening redo', () => {
    const original = data();
    const created = createDesignBlock(original, 'screen', '검토', 'review', 'first');
    const undone = restoreDesignEdit(created, original, created);
    expect(undone[0]?.design.nextScreen).toBe(8);
    expect(seq(restoreDesignEdit(original, created, undone))).toEqual(['Q-01', 'Q-07', 'Q-06']);
    expect(seq(createDesignBlock(undone, 'screen', '확인', 'another', 'first'))).toEqual([
      'Q-01',
      'Q-08',
      'Q-06',
    ]);
  });
  it('duplicates state insertions, slot assignments, instance paths and self-navigation in new scopes', () => {
    const b = [
      ...data().slice(0, 1),
      block('shared01', {
        id: 'widget',
        kind: 'component',
        name: '부품',
        props: [{ id: 'content', type: 'slot' }],
        root: { id: 'child', kind: 'box', children: [{ id: 'slot', kind: 'slot', prop: 'content' }] },
      }),
      block('first001', {
        id: 'first',
        kind: 'screen',
        code: 'Q-01',
        name: '폼',
        root: {
          id: 'body',
          kind: 'box',
          children: [
            {
              id: 'one',
              kind: 'instance',
              component: 'widget',
              slots: { content: [{ id: 'slotted', kind: 'text', text: '공통' }] },
            },
          ],
        },
      }),
      block('state001', {
        id: 'editing',
        kind: 'state',
        name: '편집',
        screen: 'first',
        changes: [
          {
            op: 'insert',
            at: { parent: { owner: 'first', node: 'body' }, edge: 'last' },
            node: { id: 'new', kind: 'text', text: '새로 추가' },
          },
          { op: 'leaf', target: { owner: 'first', node: 'slotted' }, path: 'text', value: '편집 문구' },
        ],
      }),
      block('annot001', {
        id: 'action',
        kind: 'annotation',
        name: '부품 설명',
        screen: 'first',
        state: 'editing',
        target: {
          owner: 'widget',
          node: 'child',
          via: [{ owner: 'first', instance: 'one', component: 'widget' }],
        },
        actions: [{ when: '누르면', mode: 'same', to: { screen: 'first', state: 'editing' } }],
      }),
      block('annot002', {
        id: 'other-action',
        kind: 'annotation',
        screen: 'first',
        state: 'editing',
        target: { owner: 'editing', node: 'new' },
      }),
      ...data().slice(-1),
    ];
    const copy = duplicateDesignScreen(b, 'first', allocate()),
      dest = sourceBlock(copy.blocks, copy.screen, 'screen');
    assert(dest);
    expect(seq(copy.blocks)).toEqual(['Q-01', 'Q-07', 'Q-06']);
    expect(dest.design.name).toBe('폼 사본');
    const st = copy.blocks.find((i) => i.design.screen === dest.design.id && i.design.kind === 'state');
    assert(st);
    const notes = copy.blocks.filter(
      (i) => i.design.screen === dest.design.id && i.design.kind === 'annotation',
    );
    expect(notes[0]?.design.target).toMatchObject({
      owner: 'widget',
      node: 'child',
      via: [{ owner: dest.design.id, component: 'widget' }],
    });
    expect(notes[0]?.design.actions).toEqual([
      { when: '누르면', mode: 'same', to: { screen: dest.design.id, state: st.design.id } },
    ]);
    const scene = resolveDesignScene(copy.blocks, copy.screen, stringValue(st.design.id));
    expect(scene.warnings).toEqual([]);
    expect(scene.root?.children[1]?.node.text).toBe('새로 추가');
    expect(scene.root?.children[0]?.children[0]?.node.text).toBe('편집 문구');
    expect(notes[1]?.design.target).toEqual(scene.root?.children[1]?.address);
    expect(Object.keys(notes[1]?.design.target ?? {})).not.toHaveLength(0);
    expect(scene.root?.children[0]?.mounts.some((m) => m.node === 'one')).toBe(false);
    expect(b.every((old) => old.design.kind === 'settings' || copy.blocks.includes(old))).toBe(true);
  });
  it('deletes exactly the screen family, leaving incoming routes and unrelated blocks intact', () => {
    const other = block('outside1', {
      id: 'incoming',
      kind: 'annotation',
      screen: 'other',
      target: { unresolvedName: 'old' },
      actions: [{ when: '저장', mode: 'page', to: { screen: 'first' } }],
    });
    const bs = [
      ...data(),
      block('state001', { id: 'busy', kind: 'state', screen: 'first' }),
      block('annot001', { id: 'note', kind: 'annotation', screen: 'first' }),
      other,
    ];
    const after = deleteDesignScreen(bs, 'first');
    expect(after.screen).toBe('second01');
    expect(after.blocks.map((x) => x.id)).toEqual(['settings1', 'second01', 'outside1']);
    expect(after.blocks.at(-1)).toBe(other);
    expect(() => deleteDesignScreen(after.blocks, 'other')).toThrow('마지막 화면');
  });
  it('only converts an empty screen to a note and recreates its canvas when switched back', () => {
    const bs = data(),
      empty = sourceBlock(bs, 'first');
    assert(empty);
    expect(canSetNote(bs, empty)).toBe(true);
    expect(
      canSetNote([...bs, block('active01', { id: 'busy', kind: 'state', screen: 'first' })], empty),
    ).toBe(false);
    const note = setScreenPreset(bs, 'first', 'note', { width: 1, height: 1 });
    expect(sourceBlock(note, 'first')?.design.root).toBeUndefined();
    const canvas = setScreenPreset(note, 'first', '', { width: 0, height: 0 });
    expect(sourceBlock(canvas, 'first')?.design).toMatchObject({
      root: { kind: 'box', layout: { mode: 'free' } },
    });
    expect(sourceBlock(canvas, 'first')?.design.frame).toBeUndefined();
  });
});

describe('wire element palette', () => {
  it('creates the 16 editable kinds, matching annotation policy and using each portable definition only once', () => {
    let bs = data();
    const ids = allocate(),
      kinds = Object.keys(KINDS) as FlowKind[];
    for (const kind of kinds) {
      const result = addDesignElement(
        bs,
        { screen: 'first' },
        kind,
        { width: 390, height: 844 },
        undefined,
        ids,
      );
      bs = result.blocks;
      const annotation = bs.find((v) => v.design.kind === 'annotation' && v.design.id === result.target.node);
      assert(annotation);
      expect(annotation.design.enabled).toBe(kind !== 'title' && kind !== 'box');
      expect(annotation.design.target).toEqual(result.target);
    }
    expect(bs.filter((x) => x.design.kind === 'component')).toHaveLength(16);
    const result = addDesignElement(
      bs,
      { screen: 'first' },
      'button',
      { width: 390, height: 844 },
      { x: 18, y: 580, w: 354, h: 44 },
      ids,
    );
    expect(result.blocks.filter((x) => x.design.kind === 'component')).toHaveLength(16);
    const scene = resolveDesignScene(result.blocks, 'first');
    expect(scene.warnings).toEqual([]);
    expect(scene.root?.children).toHaveLength(17);
    const placed = scene.root?.children.at(-1);
    expect(placed?.node.position).toMatchObject({ x: { start: 18 }, y: { start: 636 } });
    expect(placed?.mounts.map(designAddressKey)).toContain(designAddressKey(result.target));
  });
  it('adds a state-only element as a state-owned node without adding anything to the base screen', () => {
    const bs = createDesignBlock(data(), 'state', '저장 중', 'saving', 'first');
    const result = addDesignElement(
      bs,
      { screen: 'first', state: 'saving' },
      'bar',
      { width: 360, height: 800 },
      undefined,
      allocate(),
    );
    const state = sourceBlock(result.blocks, 'saving', 'state');
    assert(state);
    expect(records(state.design.changes)[0]).toMatchObject({
      op: 'insert',
      node: { size: { x: { px: 360 } }, position: { x: { start: 0 }, y: { start: 0 } } },
    });
    expect(result.blocks.at(-1)?.design.target).toEqual({ owner: 'saving', node: result.target.node });
    expect(resolveDesignScene(result.blocks, 'first').root?.children.length).toBe(0);
    expect(resolveDesignScene(result.blocks, 'first', 'saving').root?.children.length).toBe(1);
  });
});
