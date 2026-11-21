import { describe, expect, it } from 'vitest';
import { newElement, newFlow } from '../../../../domain/flow/flow';
import { toBlock, toStoredBlock } from './mappers';
describe('화면 흐름 JSON', () => {
  it('설정·화면·요소와 배열을 손실 없이 변환한다', () => {
    const blocks = [
      ...newFlow('settings', 'screen'),
      {
        ...newElement('element', 'list', 390, 844),
        items: ['하나', '둘'],
        states: ['기본', '저장 중'],
        actions: [{ when: '선택', to: 'M-01', mode: 'same' as const }],
      },
    ];
    expect(blocks.map(toBlock)).toEqual(blocks);
    expect(toStoredBlock({ ...blocks[2], hash: '12345678' })).toEqual({ ...blocks[2], hash: '12345678' });
  });
  it('알 수 없는 종류나 이동 방식은 거절한다', () => {
    const element = newElement('element', 'button', 390, 844);
    expect(() => toBlock({ ...element, kind: 'unknown' })).toThrow();
    expect(() => toBlock({ ...element, actions: [{ when: '누르면', to: null, mode: 'unknown' }] })).toThrow();
  });
});
