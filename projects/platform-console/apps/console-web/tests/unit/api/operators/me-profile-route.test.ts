import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin self update-profile proxy (TASK-PC-FE-016):
 *   - (a) POST `{ operatorContext: { defaultAccountId: "<valid>" } }` → 204
 *     (forwards to IAM PATCH `me/profile` with NO reason / NO key);
 *   - (b) POST `{ operatorContext: {} }` (missing `defaultAccountId`) → 422
 *     VALIDATION_ERROR (proxy zod fails first; IAM not called);
 *   - (c) IAM returns 503 CIRCUIT_OPEN → proxy maps to 503 via `mapError`;
 *
 * Plus: explicit `null` clears the column; an extra top-level key is
 * rejected at the proxy (`.strict()` mirror of FAIL_ON_UNKNOWN_PROPERTIES).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..010 lane).
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { POST as profilePOST } from '@/app/api/operators/me/profile/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function noContent() {
  return new Response(null, { status: 204 });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('POST /api/operators/me/profile proxy (a) — valid body → 204', () => {
  it('forwards to the self path with NO reason / NO key and returns 204', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: {
            defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
          },
        }),
      }),
    );

    expect(res.status).toBe(204);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/me/profile');
    expect((init as RequestInit).method).toBe('PATCH');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    // The body shape mirrors the registry read shape verbatim.
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody).toEqual({
      operatorContext: {
        defaultAccountId: '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
      },
    });
  });

  it('explicit null clears the column (forwards `null`, NOT empty string)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: null },
        }),
      }),
    );

    expect(res.status).toBe(204);
    const [, init] = fetchMock.mock.calls[0];
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody).toEqual({
      operatorContext: { defaultAccountId: null },
    });
  });
});

describe('POST /api/operators/me/profile proxy (b) — malformed body → 422', () => {
  it('missing defaultAccountId → 422 VALIDATION_ERROR, NO fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({ operatorContext: {} }),
      }),
    );

    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('whitespace-only defaultAccountId → 422 (zod `.trim().min(1)`)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: '   ' },
        }),
      }),
    );

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('over-36-char defaultAccountId → 422', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: 'x'.repeat(37) },
        }),
      }),
    );

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('unknown top-level key → 422 (`.strict()` mirror of FAIL_ON_UNKNOWN_PROPERTIES)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: 'acc-uuid-7' },
          extra: 'forbidden',
        }),
      }),
    );

    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/operators/me/profile proxy (c) — downstream error mapping', () => {
  it('IAM 503 CIRCUIT_OPEN → proxy 503 (operators section degrades only)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: 'acc-uuid-7' },
        }),
      }),
    );

    expect(res.status).toBe(503);
  });

  it('IAM 409 OPTIMISTIC_LOCK_CONFLICT → proxy 409 (passthrough inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          jsonResponse({ code: 'OPTIMISTIC_LOCK_CONFLICT' }, 409),
        ),
    );

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: 'acc-uuid-7' },
        }),
      }),
    );

    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('OPTIMISTIC_LOCK_CONFLICT');
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; NO fetch)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await profilePOST(
      new Request('http://console.local/api/operators/me/profile', {
        method: 'POST',
        body: JSON.stringify({
          operatorContext: { defaultAccountId: 'acc-uuid-7' },
        }),
      }),
    );

    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
