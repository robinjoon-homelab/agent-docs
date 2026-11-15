import { describe, expect, it } from 'vitest';
import { toStoredBlock } from './mappers';

describe('toStoredBlock', () => {
  it('keeps a display formula so it saves back as $$ instead of a fenced math block', () => {
    const block = toStoredBlock({
      id: 'math000001',
      type: 'code',
      language: 'math',
      code: 'E = mc^2',
      displayMath: true,
      hash: 'abc',
    });

    expect(block).toEqual({
      id: 'math000001',
      type: 'code',
      language: 'math',
      code: 'E = mc^2',
      displayMath: true,
      hash: 'abc',
    });
  });

  it('leaves fenced math and ordinary code without the flag', () => {
    const fenced = toStoredBlock({
      id: 'math000002',
      type: 'code',
      language: 'math',
      code: 'x',
      displayMath: false,
    });
    const legacy = toStoredBlock({ id: 'code000003', type: 'code', language: 'kotlin', code: 'val x = 1' });

    expect('displayMath' in fenced).toBe(false);
    expect('displayMath' in legacy).toBe(false);
  });
});
