import { describe, expect, it } from 'vitest';
import { toStoredBlock } from './mappers';

describe('toStoredBlock', () => {
  it.each([1, 2, 3, 4, 5, 6])('reads a level %i heading from the API', (level) => {
    const heading = { id: 'head000001', type: 'heading', level, text: '세부 항목', hash: 'abc' };
    expect(toStoredBlock(heading)).toEqual(heading);
  });

  it.each([0, 2.5, 7])('rejects heading level %s', (level) => {
    expect(() => toStoredBlock({ id: 'head000001', type: 'heading', level, text: '세부 항목' })).toThrow();
  });

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

  it('reads a drawing with both of its assets and the alt text', () => {
    const block = toStoredBlock({
      id: 'draw000001',
      type: 'drawing',
      svgAssetId: '11111111-1111-4111-8111-111111111111',
      sceneAssetId: '22222222-2222-4222-8222-222222222222',
      alt: '배포 흐름',
      hash: 'h1',
    });

    expect(block).toEqual({
      id: 'draw000001',
      type: 'drawing',
      svgAssetId: '11111111-1111-4111-8111-111111111111',
      sceneAssetId: '22222222-2222-4222-8222-222222222222',
      alt: '배포 흐름',
      hash: 'h1',
    });
  });

  it('rejects a drawing without its scene instead of keeping only the picture', () => {
    expect(() =>
      toStoredBlock({
        id: 'draw000002',
        type: 'drawing',
        svgAssetId: '11111111-1111-4111-8111-111111111111',
      }),
    ).toThrow();
  });
});

it('reads only known space permissions and never upgrades an unknown grant', async () => {
  const { toPermission } = await import('./mappers');
  expect(['read', 'comment', 'edit', 'owner', 'future'].map(toPermission)).toEqual([
    'read',
    'comment',
    'edit',
    'owner',
    'read',
  ]);
  expect(toPermission(undefined)).toBeUndefined();
});
