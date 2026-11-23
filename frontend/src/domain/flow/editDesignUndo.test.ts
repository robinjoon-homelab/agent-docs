import { describe, it, expect } from 'vitest';
import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import { restoreDesignEdit } from './editDesignUndo';
const screen = (children: DesignObject[]): FlowDesignBlock => ({
  id: 'block',
  type: 'flowDesign',
  design: { kind: 'screen', id: 'memo', root: { id: 'root', kind: 'box', children } },
});
describe('v4 편집 되돌리기', () => {
  it('같은 원본 블록에 들어온 다른 편집의 노드 순서·문구·추가 요소를 보존한다', () => {
    const a = { id: 'a', kind: 'text', text: '저장' },
      b = { id: 'b', kind: 'text', text: '취소' };
    const changed = { ...a, text: '메모 저장' },
      other = { ...b, text: '닫기' },
      added = { id: 'c', kind: 'text', text: '나가기' };
    expect(
      restoreDesignEdit([screen([changed, b])], [screen([a, b])], [screen([other, changed, added])]),
    ).toEqual([screen([other, a, added])]);
  });
  it('다른 편집자가 같은 문구를 고쳤다면 현재 값을 그대로 남긴다', () => {
    const base = { id: 'a', text: '메모 저장' },
      original = { id: 'a', text: '저장' },
      current = screen([{ id: 'a', text: '초안 저장' }]);
    expect(() => restoreDesignEdit([screen([base])], [screen([original])], [current])).toThrow('같은 속성');
    expect(current).toEqual(screen([{ id: 'a', text: '초안 저장' }]));
  });
});
