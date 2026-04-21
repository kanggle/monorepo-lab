import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  oxc: {
    jsx: 'automatic' as 'preserve',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/__tests__/**', 'src/**/*.d.ts'],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      '@repo/types/guards': path.resolve(__dirname, '../../packages/types/src/guards'),
      '@repo/types': path.resolve(__dirname, '../../packages/types/src'),
      '@repo/api-client': path.resolve(__dirname, '../../packages/api-client/src'),
      '@repo/utils/pagination': path.resolve(__dirname, '../../packages/utils/src/pagination.ts'),
      '@repo/utils': path.resolve(__dirname, '../../packages/utils/src/index.ts'),
    },
  },
});
