/**
 * Unit tests for web-store apiClient baseURL determination.
 *
 * Added by TASK-FE-068 to lock web-store apiClient baseURL logic against
 * regression.
 *
 * Key constraint (Edge Case #5 in TASK-FE-068):
 *   `baseURL` is a module-level const captured at import time.
 *   To test different env combinations we must:
 *     1. Set process.env before dynamic import
 *     2. Call vi.resetModules() between cases so each import is fresh
 *     3. Use dynamic import() — not static — so the module re-evaluates
 */

import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest';

const ORIGINAL_ENV = { ...process.env };

beforeEach(() => {
  vi.resetModules();
  delete process.env.API_URL_INTERNAL;
  delete process.env.NEXT_PUBLIC_API_URL;
});

afterEach(() => {
  process.env = { ...ORIGINAL_ENV };
});

async function loadApiClientWithEnv(
  env: Record<string, string | undefined>,
  windowDefined: boolean,
): Promise<string> {
  for (const [k, v] of Object.entries(env)) {
    if (v === undefined) {
      delete process.env[k];
    } else {
      process.env[k] = v;
    }
  }

  const originalWindow = globalThis.window;
  if (!windowDefined) {
    // @ts-expect-error -- intentionally deleting window to simulate SSR
    delete globalThis.window;
  } else {
    // @ts-expect-error -- assign a minimal stub
    globalThis.window = {};
  }

  let capturedBaseURL = '';

  // ApiClient is used as `new ApiClient(...)` so the mock must be a
  // constructable function (not an arrow function).
  vi.doMock('@repo/api-client', () => ({
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    ApiClient: vi.fn(function (this: unknown, opts: { baseURL: string }) {
      capturedBaseURL = opts.baseURL;
    }),
  }));

  await import('../api');

  globalThis.window = originalWindow;

  return capturedBaseURL;
}

describe('web-store apiClient baseURL', () => {
  it('SSR + API_URL_INTERNAL set → uses internal URL', async () => {
    const baseURL = await loadApiClientWithEnv(
      {
        API_URL_INTERNAL: 'http://gateway-service:8080',
        NEXT_PUBLIC_API_URL: 'http://ecommerce.local',
      },
      false,
    );
    expect(baseURL).toBe('http://gateway-service:8080');
  });

  it('SSR + API_URL_INTERNAL unset → falls back to NEXT_PUBLIC_API_URL', async () => {
    const baseURL = await loadApiClientWithEnv(
      {
        API_URL_INTERNAL: undefined,
        NEXT_PUBLIC_API_URL: 'http://ecommerce.local',
      },
      false,
    );
    expect(baseURL).toBe('http://ecommerce.local');
  });

  it('SSR + both env vars unset → falls back to localhost:8080', async () => {
    const baseURL = await loadApiClientWithEnv(
      {
        API_URL_INTERNAL: undefined,
        NEXT_PUBLIC_API_URL: undefined,
      },
      false,
    );
    expect(baseURL).toBe('http://localhost:8080');
  });

  it('client (window defined) → uses same-origin BFF proxy, ignores both backend URLs (F2)', async () => {
    // Phase 4.5 F2: the client never talks to the backend gateway directly; it
    // goes through the same-origin `/api/bff` proxy which attaches the bearer
    // server-side. So the client baseURL is always `/api/bff`, regardless of
    // API_URL_INTERNAL / NEXT_PUBLIC_API_URL.
    const baseURL = await loadApiClientWithEnv(
      {
        API_URL_INTERNAL: 'http://gateway-service:8080',
        NEXT_PUBLIC_API_URL: 'http://ecommerce.local',
      },
      true,
    );
    expect(baseURL).toBe('/api/bff');
  });
});
