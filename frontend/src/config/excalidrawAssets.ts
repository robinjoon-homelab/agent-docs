/** The @excalidraw/excalidraw version these assets were reviewed for. The build fails when the installed one differs. */
export const EXCALIDRAW_VERSION = '0.18.1';

/**
 * Where the build puts Excalidraw's fonts and their licenses. Browsers keep /assets/** for a year as immutable, so
 * each version gets its own folder and a later one never replaces files a browser already holds under the same URL.
 */
export const EXCALIDRAW_ASSET_BASE = `/assets/excalidraw/${EXCALIDRAW_VERSION}/`;

/** A reviewed statement of one of the package's builds and what it becomes. */
export interface PackageStatement {
  /** The package's build folder: dist/prod/ for the production build, dist/dev/ for the dev server's pre-bundle. */
  build: string;
  file: string;
  code: string;
  replacement: string;
}

/**
 * The statement in each build of @excalidraw/excalidraw 0.18.1 (ExcalidrawFontFace.createUrls) that adds the
 * package's CDN copy on esm.sh after the asset path this app gives it, whatever that setting is. The browser moves on
 * to that copy whenever it cannot use the local font, so both builds drop the statement: every font face then lists
 * only this app's copy, and a font that fails stays missing instead of being fetched from elsewhere.
 */
export const EXCALIDRAW_CDN_FONT_SOURCES: readonly PackageStatement[] = [
  {
    build: 'dist/prod/',
    file: 'chunk-K2UTITRG.js',
    code: 'return r.push(new URL(n,jn.ASSETS_FALLBACK_URL)),r}',
    replacement: 'return r}',
  },
  {
    build: 'dist/dev/',
    file: 'chunk-4FTI6OG3.js',
    code: 'urls.push(new URL(assetUrl, _ExcalidrawFontFace.ASSETS_FALLBACK_URL));',
    replacement: '',
  },
];

/** The package file without its CDN font source; throws unless the reviewed statement appears exactly once. */
export function withoutCdnFontSource(
  source: string,
  { build, file, code, replacement }: PackageStatement,
): string {
  const found = source.split(code).length - 1;
  if (found !== 1) {
    throw new Error(
      `The CDN font source of @excalidraw/excalidraw ${build}${file} appears ${found} times instead of once: review the package before building`,
    );
  }
  return source.replace(code, () => replacement);
}
