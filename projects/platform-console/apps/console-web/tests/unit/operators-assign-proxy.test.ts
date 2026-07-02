import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin operator↔tenant assignment proxy route handlers
 * (TASK-PC-FE-157 / TASK-BE-347):
 *   - `.../assignments/[tenantId]` route exports POST + DELETE ONLY (no
 *     GET/PUT/PATCH).
 *   - POST → create the assignment; forwards operator-token bearer +
 *     X-Tenant-Id + X-Operator-Reason (NO Idempotency-Key); 201 passthrough.
 *   - DELETE → remove the assignment; forwards reason; 204 passthrough.
 *   - error mapping: 403 TENANT_SCOPE_DENIED → 403; 404 OPERATOR_NOT_FOUND /
 *     404 ASSIGNMENT_NOT_FOUND → 404; 409 ASSIGNMENT_ALREADY_EXISTS → 409;
 *     401 → 401; malformed body → 422 without calling GAP; no active tenant →
 *     400 NO_ACTIVE_TENANT (no fetch).
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

import * as assignRoute from '@/app/api/operators/[operatorId]/assignments/[tenantId]/route';
import { OPERATOR_COOKIE, TENANT_COOKIE, ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const params = () =>
  Promise.resolve({ operatorId: 'op-1', tenantId: 'acme-corp' });

function reqWithBody(method: string, body: unknown) {
  return new Request(
    'http://console.local/api/operators/op-1/assignments/acme-corp',
    { method, body: JSON.stringify(body) },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('method exposure (POST + DELETE only)', () => {
  it('exposes POST + DELETE; NO GET/PUT/PATCH', () => {
    expect(typeof assignRoute.POST).toBe('function');
    expect(typeof assignRoute.DELETE).toBe('function');
    expect((assignRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((assignRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((assignRoute as Record<string, unknown>).PATCH).toBeUndefined();
  });
});

describe('POST /api/operators/[id]/assignments/[tenantId] (assign)', () => {
  it('forwards operator-token bearer + X-Tenant-Id + reason (NO key); 201 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await assignRoute.POST(
      reqWithBody('POST', { reason: 'onboard employee' }),
      { params: params() },
    );
    expect(res.status).toBe(201);
    expect((await res.json()).tenantId).toBe('acme-corp');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('POST');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('onboard employee');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('malformed body (reason missing) → 422 without calling GAP', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await assignRoute.POST(reqWithBody('POST', {}), {
      params: params(),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT, no fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await assignRoute.POST(reqWithBody('POST', { reason: 'r' }), {
      params: params(),
    });
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 ASSIGNMENT_ALREADY_EXISTS from IAM → 409 (inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'ASSIGNMENT_ALREADY_EXISTS' }, 409)),
    );
    const res = await assignRoute.POST(
      reqWithBody('POST', { reason: 'r' }),
      { params: params() },
    );
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('ASSIGNMENT_ALREADY_EXISTS');
  });

  it('403 TENANT_SCOPE_DENIED from IAM → 403 (inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'TENANT_SCOPE_DENIED' }, 403)),
    );
    const res = await assignRoute.POST(
      reqWithBody('POST', { reason: 'r' }),
      { params: params() },
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('TENANT_SCOPE_DENIED');
  });
});

describe('DELETE /api/operators/[id]/assignments/[tenantId] (unassign)', () => {
  it('forwards reason; 204 passthrough', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await assignRoute.DELETE(
      reqWithBody('DELETE', { reason: 'offboard' }),
      { params: params() },
    );
    expect(res.status).toBe(204);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/api/admin/operators/op-1/assignments/acme-corp');
    expect((init as RequestInit).method).toBe('DELETE');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('offboard');
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('404 ASSIGNMENT_NOT_FOUND from IAM → 404 (inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'ASSIGNMENT_NOT_FOUND' }, 404)),
    );
    const res = await assignRoute.DELETE(
      reqWithBody('DELETE', { reason: 'r' }),
      { params: params() },
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('ASSIGNMENT_NOT_FOUND');
  });

  it('malformed body (reason missing) → 422 without calling GAP', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await assignRoute.DELETE(reqWithBody('DELETE', {}), {
      params: params(),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
