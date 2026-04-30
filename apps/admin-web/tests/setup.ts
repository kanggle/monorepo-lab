import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

// jsdom doesn't always implement crypto.randomUUID — polyfill for older envs.
const g = globalThis as unknown as { crypto?: { randomUUID?: () => string } };
if (!g.crypto) {
  (globalThis as unknown as { crypto: object }).crypto = {};
}
if (typeof g.crypto!.randomUUID !== 'function') {
  (g.crypto as { randomUUID: () => string }).randomUUID = () => '00000000-0000-4000-8000-000000000000';
}
