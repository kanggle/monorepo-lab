import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin org_scope proxy route handlers (TASK-PC-FE-050 / TASK-BE-339):
 *   - assignments route exports GET ONLY (no POST/PUT/PATCH/DELETE).
 *   - org-scope route exports PUT ONLY (no GET/POST/PATCH/DELETE).
 *   - GET .../assignments → forwards to GAP; 200 passthrough; server-only
 *     operator-token bearer + X-Tenant-Id (never the GAP OIDC token).
 *   - PUT .../org-scope → forwards X-Operator-Reason; NO Idempotency-Key;
 *     tri-state body { orgScope: null | [] | [ids] } passes through.
 *   - error mapping: 403 TENANT_SCOPE_MISMATCH → 403; 404 ASSIGNMENT_NOT_FOUND
 *     → 404; 401 → 401; malformed body → 422 without calling GAP.
 *   - no active tenant → 400 NO_ACTIVE_TENANT (tenant gate; no fetch).
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

import * as assignmentsRoute from '@/app/api/operators/[operatorId]/assignments/route';
import * as orgScopeRoute from '@/app/api/operators/[operatorId]/assignments/[tenantId]/org-scope/route';
import { OPERATOR_COOKIE, TENANT_COOKIE, ACCESS_COOKIE } from '@/shared/lib/session';

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

describe('method exposure (GET-only / PUT-only gates)', () => {
  it('assignments route exposes ONLY GET (no POST/PUT/PATCH/DELETE)', () => {
    expect(typeof assignmentsRoute.GET).toBe('function');
    expect(
      (assignmentsRoute as Record<string, unknown>).POST,
    ).toBeUndefined();
    expect((assignmentsRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((assignmentsRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect(
      (assignmentsRoute as Record<string, unknown>).DELETE,
    ).toBeUndefined();
  });

  it('org-scope route exposes ONLY PUT (no GET/POST/PATCH/DELETE)', () => {
    expect(typeof orgScopeRoute.PUT).toBe('function');
    expect((orgScopeRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((orgScopeRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((orgScopeRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((orgScopeRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});

describe('GET /api/operators/[id]/assignments proxy', () => {
  it('forwards to GAP with operator-token bearer + X-Tenant-Id; 200 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-must-not-leak');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        assignments: [{ tenantId: 'acme-corp', orgScope: ['dept-sales'] }],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await assignmentsRoute.GET(
      new Request('http://console.local/api/operators/op-1/assignments'),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(200);
    expect((await res.json()).assignments[0].orgScope).toEqual(['dept-sales']);
    const h = (fetchMock.mock.calls[0][1] as RequestInit).headers as Record<
      string,
      string
    >;
    expect(h.Authorization).toBe('Bearer OPERATOR-correct');
    expect(h.Authorization).not.toContain('GAP-OIDC-must-not-leak');
    expect(h['X-Tenant-Id']).toBe('acme-corp');
  });

  it('no active tenant → 400 NO_ACTIVE_TENANT, no fetch', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await assignmentsRoute.GET(
      new Request('http://console.local/api/operators/op-1/assignments'),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('NO_ACTIVE_TENANT');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('401 from GAP → 401 (forced re-login)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const res = await assignmentsRoute.GET(
      new Request('http://console.local/api/operators/op-1/assignments'),
      { params: Promise.resolve({ operatorId: 'op-1' }) },
    );
    expect(res.status).toBe(401);
  });
});

describe('PUT /api/operators/[id]/assignments/[tenantId]/org-scope proxy', () => {
  function putReq(body: unknown) {
    return new Request(
      'http://console.local/api/operators/op-1/assignments/acme-corp/org-scope',
      { method: 'PUT', body: JSON.stringify(body) },
    );
  }
  const params = () =>
    Promise.resolve({ operatorId: 'op-1', tenantId: 'acme-corp' });

  it('forwards X-Operator-Reason; NO Idempotency-Key; tri-state body passes through', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-correct');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ tenantId: 'acme-corp', orgScope: ['dept-sales'] }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await orgScopeRoute.PUT(
      putReq({ orgScope: ['dept-sales'], reason: 'scope to sales' }),
      { params: params() },
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(
      '/api/admin/operators/op-1/assignments/acme-corp/org-scope',
    );
    expect((init as RequestInit).method).toBe('PUT');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(decodeURIComponent(h['X-Operator-Reason'])).toBe('scope to sales');
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      orgScope: ['dept-sales'],
    });
  });

  it('null (clear) body forwards as { orgScope: null } JSON literal', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ tenantId: 'acme-corp' }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await orgScopeRoute.PUT(
      putReq({ orgScope: null, reason: 'reset' }),
      { params: params() },
    );
    expect(res.status).toBe(200);
    const rawBody = (fetchMock.mock.calls[0][1] as RequestInit).body as string;
    expect(rawBody).toContain('"orgScope":null');
  });

  it('malformed body (orgScope missing) → 422 without calling GAP', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await orgScopeRoute.PUT(putReq({ reason: 'r' }), {
      params: params(),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 TENANT_SCOPE_MISMATCH from GAP → 403 (inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'TENANT_SCOPE_MISMATCH' }, 403)),
    );
    const res = await orgScopeRoute.PUT(
      putReq({ orgScope: [], reason: 'r' }),
      { params: params() },
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('TENANT_SCOPE_MISMATCH');
  });

  it('404 ASSIGNMENT_NOT_FOUND from GAP → 404 (inline)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OP');
    cookieJar.set(TENANT_COOKIE, 'acme-corp');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'ASSIGNMENT_NOT_FOUND' }, 404)),
    );
    const res = await orgScopeRoute.PUT(
      putReq({ orgScope: ['dept-x'], reason: 'r' }),
      { params: params() },
    );
    expect(res.status).toBe(404);
    expect((await res.json()).code).toBe('ASSIGNMENT_NOT_FOUND');
  });
});
