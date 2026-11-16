import { describe, expect, it } from 'vitest';
import { DrawingEditState } from './drawingInput';

describe('DrawingEditState', () => {
  it('takes the opened scene as the baseline and counts each new content change once', () => {
    const state = new DrawingEditState();
    expect(state.observe('v1|#fff')).toBe(false);
    expect(state.observe('v1|#fff')).toBe(false);
    expect(state.isDirty()).toBe(false);
    expect(state.observe('v2|#fff')).toBe(true);
    expect(state.observe('v2|#fff')).toBe(false);
    expect(state.isDirty()).toBe(true);
  });

  it('is clean after saving that content and dirty again after a later edit', () => {
    const state = new DrawingEditState();
    state.observe('v1');
    state.observe('v2');
    state.saved('v2');
    expect(state.isDirty()).toBe(false);
    state.observe('v3');
    expect(state.isDirty()).toBe(true);
  });

  it('is clean when an edit is undone back to what was opened', () => {
    const state = new DrawingEditState();
    state.observe('v1');
    expect(state.observe('v2')).toBe(true);
    expect(state.observe('v1')).toBe(true);
    expect(state.isDirty()).toBe(false);
  });
});
