import { describe, it, expect } from 'vitest';
import {
  newFlow,
  readFlow,
  flowBlocks,
  normalizeFlow,
  newElement,
  numbered,
  nextScreens,
  warnings,
  snapMove,
  resizeElement,
  alignElements,
  flowTarget,
  flowDifferences,
  anchoredScreenId,
} from './flow';
describe('화면 흐름 규칙', () => {
  it('블록 순서를 보존하고 삭제해도 다음 번호를 낮추지 않는다', () => {
    const blocks = newFlow('settings', 'screen');
    const flow = readFlow(blocks);
    expect(flowBlocks(flow)).toEqual(blocks);
    const next = normalizeFlow(blocks, [{ ...flow.settings, nextScreen: 8 }]);
    expect(readFlow(next).settings.nextScreen).toBe(8);
    expect(() => readFlow([flow.settings])).toThrow();
  });
  it('번호는 설명이 붙은 요소의 위쪽, 왼쪽 순서다', () => {
    const a = newElement('a', 'button', 390, 844),
      b = { ...a, id: 'b', y: a.y - 20 },
      c = { ...a, id: 'c', annotated: false };
    expect(numbered([a, c, b]).map((e) => e.id)).toEqual(['b', 'a']);
  });
  it('다음 화면은 설명 붙은 요소의 위쪽·왼쪽 번호순과 각 이동 목록순이다', () => {
    const screen = readFlow(newFlow('settings', 'screen')).screens[0];
    if (!screen) throw new Error('화면 필요');
    const element = newElement('base', 'button', 390, 844);
    screen.elements = [
      {
        ...element,
        id: 'save',
        x: 100,
        y: 40,
        actions: [
          { when: '저장 성공', to: 'M-05', mode: 'same' },
          { when: '저장 실패', to: 'M-04', mode: 'same' },
        ],
      },
      {
        ...element,
        id: 'decoration',
        x: 0,
        y: 0,
        annotated: false,
        actions: [{ when: '장식', to: 'M-99', mode: 'same' }],
      },
      { ...element, id: 'mode', x: 0, y: 40, actions: [{ when: '일기 선택', to: 'M-03', mode: 'same' }] },
      {
        ...element,
        id: 'space',
        x: 100,
        y: 20,
        actions: [{ when: '스페이스 선택', to: 'M-02', mode: 'sheet' }],
      },
    ];
    expect(nextScreens(screen)).toEqual(['M-02', 'M-03', 'M-05', 'M-04']);
    expect(screen.elements.map((e) => e.id)).toEqual(['save', 'decoration', 'mode', 'space']);
  });
  it('같은 요소 안과 요소 사이의 중복 이동은 처음 나온 자리에만 남긴다', () => {
    const screen = readFlow(newFlow('settings', 'screen')).screens[0];
    if (!screen) throw new Error('화면 필요');
    const element = newElement('first', 'button', 390, 844);
    screen.elements = [
      {
        ...element,
        y: 0,
        actions: [
          { when: '첫 이동', to: 'M-09', mode: 'same' },
          { when: '이동 없음', to: null, mode: 'same' },
          { when: '둘째 이동', to: 'M-02', mode: 'page' },
          { when: '같은 대상', to: 'M-09', mode: 'popup' },
        ],
      },
      {
        ...element,
        id: 'second',
        y: 80,
        actions: [
          { when: '앞에서 나온 대상', to: 'M-02', mode: 'same' },
          { when: '새 대상', to: 'M-03', mode: 'same' },
        ],
      },
    ];
    expect(nextScreens(screen)).toEqual(['M-09', 'M-02', 'M-03']);
  });
  it('번호 없는 요소의 이동과 이동 없음은 다음 화면에 넣지 않는다', () => {
    const screen = readFlow(newFlow('settings', 'screen')).screens[0];
    if (!screen) throw new Error('화면 필요');
    screen.elements = [
      { ...newElement('plain', 'title', 390, 844), actions: [{ when: '모양만', to: 'M-02', mode: 'same' }] },
      { ...newElement('info', 'button', 390, 844), actions: [{ when: '설명', to: null, mode: 'same' }] },
    ];
    expect(nextScreens(screen)).toEqual([]);
  });
  it('경고는 저장을 막지 않고 설명, 이동, 틀 순으로 나온다', () => {
    const flow = readFlow(newFlow('settings', 'screen'));
    const screen = flow.screens[0];
    if (!screen) throw new Error('화면 필요');
    const e = {
      ...newElement('a', 'button', 390, 844),
      x: 380,
      actions: [{ when: '클릭', to: 'M-99', mode: 'same' as const }],
    };
    expect(warnings(flow, screen, e)).toEqual(['설명 없음', '없는 화면', '틀 밖']);
  });
  it('가까운 가장자리에 붙고 Alt는 스냅을 끈다', () => {
    const rect = { x: 20, y: 20, w: 100, h: 40 };
    expect(snapMove(rect, 77, 0, [{ x: 100, y: 200, w: 100, h: 20 }], 400).x).toBe(100);
    expect(snapMove(rect, 77, 0, [], 390, true).x).toBe(97);
  });
  it('정렬에서 잠긴 요소를 제외한다', () => {
    const a = newElement('a', 'button', 390, 844),
      b = { ...a, id: 'b', x: 80 },
      c = { ...a, id: 'c', x: 140, pinned: true };
    expect(alignElements([a, b, c], ['a', 'b', 'c'], 'left').map((e) => e.x)).toEqual([a.x, a.x, 140]);
  });
  it('위치와 설명 변경, 요소 삭제를 구분한다', () => {
    const flow = readFlow(newFlow('settings', 'screen'));
    const e = newElement('a', 'button', 390, 844);
    flow.screens[0]?.elements.push(e);
    const before = flowBlocks(flow);
    const moved = before.map((b) => (b.id === 'a' ? { ...e, x: 88 } : b));
    expect(flowTarget(before, moved, 'a')).toBe('changed');
    expect(
      flowTarget(
        before,
        before.map((b) => (b.id === 'a' ? { ...e, desc: '변경' } : b)),
        'a',
      ),
    ).toBe('changed');
    expect(
      flowTarget(
        before,
        before.filter((b) => b.id !== 'a'),
        'a',
      ),
    ).toBe('elementDeleted');
  });
  it('크기를 틀 끝까지 늘려도 반대쪽 변은 움직이지 않는다', () => {
    const e = { ...newElement('a', 'button', 390, 844), x: 40, y: 20, w: 100, h: 40 };
    expect(resizeElement(e, { dx: 500, dy: 0, handle: 'e', width: 390, alt: false })).toMatchObject({
      x: 40,
      w: 350,
    });
    expect(resizeElement(e, { dx: -100, dy: 0, handle: 'w', width: 390, alt: false })).toMatchObject({
      x: 0,
      w: 140,
    });
    expect(resizeElement(e, { dx: 500, dy: 0, handle: 'w', width: 390, alt: false })).toMatchObject({
      x: 124,
      w: 16,
    });
  });
  it.each(['겹침 순서', '문서 기본 틀'] as const)('%s만 바꾸면 화면 댓글은 바뀌지 않는다', (change) => {
    const flow = readFlow(newFlow('settings', 'screen'));
    const a = newElement('a', 'button', 390, 844),
      b = { ...a, id: 'b' };
    flow.screens[0]?.elements.push(a, b);
    const before = flowBlocks(flow);
    const after = flowBlocks({
      ...flow,
      settings: change === '문서 기본 틀' ? { ...flow.settings, frame: 'desktop' } : flow.settings,
      screens:
        change === '겹침 순서'
          ? flow.screens.map((s) => ({ ...s, elements: [...s.elements].reverse() }))
          : flow.screens,
    });
    expect(flowTarget(before, after, 'screen')).toBe('ok');
    expect(flowDifferences(before, after, 'screen')).toEqual([]);
  });
  it('저장된 틀 값과 custom 크기는 비교하고 잠금·묶음은 제외한다', () => {
    const flow = readFlow(newFlow('settings', 'screen'));
    const a = newElement('a', 'button', 390, 844);
    flow.screens[0]?.elements.push(a);
    const before = flowBlocks(flow);
    const locked = before.map((b) => (b.id === 'a' ? { ...a, pinned: true, group: 'g1' } : b));
    expect(flowTarget(before, locked, 'screen')).toBe('ok');
    const explicit = before.map((b) => (b.type === 'flowScreen' ? { ...b, frame: 'phone' as const } : b));
    expect(flowTarget(before, explicit, 'screen')).toBe('changed');
    const custom = before.map((b) =>
      b.type === 'flowScreen' ? { ...b, frame: 'custom' as const, frameW: 600, frameH: 720 } : b,
    );
    const resized = custom.map((b) => (b.type === 'flowScreen' ? { ...b, frameW: 604 } : b));
    expect(flowDifferences(custom, resized, 'screen')).toEqual([
      { field: '기기 틀', before: '직접 입력 600×720', after: '직접 입력 604×720' },
    ]);
  });
  it('요소가 다른 화면으로 옮겨져도 원래 화면의 댓글은 요소 삭제됨이다', () => {
    const flow = readFlow(newFlow('settings', 'screen'));
    const e = newElement('a', 'button', 390, 844);
    flow.screens[0]?.elements.push(e);
    const before = flowBlocks(flow);
    const after = [
      ...newFlow('settings', 'screen'),
      ...newFlow('settings2', 'screen2')
        .slice(1)
        .map((b) => (b.type === 'flowScreen' ? { ...b, screenId: 'M-02' } : b)),
      e,
    ];
    expect(flowTarget(before, after, 'a')).toBe('elementDeleted');
    expect(anchoredScreenId('a', readFlow(after).screens, 'screen')).toBe('screen');
    expect(anchoredScreenId('a', readFlow(after).screens)).toBe('screen2');
    expect(anchoredScreenId(null, readFlow(after).screens)).toBeNull();
    expect(flowDifferences(before, after, 'a')).toEqual([{ field: '요소', before: '버튼', after: '삭제됨' }]);
  });
});
