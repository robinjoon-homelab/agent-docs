import { describe, expect, it } from 'vitest';
import devFontFace from '../../node_modules/@excalidraw/excalidraw/dist/dev/chunk-4FTI6OG3.js?raw';
import prodFontFace from '../../node_modules/@excalidraw/excalidraw/dist/prod/chunk-K2UTITRG.js?raw';
import { EXCALIDRAW_CDN_FONT_SOURCES, withoutCdnFontSource } from './excalidrawAssets';

const installed: Record<string, string> = {
  'dist/prod/chunk-K2UTITRG.js': prodFontFace,
  'dist/dev/chunk-4FTI6OG3.js': devFontFace,
};

describe.each(EXCALIDRAW_CDN_FONT_SOURCES)('the CDN font source in $file', (statement) => {
  const source = installed[`${statement.build}${statement.file}`] ?? '';

  it('is dropped where the installed package has it, leaving the rest of the file and no reader of the CDN', () => {
    const at = source.indexOf(statement.code);

    const changed = withoutCdnFontSource(source, statement);

    expect(changed).toBe(
      source.slice(0, at) + statement.replacement + source.slice(at + statement.code.length),
    );
    expect(changed).not.toMatch(/\.ASSETS_FALLBACK_URL\b/);
  });

  it('refuses a file that no longer has the statement or has it twice', () => {
    const changed = withoutCdnFontSource(source, statement);

    expect(() => withoutCdnFontSource(changed, statement)).toThrow(/appears 0 times/);
    expect(() => withoutCdnFontSource(source + statement.code, statement)).toThrow(/appears 2 times/);
  });
});
