import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin erp READ-MODEL proxy routes (TASK-PC-FE-049 — § 2.4.8
 * *Integrated read-model binding*):
 *
 *   - `GET /api/erp/read-model/employees` (list) — read-pure, GET only.
 *   - `GET /api/erp/read-model/employees/{id}` (detail) — read-pure, GET only.
 *
 * AC-3 pins:
 *   - both routes are **GET-only** (no POST / PATCH handler exists;
 *     the route files do NOT export POST).
 *   - credential = unchanged GAP OIDC domain-facing token (NOT the
 *     operator token, NOT `getOperatorToken()`); no `X-Operator-Reason`;
 *     no `Idempotency-Key`; no body on GET.
 *   - E3 `?asOf=` threaded through verbatim.
 *   - `?departmentId=` and `?status=` forwarded verbatim (read-model
 *     specific filters).
 *   - 401 / 403 / 404 / 503 error mapping via shared `mapErpError`.
 *   - The upstream URL includes `/read-model/employees` (NOT
 *     `/masterdata/employees`) — the path routing distinction.
 *
 * This test mirrors the shape of `erp-proxy.test.ts` exactly; the
 * read-model routes are a new surface layered on the SAME erp base
 * URL + credential plumbing.
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
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as rmListGET } from '@/app/api/erp/read-model/employees/route';
import { GET as rmDetailGET } from '@/app/api/erp/read-model/employees/[id]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

// Import the route modules to assert POST is NOT exported.
import * as rmListRoute from '@/app/api/erp/read-model/employees/route';
import * as rmDetailRoute from '@/app/api/erp/read-model/employees/[id]/route';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

/** Minimal read-model list response fixture. */
const RM_LIST_ENV = {
  data: [
    {
      id: 'emp-org-1',
      employeeNumber: 'E-1001',
      name: '홍길동',
      status: 'ACTIVE',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
      department: {
        id: 'dept-1',
        code: 'SALES',
        name: '영업본부',
        path: [
          { id: 'dept-root', code: 'HQ', name: '본사' },
          { id: 'dept-1', code: 'SALES', name: '영업본부' },
        ],
      },
      costCenter: { id: 'cc-1', code: 'CC-100', name: '영업원가센터' },
      jobGrade: { id: 'jg-1', code: 'G3', name: '사원', displayOrder: 30 },
    },
  ],
  meta: {
    page: 0,
    size: 20,
    totalElements: 1,
    timestamp: '2026-06-04T00:00:00Z',
    warning: 'Eventually-consistent read-model',
  },
};

/** Detail with one unresolved reference. */
const RM_DETAIL_ENV = {
  data: {
    id: 'emp-org-1',
    employeeNumber: 'E-1001',
    name: '홍길동',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    department: null,
    costCenter: { id: 'cc-1', code: 'CC-100', name: '영업원가센터' },
    jobGrade: null,
  },
  meta: {
    timestamp: '2026-06-04T00:00:00Z',
    warning: 'Eventually-consistent read-model',
    unresolved: ['department', 'jobGrade'],
  },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

// ===========================================================================
// AC-3: read-model routes are GET-only (no POST / PATCH)
// ===========================================================================

describe('AC-3 — read-model routes export GET only (no POST / PATCH)', () => {
  it('read-model employees list route does NOT export POST', () => {
    expect(typeof rmListRoute.GET).toBe('function');
    expect((rmListRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((rmListRoute as Record<string, unknown>).PATCH).toBeUndefined();
  });

  it('read-model employees detail route does NOT export POST', () => {
    expect(typeof rmDetailRoute.GET).toBe('function');
    expect((rmDetailRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((rmDetailRoute as Record<string, unknown>).PATCH).toBeUndefined();
  });
});

// ===========================================================================
// AC-3: credential invariant — GAP OIDC access token, NOT operator token
// ===========================================================================

describe('GET /api/erp/read-model/employees — credential + path', () => {
  it('attaches the GAP OIDC access token (NOT the operator token), targets /read-model/ path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS-READMODEL');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await rmListGET(
      new Request('http://console.local/api/erp/read-model/employees'),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    // AC-3: GAP OIDC token, never operator token.
    expect(h.Authorization).toBe('Bearer GAP-ACCESS-READMODEL');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    // No mutation headers.
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    // GET — no body.
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    // Upstream URL must contain /read-model/ (not /masterdata/).
    expect(String(url)).toContain('http://erp.local/api/erp/read-model/employees');
    expect(String(url)).not.toContain('/masterdata/');
  });

  it('no GAP session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await rmListGET(
      new Request('http://console.local/api/erp/read-model/employees'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('threads ?asOf= verbatim (E3)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await rmListGET(
      new Request(
        'http://console.local/api/erp/read-model/employees?asOf=2025-06-01&page=0&size=10',
      ),
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('asOf')).toBe('2025-06-01');
    expect(upstream.searchParams.get('page')).toBe('0');
    expect(upstream.searchParams.get('size')).toBe('10');
  });

  it('forwards ?departmentId= (read-model subtree filter)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await rmListGET(
      new Request(
        'http://console.local/api/erp/read-model/employees?departmentId=dept-root',
      ),
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('departmentId')).toBe('dept-root');
  });

  it('forwards ?status= (read-model status filter)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await rmListGET(
      new Request(
        'http://console.local/api/erp/read-model/employees?status=RETIRED',
      ),
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('status')).toBe('RETIRED');
  });

  it('403 TENANT_FORBIDDEN → 403 inline', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('TENANT_FORBIDDEN', 403)),
    );
    const res = await rmListGET(
      new Request('http://console.local/api/erp/read-model/employees'),
    );
    expect(res.status).toBe(403);
  });

  it('503 → 503 (erp section degrades only, not whole console)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await rmListGET(
      new Request('http://console.local/api/erp/read-model/employees'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/erp/read-model/employees/{id} — credential + path + asOf', () => {
  it('attaches GAP OIDC token + targets /read-model/{id} path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await rmDetailGET(
      new Request('http://console.local/api/erp/read-model/employees/emp-org-1'),
      { params: Promise.resolve({ id: 'emp-org-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    // Path is /read-model/, not /masterdata/.
    const upstream = new URL(String(url));
    expect(upstream.pathname).toBe('/api/erp/read-model/employees/emp-org-1');
    expect(upstream.pathname).not.toContain('/masterdata/');
  });

  it('threads ?asOf= verbatim on detail (E3)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RM_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await rmDetailGET(
      new Request(
        'http://console.local/api/erp/read-model/employees/emp-org-1?asOf=2025-03-15',
      ),
      { params: Promise.resolve({ id: 'emp-org-1' }) },
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('asOf')).toBe('2025-03-15');
  });

  it('404 MASTERDATA_NOT_FOUND → 404 inline actionable', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('MASTERDATA_NOT_FOUND', 404)),
    );
    const res = await rmDetailGET(
      new Request('http://console.local/api/erp/read-model/employees/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('MASTERDATA_NOT_FOUND');
  });
});
