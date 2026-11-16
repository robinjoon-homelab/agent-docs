import { cpSync, readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import babel from '@rolldown/plugin-babel';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import type { Plugin } from 'vite';
import { defineConfig } from 'vitest/config';
import { EXCALIDRAW_ASSET_BASE, EXCALIDRAW_VERSION } from './src/config/excalidrawAssets.ts';

const backend = 'http://localhost:8080';
const proxiedPaths = ['/api', '/oauth2', '/login/oauth2', '/logout', '/oauth/token', '/mcp', '/.well-known'];

/**
 * Every font family in the Excalidraw package, by how it ships. A family ships only with a license confirmed from its
 * primary text (third-party/excalidraw/THIRD-PARTY-NOTICES.txt), so one the package adds later fails the build until
 * it is reviewed and listed here.
 */
const excalidrawFontFamilies = {
  copied: ['Cascadia', 'ComicShanns', 'Excalifont', 'Lilita', 'Nunito', 'Virgil', 'Xiaolai'],
  // Excalidraw's stylesheet refers to these, so Vite already emits them with the CSS bundle.
  stylesheet: ['Assistant'],
  // Liberation Sans 1.05 points only to "the license agreement under which you accepted" it; no such text was found.
  excluded: ['Liberation'],
};

/**
 * Ships Excalidraw's fonts and their licenses with the app so the drawing editor never fetches fonts from a CDN. A
 * package version or font family nobody reviewed, or a missing source, fails the build.
 */
function excalidrawFonts(): Plugin {
  const pkg = fileURLToPath(new URL('./node_modules/@excalidraw/excalidraw/', import.meta.url));
  const notices = fileURLToPath(new URL('./third-party/excalidraw/', import.meta.url));
  return {
    name: 'agent-docs-excalidraw-fonts',
    apply: 'build',
    buildStart() {
      const { version } = JSON.parse(readFileSync(`${pkg}package.json`, 'utf8')) as { version: string };
      if (version !== EXCALIDRAW_VERSION) {
        throw new Error(
          `@excalidraw/excalidraw ${version} is installed but its fonts were reviewed for ${EXCALIDRAW_VERSION}: review them and update src/config/excalidrawAssets.ts`,
        );
      }
      const known = Object.values(excalidrawFontFamilies).flat();
      const unknown = readdirSync(`${pkg}dist/prod/fonts`).filter((family) => !known.includes(family));
      if (unknown.length > 0) throw new Error(`Unreviewed Excalidraw font families: ${unknown.join(', ')}`);
    },
    writeBundle(options) {
      const out = `${options.dir ?? fileURLToPath(new URL('./dist', import.meta.url))}${EXCALIDRAW_ASSET_BASE}`;
      for (const family of excalidrawFontFamilies.copied) {
        cpSync(`${pkg}dist/prod/fonts/${family}`, `${out}fonts/${family}`, { recursive: true });
      }
      cpSync(notices, out, { recursive: true });
    },
  };
}

/**
 * The production page policy (font-src and connect-src kept on this origin) on the dev server too, widened only by
 * what development itself needs: the HMR socket of this dev host and, for the in-memory fake API, its blob: files.
 */
function devPagePolicy(): Plugin {
  const fake = process.env.VITE_FAKE_API === 'true';
  return {
    name: 'agent-docs-dev-page-policy',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const host = request.headers.host;
        const connect = [
          "'self'",
          ...(fake ? ['blob:'] : []),
          ...(host === undefined ? [] : [`ws://${host}`]),
        ];
        response.setHeader(
          'Content-Security-Policy',
          `font-src 'self' data:; connect-src ${connect.join(' ')}`,
        );
        next();
      });
    },
  };
}

export default defineConfig({
  plugins: [react(), babel({ presets: [reactCompilerPreset()] }), excalidrawFonts(), devPagePolicy()],
  server: {
    proxy: Object.fromEntries(proxiedPaths.map((path) => [path, { target: backend, changeOrigin: false }])),
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'node',
  },
});
