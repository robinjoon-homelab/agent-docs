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

  it('treats canonical app document URLs and doc: syntax as the same document ID and ignores unrelated origins', () => {
    const targets = backlinkTargets(
      [
        {
          id: 'urls',
          type: 'paragraph',
          text: [
            '[doc](doc:doc-1)',
            '[canon](https://docs.example.com/s/team/d/doc-2#thread-1)',
            '[short](https://docs.example.com:443/d/doc-3?via=x)',
            '[rel-space](/s/old/d/doc-4)',
            '[rel-short](/d/doc-5#block-p1)',
            '[foreign](https://evil.example.com/s/team/d/doc-9)',
            '[proto-rel](//docs.example.com/d/doc-9)',
            '[history](https://docs.example.com/s/team/d/doc-9/history)',
          ].join(' '),
        },
      ],
      'https://docs.example.com',
    );
    expect([...targets]).toEqual(['doc-1', 'doc-2', 'doc-3', 'doc-4', 'doc-5']);
  });
});
