import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';

// The server runs this same canvas in an isolated Chromium page with document-scoped assets.
export default defineConfig({
  publicDir: false,
  define: { 'process.env.NODE_ENV': JSON.stringify('production') },
  build: {
    outDir: 'dist/design-render',
    lib: {
      entry: fileURLToPath(new URL('./src/adapters/inbound/render/designRender.tsx', import.meta.url)),
      formats: ['iife'],
      name: 'AgentDocsDesignRender',
      fileName: () => 'renderer.js',
      cssFileName: 'renderer',
    },
  },
});
