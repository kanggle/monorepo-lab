import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin audit read proxy route handler (TASK-PC-FE-003 — READ-ONLY):
 *   - GET only — no body schema, no mutation branch.
 *   - the operator token + tenant are attached server-side; NO
 *     X-Operator-Reason / Idempotency-Key are ever sent (read-only).
 *   - 401 → 401 (client api-client refresh→re-login; no partial state).
 *   - 403 PERMISSION_DENIED → 403 (inline permission, no crash).
 *   - 403 TENANT_SCOPE_DENIED → 403 (inline tenant-scope, no crash).
 *   - 422 → 422 (inline field-level).
 *   - 503 → 503 (audit section degrades only).
 *   - no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; never empty).
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
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    GAP_ADMIN_API_BASE: 'http://gap.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as auditGET } from '@/app/api/audit/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PAGE = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/audit proxy', () => {
  it('forwards filter/source/page params and attaches the operator token (no reason/idempotency)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAGE));
    vi.stubGlobal('fetch', fetchMock);

    const res = await auditGET(
      new Request(
        'http://console.local/api/audit?source=login_history&accountId=acc-1&page=1&size=999',
      ),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('source')).toBe('login_history');
    expect(upstream.searchParams.get('accountId')).toBe('acc-1');
    expect(upstream.searchParams.get('page')).toBe('1');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OP');
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('401 from GAP → 401 (forced re-login, no partial authed state)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const res = await auditGET(
      new Request('http://console.local/api/audit?source=admin'),
    );
    expect(res.status).toBe(401);
  });

  it('403 PERMISSION_DENIED from GAP → 403 (inline, no crash)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const res = await auditGET(
      new Request('http://console.local/api/audit?source=suspicious'),
    );
    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe('PERMISSION_DENIED');
  });

  it('403 TENANT_SCOPE_DENIED from GAP → 403 (inline tenant-scope)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'TENANT_SCOPE_DENIED' }, 403)),
    );
    const res = await auditGET(
      new Request('http://console.local/api/audit?tenantId=foreign'),
    );
    expect(res.status).toBe(403);
    const body = await res.json();
    expect(body.code).toBe('TENANT_SCOPE_DENIED');
  });

  it('422 from GAP → 422 (inline field-level)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'VALIDATION_ERROR' }, 422)),
    );
    const res = await auditGET(
      new Request('http://console.local/api/audit'),
    );
    expect(res.status).toBe(422);
  });

  it('503 from GAP → 503 (audit section degrades only)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'CIRCUIT_OPEN' }, 503)),
    );
    const res = await auditGET(
      new Request('http://console.local/api/audit'),
    );
    expect(res.status).toBe(503);
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT (tenant gate, never empty)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await auditGET(
      new Request('http://console.local/api/audit'),
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
