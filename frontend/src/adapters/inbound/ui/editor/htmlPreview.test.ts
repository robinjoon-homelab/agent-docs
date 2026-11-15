import { describe, expect, it } from 'vitest';
import { htmlPreviewSource } from './htmlPreview';
import { pastedTextNodes } from './pasteText';

describe('isolated HTML and math input', () => {
  it('places the restrictive policy before original scripts and keeps source intact', () => {
    const original = '<script>fetch("/api/documents")</script><button>눌러 보기</button>';
    const preview = htmlPreviewSource(original);
    expect(preview.indexOf('Content-Security-Policy')).toBeLessThan(preview.indexOf(original));
    expect(preview).toContain("connect-src 'none'");
    expect(preview).toContain("frame-src 'none'");
    expect(preview).toContain("base-uri 'none'");
    expect(preview).toContain("form-action 'none'");
    expect(preview).toContain(original);
  });
  it('pastes display math as a source-preserving math code node', () => {
    expect(pastedTextNodes('$$\na^2 + b^2\n$$')).toEqual([
      {
        type: 'codeBlock',
        attrs: { language: 'math', displayMath: true },
        content: [{ type: 'text', text: 'a^2 + b^2' }],
      },
    ]);
  });
});
