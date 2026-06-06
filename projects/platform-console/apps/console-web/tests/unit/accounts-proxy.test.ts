import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin accounts proxy route handlers (TASK-PC-FE-002):
 *   - search 401 → 401 (client api-client refresh→re-login; no partial state)
 *   - search 503 → 503 (accounts section degrades only)
 *   - no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; never empty)
 *   - lock proxy passes the operator-entered reason + idempotency-key
 *     through and never fabricates a reason
 *   - 403 PERMISSION_DENIED → 403 (forced re-login, inline-permission UX)
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
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as searchGET } from '@/app/api/accounts/route';
import { POST as lockPOST } from '@/app/api/accounts/[accountId]/lock/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/accounts proxy', () => {
  it('401 from IAM → 401 (forced re-login, no partial authed state)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const res = await searchGET(
      new Request('http://console.local/api/accounts?page=0&size=20'),
    );
    expect(res.status).toBe(401);
  });

  it('503 from IAM → 503 (accounts section degrades only)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const res = await searchGET(
      new Request('http://console.local/api/accounts'),
    );
    expect(res.status).toBe(503);
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate, never empty)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await searchGET(
      new Request('http://console.local/api/accounts'),
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/accounts/[id]/lock proxy', () => {
  it('passes the operator reason + idempotency-key to IAM unchanged', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        accountId: 'acc-1',
        previousStatus: 'ACTIVE',
        currentStatus: 'LOCKED',
        operatorId: 'op',
        lockedAt: 'x',
        auditId: 'a',
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await lockPOST(
      new Request('http://console.local/api/accounts/acc-1/lock', {
        method: 'POST',
        body: JSON.stringify({
          reason: 'operator entered reason',
          idempotencyKey: 'idem-1',
        }),
      }),
      { params: Promise.resolve({ accountId: 'acc-1' }) },
    );
    expect(res.status).toBe(200);
    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    // TASK-MONO-176: percent-encoded on the wire to GAP; round-trips.
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe(
      'operator entered reason',
    );
    expect(h['Idempotency-Key']).toBe('idem-1');
  });

  it('403 PERMISSION_DENIED from IAM → 403 (inline permission / re-login)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'PERMISSION_DENIED' }, 403),
      ),
    );
    const res = await lockPOST(
      new Request('http://console.local/api/accounts/acc-1/lock', {
        method: 'POST',
        body: JSON.stringify({ reason: 'r', idempotencyKey: 'k' }),
      }),
      { params: Promise.resolve({ accountId: 'acc-1' }) },
    );
    expect(res.status).toBe(403);
  });

  it('a malformed body → 422 without calling IAM (the reason is never fabricated)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await lockPOST(
      new Request('http://console.local/api/accounts/acc-1/lock', {
        method: 'POST',
        body: JSON.stringify({ idempotencyKey: 'k' }), // no reason
      }),
      { params: Promise.resolve({ accountId: 'acc-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
