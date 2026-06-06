import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `shared/api/registry-client.ts` — the registry call MUST authenticate with
 * the EXCHANGED operator token (the operator cookie), NEVER the IAM OIDC
 * access token. This is the assertion that closes the #569 latent defect
 * (console-integration-contract § 2.1/§ 2.2). Existing 401/503/timeout
 * degrade paths must still hold.
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001 test lane).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { fetchRegistry } from '@/shared/api/registry-client';
import { RegistryUnavailableError, ApiError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const REGISTRY_200 = {
  products: [
    {
      productKey: 'iam',
      displayName: 'IAM',
      available: true,
      tenants: ['wms'],
      baseRoute: '/iam',
    },
  ],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('fetchRegistry — operator-token bearer (#569 fix)', () => {
  it('sends the OPERATOR cookie as the bearer, NOT the IAM access token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-TOKEN-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-correct');

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(REGISTRY_200), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await fetchRegistry();

    const [, init] = fetchMock.mock.calls[0];
    const auth = (init as RequestInit).headers as Record<string, string>;
    expect(auth.Authorization).toBe('Bearer OPERATOR-TOKEN-correct');
    // The IAM OIDC token must NEVER appear on the /api/admin/** call.
    expect(auth.Authorization).not.toContain(
      'GAP-OIDC-ACCESS-TOKEN-must-not-leak',
    );
  });

  it('401s (no fetch) when only the IAM token is present but no operator token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-only');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await fetchRegistry().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    // It must NOT silently fall back to the IAM token on the boundary.
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('fetchRegistry — existing degrade paths still hold', () => {
  beforeEach(() => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
  });

  it('401 → RegistryUnavailableError(unauthorized)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const err = await fetchRegistry().catch((e) => e);
    expect(err).toBeInstanceOf(RegistryUnavailableError);
    expect(err.reason).toBe('unauthorized');
  });

  it('503 CIRCUIT_OPEN → RegistryUnavailableError(circuit_open)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'CIRCUIT_OPEN' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const err = await fetchRegistry().catch((e) => e);
    expect(err).toBeInstanceOf(RegistryUnavailableError);
    expect(err.reason).toBe('circuit_open');
  });

  it('timeout → RegistryUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            reject(e);
          });
        });
      }),
    );
    const err = await fetchRegistry().catch((e) => e);
    expect(err).toBeInstanceOf(RegistryUnavailableError);
    expect(err.reason).toBe('timeout');
  });
});
