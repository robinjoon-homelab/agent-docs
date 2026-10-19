import babel from '@rolldown/plugin-babel';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

const backend = 'http://localhost:8080';
const proxiedPaths = ['/api', '/oauth2', '/login/oauth2', '/logout', '/oauth/token', '/mcp', '/.well-known'];

export default defineConfig({
  plugins: [react(), babel({ presets: [reactCompilerPreset()] })],
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
