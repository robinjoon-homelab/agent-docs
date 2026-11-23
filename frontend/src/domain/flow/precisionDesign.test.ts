import { describe, expect, it } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { editDesignPrecision } from './editDesign';
import { resolveDesignScene } from './designScene';
import {
  applyPrecisionEdit,
  availablePaintStacks,
  insertPaintPart,
  precisionSelection,
  precisionSet,
} from './precisionDesign';

const vertex = (id: string, x: number): DesignObject => ({ id, x, y: 4 });
const outline = (id: string, from: number): DesignObject => ({
  id,
  closed: false,
  vertices: [vertex('start', from), vertex('end', from + 8)],
});
const curve: DesignObject = {
  kind: 'vector',
  id: 'curve',
  fillRule: 'nonzero',
  contours: [outline('inner', 0), outline('outer', 3)],
};
const point = [
  { collection: 'contours', id: 'outer' },
  { collection: 'vertices', id: 'start' },
];

describe('세부 속성 편집', () => {
  it('같은 이름의 꼭짓점을 가진 두 윤곽 중 선택한 윤곽만 수정한다', () => {
    const changed = applyPrecisionEdit(curve, precisionSet(point, 'x', 7));
    expect(changed).toEqual({
      ...curve,
      contours: [
        outline('inner', 0),
        { id: 'outer', closed: false, vertices: [vertex('start', 7), vertex('end', 11)] },
      ],
    });
    expect(precisionSelection(curve, point)?.member.x).toBe(3);
    expect(() =>
      applyPrecisionEdit(
        changed,
        precisionSet(
          [
            { collection: 'contours', id: 'gone' },
            { collection: 'vertices', id: 'start' },
          ],
          'x',
          5,
        ),
      ),
    ).toThrow();
  });
  it('기존 획이 가진 그라데이션의 정지점을 ID로 고치고 다른 획·정지점은 보존한다', () => {
    const fill: DesignObject = {
      id: 'ink',
      kind: 'linear',
      stops: [
        { id: 'a', at: 0, color: '#112233' },
        { id: 'b', at: 1, color: '#ffffff' },
      ],
    };
    const panel: DesignObject = {
      id: 'panel',
      kind: 'box',
      style: {
        strokes: [{ id: 'rim', width: 3, paint: fill }],
        fills: [{ id: 'back', kind: 'solid', color: '#fff' }],
      },
    };
    const steps = [
      { collection: 'strokes', id: 'rim' },
      { collection: 'paint', id: 'ink' },
      { collection: 'stops', id: 'b' },
    ];
    const changed = applyPrecisionEdit(panel, precisionSet(steps, 'color', { token: 'accent' }));
    expect(precisionSelection(changed, steps)?.member).toEqual({
      id: 'b',
      at: 1,
      color: { token: 'accent' },
    });
    expect(precisionSelection(panel, steps)?.member.color).toBe('#ffffff');
    expect(precisionSelection(changed, [{ collection: 'fills', id: 'back' }])?.member.color).toBe('#fff');
  });
  it('여러 채움 레이어를 추가·이동·제거할 때 원래 ID를 보존한다', () => {
    const base: DesignObject = { kind: 'box', id: 'frame', style: { opacity: 0.7 } };
    const first = applyPrecisionEdit(base, insertPaintPart('fills', 'default', 'earth'));
    const second = applyPrecisionEdit(first, insertPaintPart('fills', 'linear', 'sea'));
    const path = [{ collection: 'fills', id: 'sea' }];
    const moved = applyPrecisionEdit(second, { op: 'detailMove', parts: path, at: { before: 'earth' } });
    expect(precisionSelection(moved, [])?.groups[0]?.members.map((m) => m.id)).toEqual(['sea', 'earth']);
    expect(precisionSelection(moved, [...path, { collection: 'stops', id: 'end' }])?.member.at).toBe(1);
    expect(applyPrecisionEdit(moved, { op: 'detailRemove', parts: path })).toEqual(first);
    expect(() =>
      applyPrecisionEdit(moved, { op: 'detailMove', parts: path, at: { before: 'nonexistent' } }),
    ).toThrow();
    expect(availablePaintStacks({ style: { fill: '#fff', border: {} } })).toEqual(['effects']);
  });
  it('공유 네트워크 꼭짓점의 이동은 두 면에서 참조하는 변·고리를 유지한다', () => {
    const segments = [
      { id: 'ab', from: 'a', to: 'b' },
      { id: 'bc', from: 'b', to: 'c' },
    ];
    const regions = [
      { id: 'top', loops: [[{ segment: 'ab' }]] },
      { id: 'bottom', loops: [[{ segment: 'ab', reverse: true }]] },
    ];
    const network: DesignObject = {
      kind: 'vector',
      id: 'badge',
      network: { vertices: [vertex('a', 2), vertex('b', 8), vertex('c', 12)], segments, regions },
    };
    expect(applyPrecisionEdit(network, precisionSet([{ collection: 'vertices', id: 'a' }], 'x', 6))).toEqual({
      ...network,
      network: { vertices: [vertex('a', 6), vertex('b', 8), vertex('c', 12)], segments, regions },
    });
  });
  it('인스턴스, 상태, 원본 편집의 쓰기 범위가 각각 다르다', () => {
    const asBlock = (id: string, design: DesignObject): FlowDesignBlock => ({
      type: 'flowDesign',
      id,
      design,
    });
    const blocks: FlowDesignBlock[] = [
      asBlock('settings', { kind: 'settings', id: 'settings', frame: 'phone' }),
      asBlock('master', {
        kind: 'component',
        id: 'mark',
        props: [],
        root: { id: 'template', kind: 'box', layout: { mode: 'free' }, children: [curve] },
      }),
      asBlock('screen', {
        kind: 'screen',
        id: 'map',
        root: {
          id: 'pair',
          kind: 'box',
          layout: { mode: 'row' },
          children: [
            { id: 'left', kind: 'instance', component: 'mark' },
            { id: 'right', kind: 'instance', component: 'mark' },
          ],
        },
      }),
      asBlock('state', { kind: 'state', id: 'active', screen: 'map', changes: [] }),
    ];
    const a = { owner: 'mark', node: 'curve', via: [{ owner: 'map', instance: 'left', component: 'mark' }] };
    const instanceEdit = editDesignPrecision(blocks, { screen: 'map' }, a, precisionSet(point, 'x', 11));
    const stateEdit = editDesignPrecision(
      instanceEdit,
      { screen: 'map', state: 'active' },
      a,
      precisionSet(point, 'x', 15),
    );
    const masterEdit = editDesignPrecision(
      stateEdit,
      { screen: 'map', original: true },
      a,
      precisionSet(point, 'x', 5),
    );
    const points = (b: readonly FlowDesignBlock[], state?: string) =>
      resolveDesignScene(b, 'map', state).root?.children.map((c) => {
        const node = c.children[0]?.node;
        return node && precisionSelection(node, point)?.member.x;
      });
    expect(points(blocks)).toEqual([3, 3]);
    expect(points(instanceEdit)).toEqual([11, 3]);
    expect(points(stateEdit)).toEqual([11, 3]);
    expect(points(stateEdit, 'active')).toEqual([15, 3]);
    expect(points(masterEdit)).toEqual([11, 5]);
    expect(points(masterEdit, 'active')).toEqual([15, 5]);
  });
});
