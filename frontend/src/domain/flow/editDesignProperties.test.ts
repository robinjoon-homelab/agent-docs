import { expect, it } from 'vitest';
import type { FlowDesignBlock } from '../document/designBlock';
import type { FlowKind } from '../document/block';
import { designAddressKey, type DesignOccurrence } from './designAddress';
import { resolveDesignScene } from './designScene';
import { addDesignElement } from './addDesignElement';
import { findDesignNode, object, records, setDesignAction, stringValue } from './editDesign';
import {
  editScopedAnnotation,
  editWireAppearance,
  patchAnnotation,
  scopedAnnotation,
  wireAppearance,
  wireKind,
  wireListRows,
} from './editDesignProperties';

const frame = { width: 390, height: 844 };
function fixture(kind: FlowKind) {
  let seq = 0;
  const allocate = () => 'local' + (++seq).toString().padStart(4, '0');
  const base: FlowDesignBlock[] = [
    {
      type: 'flowDesign',
      id: 'config01',
      design: {
        id: 'config',
        kind: 'settings',
        modelVersion: 4,
        frame: 'phone',
        idPrefix: 'M',
        nextScreen: 2,
      },
    },
    {
      type: 'flowDesign',
      id: 'screen01',
      design: {
        id: 'home',
        kind: 'screen',
        code: 'M-01',
        name: '홈',
        root: { kind: 'box', id: 'root', layout: { mode: 'free' }, children: [] },
      },
    },
    {
      type: 'flowDesign',
      id: 'state001',
      design: { id: 'busy', kind: 'state', screen: 'home', name: '대기', changes: [] },
    },
  ];
  const { blocks, target } = addDesignElement(base, { screen: 'home' }, kind, frame, undefined, allocate);
  const value = (bs: readonly FlowDesignBlock[], state?: string) => {
    const node = findDesignNode(bs, target);
    if (!node) throw Error('missing');
    const k = wireKind(node);
    if (!k) throw Error('expected wire');
    return wireAppearance(
      k,
      resolveDesignScene(bs, 'home', state).inputs.get(designAddressKey(target)) ?? {},
      stringValue(node.name),
    );
  };
  return { blocks, target, allocate, value };
}
function images(root: DesignOccurrence | null): DesignOccurrence[] {
  return root ? [...(root.node.kind === 'image' ? [root] : []), ...root.children.flatMap(images)] : [];
}
it('editing the label and look of one state leaves the shared button, its base annotation and other occurrences unchanged', () => {
  const { blocks, target, value, allocate } = fixture('button');
  const scope = { screen: 'home', state: 'busy' };
  const initial = value(blocks);
  const next = editWireAppearance(
    blocks,
    scope,
    target,
    initial,
    { label: '진행 중', value: '저장 중…', look: 'disabled' },
    frame,
    allocate,
  );
  expect(value(next)).toEqual(initial);
  expect(value(next, 'busy')).toMatchObject({ label: '진행 중', value: '저장 중…', look: 'disabled' });
  expect(next.find((b) => b.design.id === 'wire:button')).toEqual(
    blocks.find((b) => b.design.id === 'wire:button'),
  );
  const base = scopedAnnotation(next, 'home', undefined, target);
  const busy = scopedAnnotation(next, 'home', 'busy', target);
  expect(base?.id).toBe(target.node);
  expect(busy?.id).not.toBe(base?.id);
  expect(busy?.design.name).toBe('진행 중');
});
it('state annotation descriptions, visibility and destinations are scoped to their stable annotation ID', () => {
  const { blocks, target, allocate } = fixture('field');
  const scope = { screen: 'home', state: 'busy' };
  const changed = patchAnnotation(
    blocks,
    scope,
    target,
    target.node,
    { enabled: false, description: '기다려 주세요.' },
    allocate,
  );
  expect(scopedAnnotation(changed, 'home', undefined, target)?.design.enabled).toBe(true);
  const busy = scopedAnnotation(changed, 'home', 'busy', target);
  if (!busy) throw Error('missing');
  expect(busy.design).toMatchObject({ state: 'busy', description: '기다려 주세요.', enabled: false });
  const actions = editScopedAnnotation(changed, scope, target, busy.id, allocate, (bs, id) =>
    setDesignAction(bs, id, { add: { when: '계속', mode: 'same', to: { screen: 'home', state: 'busy' } } }),
  );
  const first = records(scopedAnnotation(actions, 'home', 'busy', target)?.design.actions)[0];
  if (!first) throw Error('missing');
  const noDest = setDesignAction(actions, busy.id, {
    replace: 0,
    expected: first,
    action: { when: '다시 시도', mode: 'same' },
  });
  expect(scopedAnnotation(noDest, 'home', 'busy', target)?.design.actions).toEqual([
    { when: '다시 시도', mode: 'same' },
  ]);
  expect(scopedAnnotation(noDest, 'home', undefined, target)?.design.actions).toBeUndefined();
  expect(() => setDesignAction(noDest, busy.id, { remove: 0, expected: first })).toThrow(/바뀌/);
});
it('the image slot follows an uploaded asset, keeps its node ID on replacement and supports an independent state image', () => {
  const { blocks, target, allocate, value } = fixture('image');
  const base = { screen: 'home' },
    scope = { ...base, state: 'busy' };
  const first = '00000000-0000-4000-8000-000000000001',
    second = '00000000-0000-4000-8000-000000000002';
  const one = editWireAppearance(
    blocks,
    base,
    target,
    value(blocks),
    { value: 'asset:' + first, label: '로고' },
    frame,
    allocate,
  );
  const pic = images(resolveDesignScene(one, 'home').root)[0];
  expect(pic?.node).toMatchObject({ asset: first, alt: '로고' });
  const two = editWireAppearance(
    one,
    scope,
    target,
    value(one, 'busy'),
    { value: 'asset:' + second },
    frame,
    allocate,
  );
  expect(images(resolveDesignScene(two, 'home', 'busy').root)[0]?.node).toMatchObject({
    id: pic?.node.id,
    asset: second,
    alt: '로고',
  });
  expect(images(resolveDesignScene(two, 'home').root)[0]?.node.asset).toBe(first);
  const cleared = editWireAppearance(two, scope, target, value(two, 'busy'), { value: '' }, frame, allocate);
  expect(images(resolveDesignScene(cleared, 'home', 'busy').root)).toEqual([]);
  const changes = records(cleared.find((b) => b.design.id === 'busy')?.design.changes);
  expect(changes.some((c) => object(c.target)?.node === pic?.node.id)).toBe(false);
  expect(images(resolveDesignScene(cleared, 'home').root)).toHaveLength(1);
  expect(() =>
    editWireAppearance(two, base, target, value(blocks), { value: 'asset:' + first }, frame, allocate),
  ).toThrow(/바뀌/);
});
it('list and date controls preserve row identity and change observable component data', () => {
  const old = [
    { itemKey: 'a', fields: { text: '우유' } },
    { itemKey: 'b', fields: { text: '빵' } },
    { itemKey: 'c', fields: { text: '빵' } },
  ];
  const rows = wireListRows(old, ['새 항목', '빵', '빵', '우유'], () => 'added');
  expect(rows.map((r) => r.itemKey)).toEqual(['added', 'b', 'c', 'a']);
  expect(wireListRows(rows, ['새 항목', '달걀', '빵', '우유'], () => 'unused').map((r) => r.itemKey)).toEqual(
    ['added', 'b', 'c', 'a'],
  );
  const { blocks, target, value, allocate } = fixture('path');
  const next = editWireAppearance(
    blocks,
    { screen: 'home' },
    target,
    value(blocks),
    { date: '2026-11-25' },
    frame,
    allocate,
  );
  expect(object(findDesignNode(next, target)?.inputs)).toMatchObject({
    date: '2026-11-25',
    dateLayout: 'dated',
  });
});
it('changing wire kind keeps the occurrence and comment anchor identities and supplies the new component', () => {
  const { blocks, target, value, allocate } = fixture('button');
  const next = editWireAppearance(
    blocks,
    { screen: 'home' },
    target,
    value(blocks),
    { kind: 'list' },
    frame,
    allocate,
  );
  expect(wireKind(findDesignNode(next, target) ?? {})).toBe('list');
  expect(scopedAnnotation(next, 'home', undefined, target)?.id).toBe(target.node);
  expect(value(next).value).toBe(value(blocks).value);
  expect(next.filter((b) => b.design.id === 'wire:list')).toHaveLength(1);
});
