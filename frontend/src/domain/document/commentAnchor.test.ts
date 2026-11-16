import { expect, it } from 'vitest';
import { drawing, paragraph } from '../../test-support/fixtures';
import { commentAnchorPreparation } from './commentAnchor';
it('uses the stored body without saving for commenters and read-only trashed owner or editor views', () => {
  const base = [paragraph('a', 'stored body')];
  expect(commentAnchorPreparation({ writable: false, base, current: base, drawingDraft: false })).toBe(
    'ready',
  );
  expect(commentAnchorPreparation({ writable: true, base, current: base, drawingDraft: false })).toBe('save');
});
it('preserves unsaved body or drawing drafts and blocks unsafe anchors until they can be saved', () => {
  const base = [paragraph('a', 'stored')];
  const current = [paragraph('a', 'my unsaved text')];
  expect(commentAnchorPreparation({ writable: false, base, current, drawingDraft: false })).toBe('blocked');
  expect(commentAnchorPreparation({ writable: false, base, current: base, drawingDraft: true })).toBe(
    'blocked',
  );
  expect(
    commentAnchorPreparation({
      writable: false,
      base: [drawing('d', 'svg1', 'scene1')],
      current: [drawing('d', 'svg2', 'scene2')],
      drawingDraft: false,
    }),
  ).toBe('blocked');
  expect(current).toEqual([paragraph('a', 'my unsaved text')]);
});
