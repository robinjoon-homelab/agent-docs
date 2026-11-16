import { cpSync, readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import babel from '@rolldown/plugin-babel';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import type { Plugin, Rolldown } from 'vite';
import { defineConfig } from 'vitest/config';
import {
  EXCALIDRAW_ASSET_BASE,
  EXCALIDRAW_CDN_FONT_SOURCES,
  EXCALIDRAW_VERSION,
  withoutCdnFontSource,
} from './src/config/excalidrawAssets.ts';

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

const excalidrawPackage = '/@excalidraw/excalidraw/';

/**
 * Drops Excalidraw's CDN font source (EXCALIDRAW_CDN_FONT_SOURCES) from the code the browser gets. The production
 * build runs this as a Vite plugin and the dev server's dependency pre-bundle as a Rolldown plugin, so both list only
 * this app's fonts. A bundle that took one of the package's builds without changing its reviewed file fails.
 */
function excalidrawLocalFontsOnly(): Rolldown.Plugin {
  const bundled = new Set<string>();
  const changed = new Set<string>();
  return {
    name: 'agent-docs-excalidraw-local-fonts-only',
    transform: {
      filter: { id: /[\\/]@excalidraw[\\/]excalidraw[\\/]dist[\\/](prod|dev)[\\/].*\.js$/ },
      handler(code, id) {
        const path = id.replaceAll('\\', '/');
        const statement = EXCALIDRAW_CDN_FONT_SOURCES.find(({ build }) =>
          path.includes(`${excalidrawPackage}${build}`),
        );
        if (statement === undefined) return null;
        bundled.add(statement.build);
        if (!path.endsWith(`${excalidrawPackage}${statement.build}${statement.file}`)) return null;
        changed.add(statement.build);
        return { code: withoutCdnFontSource(code, statement), map: null };
      },
    },
    buildEnd(error) {
      const missed = [...bundled].filter((build) => !changed.has(build));
      if (error === undefined && missed.length > 0) {
        throw new Error(
          `@excalidraw/excalidraw ${missed.join(', ')} was bundled without removing its CDN font source`,
        );
      }
    },
  };
}

/**
 * The two small modules of Excalidraw 0.18.1's production build that its font subsetting worker shares with the
 * editor: the package's import.meta.env values and esbuild's module helpers. Neither imports anything.
 */
const excalidrawWorkerSharedModules =
  /[\\/]@excalidraw[\\/]excalidraw[\\/]dist[\\/]prod[\\/]chunk-(ZUYEQ4TG|SRAX5OIU)\.js$/;

/**
 * Rolldown's helper module, the id rolldown exports as RUNTIME_MODULE_ID (rolldown is not a dependency of this app, so
 * it is not imported from there). Rolldown adds it on its own, with no import in the source, where code needs one of
 * its helpers, such as the free require in esbuild's helpers above. In rolldown 1.2.14 it only defines those helpers
 * and touches no document, window or network, so the worker may load it.
 */
const rolldownRuntime = '\0rolldown/runtime.js';

/**
 * Keeps what Excalidraw's font subsetting worker loads to what it imports, plus Rolldown's helper module. Excalidraw
 * starts that worker from its own subset-worker chunk, so every chunk that chunk imports runs in the worker too.
 * Rolldown puts the modules the worker shares with the editor into the editor's chunk, which imports the app, and the
 * app's code needs document: the worker then fails before it runs and Excalidraw subsets fonts on the main thread
 * instead. The build's code-splitting group gives those modules a chunk of their own; this check fails the build when
 * a chunk the worker loads holds any other module.
 */
function excalidrawWorkerOwnChunks(): Plugin {
  const worker = `${excalidrawPackage}dist/prod/subset-worker.chunk.js`;
  return {
    name: 'agent-docs-excalidraw-worker-chunks',
    apply: 'build',
    generateBundle(_, bundle) {
      const chunks = new Map(
        Object.values(bundle).flatMap((output) =>
          output.type === 'chunk' ? [[output.fileName, output] as const] : [],
        ),
      );
      const entry = [...chunks.values()].find((chunk) =>
        chunk.facadeModuleId?.replaceAll('\\', '/').endsWith(worker),
      );
      if (entry?.facadeModuleId == null) throw new Error(`${worker} was not built as a chunk of its own`);
      const allowed = reachable([entry.facadeModuleId], (id) => this.getModuleInfo(id)?.importedIds ?? []);
      allowed.add(rolldownRuntime);
      const loaded = reachable([entry.fileName], (fileName) => chunks.get(fileName)?.imports ?? []);
      const strays = [...loaded].flatMap((fileName) =>
        (chunks.get(fileName)?.moduleIds ?? []).filter((id) => !allowed.has(id)),
      );
      if (strays.length > 0) {
        throw new Error(
          `Excalidraw's font subsetting worker would load ${strays.length} modules it does not import, such as ${JSON.stringify(strays.slice(0, 3))}`,
        );
      }
    },
  };
}

/** Everything reachable from start by following next, start included. */
function reachable(start: string[], next: (item: string) => readonly string[]): Set<string> {
  const seen = new Set(start);
  for (const item of seen) {
    for (const following of next(item)) seen.add(following);
  }
  return seen;
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
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    excalidrawFonts(),
    { ...excalidrawLocalFontsOnly(), apply: 'build' },
    excalidrawWorkerOwnChunks(),
    devPagePolicy(),
  ],
  server: {
    proxy: Object.fromEntries(proxiedPaths.map((path) => [path, { target: backend, changeOrigin: false }])),
  },
  optimizeDeps: {
    rolldownOptions: { plugins: [excalidrawLocalFontsOnly()] },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [{ name: 'excalidraw-worker-shared', test: excalidrawWorkerSharedModules }],
        },
      },
    },
  },
  test: {
    include: ['src/**/*.test.{ts,tsx}'],
    environment: 'node',
  },
});
