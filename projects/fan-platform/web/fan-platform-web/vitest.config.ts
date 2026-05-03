import { defineConfig } from 'vitest/config';
import path from 'path';
import react from '@vitejs/plugin-react';

/**
 * Vitest configuration — JS-DOM environment for component tests, Node default
 * for pure logic tests. Playwright specs live under `e2e-smoke/` and `e2e/`
 * and are excluded here so the two runners do not collide.
 *
 * @vitejs/plugin-react handles the React 19 automatic JSX runtime so test
 * files do not need an explicit `import React`.
 */
export default defineConfig({
  plugins: [react({ jsxRuntime: 'automatic' })],
  esbuild: {
    jsx: 'automatic',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    exclude: ['node_modules/**', 'e2e/**', 'e2e-smoke/**', '.next/**'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/__tests__/**', 'src/**/*.d.ts'],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
});
