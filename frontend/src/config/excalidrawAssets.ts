/** The @excalidraw/excalidraw version these assets were reviewed for. The build fails when the installed one differs. */
export const EXCALIDRAW_VERSION = '0.18.1';

/**
 * Where the build puts Excalidraw's fonts and their licenses. Browsers keep /assets/** for a year as immutable, so
 * each version gets its own folder and a later one never replaces files a browser already holds under the same URL.
 */
export const EXCALIDRAW_ASSET_BASE = `/assets/excalidraw/${EXCALIDRAW_VERSION}/`;
