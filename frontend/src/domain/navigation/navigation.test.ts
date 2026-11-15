import { describe, expect, it } from 'vitest';
import { diaryPath } from './navigation';
import { backlinkTargets } from './backlinks';

describe('navigation', () => {
  it('uses only the explicitly configured dated path', () => {
    expect(diaryPath('일기/{YYYY-MM-DD}', '2026-11-16')).toBe('일기/2026-11-16');
    expect(() => diaryPath('일기', '2026-11-16')).toThrow();
  });
  it('extracts actual links and ignores code and escaped labels', () => {
    const targets = backlinkTargets([
      { id: 'real', type: 'paragraph', text: '[real](doc:one) ` [code](doc:two) ` \\[escape](doc:three)' },
      { id: 'code', type: 'code', language: 'md', code: '[fake](doc:four)' },
    ]);
    expect([...targets]).toEqual(['one']);
  });
});
