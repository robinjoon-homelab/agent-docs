import { expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { clearCanvasGroup, findDesignNode, editDesignNode, records } from './editDesign';
import { resolveDesignScene } from './designScene';
import { snapResize, writeLayerRect, type PlacedLayer } from './editCanvasGeometry';
import {
  copyCanvasStateLayers,
  childParent,
  copyCanvasLayers,
  duplicateCanvasLayers,
  removeCanvasLayers,
} from './editCanvasChildren';

const target = { owner: 'screen', node: 'label' };
const layer: DesignObject = {
  id: 'label',
  kind: 'text',
  text: '원본',
  name: '이름',
  position: { mode: 'absolute', x: { end: 12 }, y: { start: 14 } },
  size: { x: { mode: 'fixed', px: 40 }, y: { mode: 'fixed', px: 22 } },
};
const screen: FlowDesignBlock = {
  id: 'screen0000',
  type: 'flowDesign',
  design: {
    kind: 'screen',
    id: 'screen',
    root: { kind: 'box', id: 'root', layout: { mode: 'free' }, children: [layer] },
  },
};
const ann: FlowDesignBlock = {
  id: 'oldann0000',
  type: 'flowDesign',
  design: { kind: 'annotation', id: 'what', screen: 'screen', name: '기획', target },
};
const state: FlowDesignBlock = {
  id: 'state00000',
  type: 'flowDesign',
  design: { kind: 'state', id: 'editing', screen: 'screen', name: '확인', changes: [] },
};
const placed: PlacedLayer = {
  address: target,
  node: layer,
  rect: { x: 148, y: 14, w: 40, h: 22 },
  parent: { width: 200, height: 200 },
};

it('keeps the opposite edge and leaves other axis untouched when resizing a trailing-anchored layer', () => {
  const next = writeLayerRect([screen], { screen: 'screen' }, placed, { x: 144, y: 14, w: 44, h: 22 });
  expect(findDesignNode(next, target)).toMatchObject({
    position: { x: { end: 12 }, y: { start: 14 } },
    size: { x: { mode: 'fixed', px: 44 }, y: { mode: 'fixed', px: 22 } },
  });
  const moved = writeLayerRect([screen], { screen: 'screen' }, placed, { ...placed.rect, x: 154, y: 17 });
  expect(findDesignNode(moved, target)?.position).toEqual({
    mode: 'absolute',
    x: { end: 6 },
    y: { start: 17 },
  });
});
it('snaps a resized edge to a sibling and draws the matching guide, or bypasses both with Alt', () => {
  const rect = { x: 20, y: 20, w: 60, h: 30 },
    other = { x: 99, y: 10, w: 50, h: 20 };
  expect(snapResize(rect, 'e', 18, 0, [other], { width: 300, height: 300 }, false)).toEqual({
    rect: { ...rect, w: 79 },
    guides: [{ axis: 'x', value: 99 }],
  });
  expect(snapResize(rect, 'e', 18, 0, [other], { width: 300, height: 300 }, true)).toEqual({
    rect: { ...rect, w: 78 },
    guides: [],
  });
});
it('changes only a state occurrence and resizes both-inset stretch by adjusting its trailing edge', () => {
  const stretched = { ...layer, position: { mode: 'absolute', x: { start: 8, end: 10 }, y: { start: 14 } } };
  const bs = writeLayerRect(
    [screen, state],
    { screen: 'screen', state: 'editing' },
    { ...placed, node: stretched },
    { ...placed.rect, w: 54 },
  );
  expect(bs[0]).toBe(screen);
  expect(bs[1]?.design.changes).toEqual([{ op: 'leaf', target, path: 'position.x.end', value: -4 }]);
});
it('preserves percent insets and rebases dragging after zoom', () => {
  const percent = {
    ...layer,
    position: { mode: 'absolute', x: { center: { percent: 0 } }, y: { start: 14 } },
  };
  const bs = writeLayerRect(
    [screen, state],
    { screen: 'screen', state: 'editing' },
    { ...placed, node: percent },
    { ...placed.rect, x: 168 },
  );
  expect(bs[1]?.design.changes).toEqual([
    { op: 'leaf', target, path: 'position.x.center', value: { percent: 10 } },
  ]);
});
it('duplicates as a named copy with fresh comment anchor and offset, and deletes only the chosen original', () => {
  let i = 0;
  const clip = copyCanvasLayers([screen, ann], 'screen', [target]);
  const copy = duplicateCanvasLayers(
    [screen, ann],
    { owner: 'screen', node: 'root' },
    clip,
    () => `new${++i}`,
    16,
    true,
  );
  const [duplicated] = copy.addresses;
  if (!duplicated) throw Error('missing copy');
  expect(findDesignNode(copy.blocks, duplicated)).toMatchObject({
    name: '이름 사본',
    position: { x: { end: -4 }, y: { start: 30 } },
  });
  const kept = removeCanvasLayers(copy.blocks, { screen: 'screen' }, [target]);
  expect(kept.map((b) => b.id)).not.toContain(ann.id);
  expect(kept.filter((b) => b.design.kind === 'annotation')).toHaveLength(1);
  expect(findDesignNode(kept, target)).toBeUndefined();
});
it('deleting from a state only hides that occurrence, preserving the base and the comment', () => {
  const before = [screen, ann, state];
  const next = removeCanvasLayers(before, { screen: 'screen', state: 'editing' }, [target]);
  expect(next[0]).toBe(screen);
  expect(next[1]).toBe(ann);
  expect(next[2]?.design.changes).toEqual([{ op: 'leaf', target, path: 'visible', value: false }]);
});

it('resizing a thin vector on one axis keeps the other dimension and content-box padding is not counted twice', () => {
  const r = { x: 10, y: 10, w: 60, h: 2 };
  expect(snapResize(r, 'e', 8, 0, [], { width: 360, height: 300 }, true).rect).toEqual({ ...r, w: 68 });
  const adjusted = writeLayerRect(
    [screen],
    { screen: 'screen' },
    { ...placed, contentInset: { x: 12, y: 0 } },
    { ...placed.rect, w: 80 },
  );
  expect(findDesignNode(adjusted, target)?.size).toMatchObject({ x: { mode: 'fixed', px: 68 } });
});
it('copies projected children inside their authored slot and leaves the shared component unchanged', () => {
  const inst = { kind: 'instance', id: 'card', component: 'card', slots: { content: [layer] } };
  const container = {
    ...screen,
    design: { ...screen.design, root: { kind: 'box', id: 'root', children: [inst] } },
  };
  const parent = childParent([container], 'screen', [target]);
  expect(parent).toEqual({ owner: 'screen', node: 'card', slot: 'content' });
  if (!parent) throw Error('missing slot');
  const clip = copyCanvasLayers([container], 'screen', [target]);
  let i = 0;
  const pasted = duplicateCanvasLayers([container], parent, clip, () => String(++i), 16, true);
  const kept = removeCanvasLayers(pasted.blocks, { screen: 'screen' }, [target]);
  const mounted = findDesignNode(kept, { owner: 'screen', node: 'card' });
  expect(mounted).toMatchObject({
    component: 'card',
    slots: { content: [{ kind: 'text', name: '이름 사본' }] },
  });
  expect(mounted).not.toHaveProperty('children');
});
it('duplicates a state-overridden button as an instance with new state-local IDs and a fresh annotation', () => {
  const component: FlowDesignBlock = {
    type: 'flowDesign',
    id: 'button0000',
    design: { kind: 'component', id: 'button', root: { kind: 'text', id: 'body', text: '기본' } },
  };
  const mount = { owner: 'screen', node: 'confirm' };
  const inside = {
    owner: 'button',
    node: 'body',
    via: [{ owner: 'screen', instance: 'confirm', component: 'button' }],
  };
  const src = {
    ...screen,
    design: {
      ...screen.design,
      root: { id: 'root', kind: 'box', children: [{ id: 'confirm', kind: 'instance', component: 'button' }] },
    },
  };
  const note = { ...ann, design: { ...ann.design, target: inside } };
  const scoped = { screen: 'screen', state: 'editing' };
  const data = editDesignNode([src, component, note, state], scoped, inside, {
    kind: 'leaf',
    path: 'text',
    value: '저장 중',
  });
  const clip = copyCanvasStateLayers(data, scoped, [mount]);
  let serial = 0;
  const out = duplicateCanvasLayers(
    data,
    { owner: 'screen', node: 'root' },
    clip,
    () => String(++serial),
    16,
    false,
    scoped,
  );
  expect(out.blocks[0]).toBe(src);
  expect(out.blocks[1]).toBe(component);
  expect(out.blocks[2]).toBe(note);
  const copies = resolveDesignScene(out.blocks, 'screen', 'editing').root?.children;
  expect(copies?.map((c) => c.node.text)).toEqual(['저장 중', '저장 중']);
  expect(resolveDesignScene(out.blocks, 'screen').root?.children.map((n) => n.node.text)).toEqual(['기본']);
  expect(out.blocks.at(-1)?.design).toMatchObject({
    kind: 'annotation',
    screen: 'screen',
    state: 'editing',
    target: {
      owner: 'button',
      node: 'body',
      via: [{ owner: 'editing', instance: '1', component: 'button' }],
    },
  });
  expect(out.blocks[3]?.design.changes).toMatchObject([
    {},
    { op: 'insert', node: { id: '1', kind: 'instance', component: 'button' } },
  ]);
  const removed = removeCanvasLayers(out.blocks, scoped, out.addresses);
  expect(removed).toEqual(data);
});
it('ungrouping an inherited grouping masks it in the selected state only', () => {
  const original = {
    ...screen,
    design: { ...screen.design, root: { id: 'root', kind: 'box', children: [{ ...layer, group: 'grp' }] } },
  };
  const next = clearCanvasGroup([original, state], { screen: 'screen', state: 'editing' }, target);
  expect(next[0]).toBe(original);
  expect(records(next[1]?.design.changes)[0]).toMatchObject({ op: 'mask', path: 'group' });
  expect(resolveDesignScene(next, 'screen', 'editing').root?.children[0]?.node).not.toHaveProperty('group');
});

it('cloned slot overrides inside nested instances receive their own persistent child IDs', () => {
  const inner = {
    id: 'nested',
    kind: 'instance',
    component: 'box',
    overrides: [
      {
        op: 'slot',
        target: { owner: 'box', node: 'placeholder' },
        prop: 'content',
        assignment: { children: [{ id: 'projected', kind: 'text', text: '슬롯' }] },
      },
    ],
  };
  const host = {
    ...screen,
    design: { ...screen.design, root: { id: 'root', kind: 'box', children: [inner] } },
  };
  const clip = copyCanvasLayers([host], 'screen', [{ owner: 'screen', node: 'nested' }]);
  let i = 0;
  const copy = duplicateCanvasLayers(
    [host],
    { owner: 'screen', node: 'root' },
    clip,
    () => String(++i),
    16,
    false,
  );
  const dup = copy.addresses[0];
  if (!dup) throw Error('missing copy');
  expect(findDesignNode(copy.blocks, dup)?.overrides).toMatchObject([
    { assignment: { children: [{ id: '2', text: '슬롯' }] } },
  ]);
  expect(findDesignNode(copy.blocks, { owner: 'screen', node: 'nested' })?.overrides).toMatchObject([
    { assignment: { children: [{ id: 'projected' }] } },
  ]);
});
