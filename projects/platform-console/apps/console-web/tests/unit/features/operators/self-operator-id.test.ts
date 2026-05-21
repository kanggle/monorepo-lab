import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/operators/api/operators-api.ts` `getSelfOperatorIdOrNull()` —
 * TASK-PC-FE-020 server-side resolve of the caller's own operatorId for the
 * `OperatorsScreen` self-row UX gate (PC-FE-017 honest gap (b) closure).
 *
 * Fail-graceful contract:
 *   - (a) success → returns `operatorId` from the parsed
 *     `OperatorSummarySchema` response body.
 *   - (b) 401 (token expired) → returns `null` (no throw); page renders with
 *     the gate inactive; the next mutation will surface 401 → redirect.
 *   - (c) 403 (permission lost between login and now) → returns `null`.
 *   - (d) 503 (admin-service degraded) → returns `null` (OperatorsUnavailable
 *     error is swallowed; the list call's degraded branch handles the page).
 *   - (e) schema parse failure (producer drift) → returns `null`.
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
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
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

import { getSelfOperatorIdOrNull } from '@/features/operators/api/operators-api';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const validMeBody = {
  operatorId: 'op-self-uuid',
  email: 'self@example.com',
  displayName: 'Self',
  status: 'ACTIVE',
  roles: ['SUPER_ADMIN'],
  totpEnrolled: true,
  lastLoginAt: '2026-05-22T00:00:00Z',
  createdAt: '2026-01-01T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
  cookieJar.set(TENANT_COOKIE, 'wms');
});

describe('getSelfOperatorIdOrNull — fail-graceful contract (TASK-PC-FE-020)', () => {
  it('(a) success → returns operatorId from GET /api/admin/me', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, validMeBody));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();

    expect(result).toBe('op-self-uuid');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/me');
    expect((init as RequestInit).method).toBe('GET');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-TOKEN');
    expect(h['X-Tenant-Id']).toBe('wms');
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('(b) 401 TOKEN_INVALID → null (no throw; gate inactive)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(401, { code: 'TOKEN_INVALID' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
  });

  it('(c) 403 PERMISSION_DENIED → null', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(403, { code: 'PERMISSION_DENIED' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
  });

  it('(d) 503 DOWNSTREAM_ERROR → null (degraded admin-service)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(503, { code: 'DOWNSTREAM_ERROR' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
  });

  it('(e) schema parse failure (producer drift) → null', async () => {
    // Response 200 but body missing required fields → zod parse throws.
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(200, { foo: 'bar' }));
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
  });

  it('(f) no operator session (cookie missing) → null', async () => {
    cookieJar.clear();
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
    // No fetch — pre-flight rejected.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('(g) no active tenant → null', async () => {
    cookieJar.clear();
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const result = await getSelfOperatorIdOrNull();
    expect(result).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
