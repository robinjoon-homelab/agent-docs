import { describe, expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { resolveDesignScene } from './designScene';
import type { DesignOccurrence } from './designScene';
import { asText } from './designValue';
import { designAddressKey } from './designAddress';

const box = (id: string, children: readonly DesignObject[]): DesignObject => ({
  id,
  kind: 'box',
  layout: { mode: 'column' },
  children,
});
const text = (id: string, content: DesignObject | string): DesignObject => ({
  kind: 'text',
  id,
  text: content,
});
const block = (design: DesignObject): FlowDesignBlock => ({
  type: 'flowDesign',
  id: 'store-' + (asText(design.id) ?? ''),
  design,
});
const component: DesignObject = {
  kind: 'component',
  id: 'card',
  props: [
    { id: 'title', type: 'text', initial: '공통' },
    { id: 'slot', type: 'slot' },
    {
      id: 'rows',
      type: 'list',
      fields: [
        { id: 'name', type: 'text' },
        { id: 'flag', type: 'boolean' },
      ],
    },
  ],
  root: box('root', [
    text('title', { prop: 'title' }),
    {
      kind: 'repeat',
      id: 'each',
      items: { prop: 'rows' },
      template: [
        { ...text('row', { repeat: 'each', field: 'name' }), visible: { repeat: 'each', field: 'flag' } },
      ],
    },
    { kind: 'slot', id: 'outlet', prop: 'slot', fallback: [text('fallback', '기본 내용')] },
  ]),
};
const address = { owner: 'S', node: 'card1' };
const target = { owner: 'card', node: 'title', via: [{ owner: 'S', instance: 'card1', component: 'card' }] };
const screen: DesignObject = {
  kind: 'screen',
  id: 'S',
  root: box('base', [
    {
      kind: 'instance',
      id: 'card1',
      component: 'card',
      inputs: {
        rows: [
          { itemKey: 'a', fields: { name: '행 A' } },
          { itemKey: 'b', fields: { name: '행 B', flag: true } },
        ],
      },
      slots: { slot: [text('specific', '끼운 내용')] },
    },
    { kind: 'instance', id: 'card2', component: 'card' },
  ]),
};
const state: DesignObject = {
  kind: 'state',
  id: 'wide',
  screen: 'S',
  frame: { width: 1600 },
  changes: [
    { op: 'input', target: address, prop: 'title', value: '첫 인스턴스' },
    { op: 'set', target, fields: { style: { text: { weight: 700 } } } },
    { op: 'itemSet', target: address, prop: 'rows', itemKey: 'b', field: 'name', value: '바뀐 행 B' },
    { op: 'slot', target: address, prop: 'slot', assignment: { children: [] } },
  ],
};
const fixture = (extra: DesignObject[] = []) => [
  block({ kind: 'settings', id: 'settings', frame: 'desktop' }),
  block(component),
  block(screen),
  block(state),
  ...extra.map(block),
];
const texts = (n?: DesignOccurrence | null): string[] =>
  n ? (n.node.kind === 'text' ? [asText(n.node.text) ?? ''] : n.children.flatMap(texts)) : [];
describe('v4 scene expansion', () => {
  it('resolves one mounted instance and repeated item by stable address while preserving source, slots and viewport', () => {
    const data = fixture(),
      before = JSON.stringify(data),
      base = resolveDesignScene(data, 'S'),
      edited = resolveDesignScene(data, 'S', 'wide');
    expect(texts(base.root)).toEqual(['공통', '행 A', '행 B', '끼운 내용', '공통', '기본 내용']);
    expect(texts(edited.root)).toEqual(['첫 인스턴스', '행 A', '바뀐 행 B', '공통', '기본 내용']);
    expect(base.root?.children[0]?.children[1]?.node.visible).toBe(false);
    expect(edited.root?.children[0]?.children[2]?.address.via?.at(-1)).toEqual({
      owner: 'card',
      repeat: 'each',
      itemKey: 'b',
    });
    expect(edited.root?.children[0]?.children[0]?.node.style).toMatchObject({ text: { weight: 700 } });
    expect(edited.inputs.get(designAddressKey(address))?.title).toBe('첫 인스턴스');
    expect(edited.frame).toEqual({ width: 1600, height: 900 });
    expect(edited.warnings).toEqual([]);
    expect(JSON.stringify(data)).toBe(before);
  });
  it('inserts and moves actual siblings across a slot expansion, without moving the source node', () => {
    const changes: DesignObject[] = [
      {
        op: 'insert',
        at: { parent: { ...target, node: 'root' }, before: { owner: 'S', node: 'specific' } },
        node: text('added', '새 항목'),
      },
      {
        op: 'move',
        target: { owner: 'S', node: 'specific' },
        at: { parent: { ...target, node: 'root' }, before: { owner: 'moved', node: 'added' } },
      },
    ];
    const layer = resolveDesignScene(
      fixture([{ kind: 'state', id: 'moved', screen: 'S', changes }]),
      'S',
      'moved',
    );
    expect(texts(layer.root?.children[0])).toEqual(['공통', '행 A', '행 B', '끼운 내용', '새 항목']);
    expect(layer.warnings).toEqual([]);
  });
  it('uses the screen theme for token aliases and changes one stop through its permanent ID', () => {
    const ramp = {
      kind: 'linear',
      id: 'brand',
      stops: [
        { id: 'begin', at: 0, color: { token: 'base' } },
        { id: 'finish', at: 1, color: '#FFFFFF' },
      ],
    };
    const authored: DesignObject[] = [
      {
        kind: 'tokens',
        id: 'tokens',
        entries: [
          { id: 'base', type: 'color', value: '#111111', modes: [{ mode: 'night', value: '#222222' }] },
        ],
      },
      { kind: 'screen', id: 'S', themeMode: 'night', root: { ...box('root', []), style: { fills: [ramp] } } },
      {
        kind: 'state',
        id: 'active',
        screen: 'S',
        changes: [
          {
            op: 'detailSet',
            target: { owner: 'S', node: 'root' },
            parts: [
              { collection: 'fills', id: 'brand' },
              { collection: 'stops', id: 'finish' },
            ],
            field: 'color',
            value: '#AABBCC',
          },
        ],
      },
    ];
    const doc = authored.map(block);
    const result = resolveDesignScene(doc, 'S', 'active');
    expect(result.root?.node.style).toEqual({
      fills: [
        {
          ...ramp,
          stops: [
            { id: 'begin', at: 0, color: '#222222' },
            { id: 'finish', at: 1, color: '#AABBCC' },
          ],
        },
      ],
    });
  });
  it('preserves a missing or circular component as a visible unresolved layer', () => {
    const authored: DesignObject[] = [
      {
        kind: 'component',
        id: 'cycle',
        root: box('base', [{ kind: 'instance', id: 'again', component: 'cycle' }]),
      },
      {
        kind: 'screen',
        id: 'S',
        root: box('page', [
          { kind: 'instance', id: 'a', component: 'cycle' },
          { kind: 'instance', id: 'b', component: 'missing' },
        ]),
      },
    ];
    const doc = authored.map(block);
    const result = resolveDesignScene(doc, 'S');
    expect(result.root?.children[0]?.children[0]?.node.kind).toBe('unresolved');
    expect(result.warnings).toHaveLength(2);
  });
});

it('retains the item key when caller-authored slot content is repeated inside a component', () => {
  const panel: DesignObject = {
    kind: 'component',
    id: 'panel',
    props: [
      { id: 'rows', name: 'rows', type: 'list', fields: [] },
      { id: 'item', name: 'item', type: 'slot' },
    ],
    root: box('list', [
      {
        kind: 'repeat',
        id: 'each',
        items: { prop: 'rows' },
        template: [{ kind: 'slot', id: 'slot', prop: 'item', fallback: [] }],
      },
    ]),
  };
  const screen = block({
    kind: 'screen',
    id: 's',
    root: box('root', [
      {
        kind: 'instance',
        id: 'list',
        component: 'panel',
        inputs: {
          rows: [
            { itemKey: 'a', fields: {} },
            { itemKey: 'b', fields: {} },
          ],
        },
        slots: { item: [text('caption', '하나')] },
      },
    ]),
  });
  const target = { owner: 's', node: 'caption', via: [{ owner: 'panel', repeat: 'each', itemKey: 'b' }] };
  const state = block({
    kind: 'state',
    id: 'changed',
    screen: 's',
    changes: [{ op: 'set', target, fields: { text: '둘' } }],
  });
  const scene = resolveDesignScene([block(panel), screen, state], 's', 'changed');
  expect(texts(scene.root)).toEqual(['하나', '둘']);
  expect(scene.root?.children[0]?.children[1]?.address).toEqual(target);
});
