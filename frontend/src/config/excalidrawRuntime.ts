import { EXCALIDRAW_ASSET_BASE } from './excalidrawAssets';

type ExcalidrawWindow = Window & { EXCALIDRAW_ASSET_PATH?: string; EXCALIDRAW_EXPORT_SOURCE?: string };

/**
 * Settings @excalidraw/excalidraw reads from window. Its fonts come from this app (the build copies them under
 * EXCALIDRAW_ASSET_BASE), never from its CDN default, and saved scenes name the app rather than this instance's
 * address. Call before the drawing editor is loaded.
 */
export function configureExcalidrawRuntime(win: Window, dev: boolean): void {
  const target = win as ExcalidrawWindow;
  target.EXCALIDRAW_ASSET_PATH = dev
    ? '/node_modules/@excalidraw/excalidraw/dist/prod/'
    : EXCALIDRAW_ASSET_BASE;
  target.EXCALIDRAW_EXPORT_SOURCE = 'agent-docs';
}
