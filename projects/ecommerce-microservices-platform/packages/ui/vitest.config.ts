import { defineConfig } from 'vitest/config';

export default defineConfig({
  oxc: {
    jsx: 'automatic' as 'preserve',
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/__tests__/setup.ts'],
  },
});
