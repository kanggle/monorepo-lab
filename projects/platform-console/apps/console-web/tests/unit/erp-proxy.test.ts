import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin erp-ops proxy route handlers (TASK-PC-FE-010 —
 * § 2.4.8):
 *   - read GET (5 list + 5 detail = 10 routes): GAP OIDC access
 *     token attached server-side (NOT the operator token); no
 *     mutation artifacts; STRICTLY READ-ONLY (GET-only routes).
 *   - E3 `?asOf=` threaded through verbatim on every list /
 *     detail route.
 *   - 401 → 401 (whole-session re-login signal; no partial
 *     authed state).
 *   - 403 → 403 (token not erp-scoped / data-scope / external-
 *     traffic boundary — inline, no crash).
 *   - 404 MASTERDATA_NOT_FOUND → 404 inline actionable.
 *   - 400 / 422 → passthrough.
 *   - 503 / timeout → 503 (erp section degrades only; shell
 *     intact).
 *   - **no 429 / Retry-After branch** (erp has no documented
 *     429 — identical to finance; a stray 429 lands as a
 *     passthrough — NO retry storm, NO Retry-After branch).
 *
 * There is NO mutation proxy route at all (no erp write, no v2
 * approval-service / read-model-service / future admin-service
 * surface).
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

import { GET as deptListGET } from '@/app/api/erp/masterdata/departments/route';
import { GET as deptDetailGET } from '@/app/api/erp/masterdata/departments/[id]/route';
import { GET as empListGET } from '@/app/api/erp/masterdata/employees/route';
import { GET as empDetailGET } from '@/app/api/erp/masterdata/employees/[id]/route';
import { GET as jgListGET } from '@/app/api/erp/masterdata/job-grades/route';
import { GET as jgDetailGET } from '@/app/api/erp/masterdata/job-grades/[id]/route';
import { GET as ccListGET } from '@/app/api/erp/masterdata/cost-centers/route';
import { GET as ccDetailGET } from '@/app/api/erp/masterdata/cost-centers/[id]/route';
import { GET as bpListGET } from '@/app/api/erp/masterdata/business-partners/route';
import { GET as bpDetailGET } from '@/app/api/erp/masterdata/business-partners/[id]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}
function erpError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const DEPT_LIST_ENV = {
  data: [
    {
      id: 'dept-1',
      code: 'DEPT-001',
      name: 'Sales',
      parentId: null,
      status: 'ACTIVE',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};
const DEPT_DETAIL_ENV = {
  data: {
    id: 'dept-1',
    code: 'DEPT-001',
    name: 'Sales',
    parentId: null,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
  },
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/erp/masterdata/departments proxy (read-only)', () => {
  it('attaches the GAP OIDC access token (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await deptListGET(
      new Request('http://console.local/api/erp/masterdata/departments'),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect(String(url)).toContain(
      'http://erp.local/api/erp/masterdata/departments',
    );
  });

  it('threads `?asOf=` to the producer verbatim (E3 CORE invariant)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await deptListGET(
      new Request(
        'http://console.local/api/erp/masterdata/departments?asOf=2025-01-01&page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('asOf')).toBe('2025-01-01');
    expect(upstream.searchParams.get('page')).toBe('0');
    expect(upstream.searchParams.get('size')).toBe('20');
  });

  it('forwards producer per-master filters (parentId)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await deptListGET(
      new Request(
        'http://console.local/api/erp/masterdata/departments?parentId=dept-parent',
      ),
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('parentId')).toBe('dept-parent');
  });

  it('no GAP session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await deptListGET(
      new Request('http://console.local/api/erp/masterdata/departments'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 TENANT_FORBIDDEN → 403 (inline not scoped)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('TENANT_FORBIDDEN', 403)),
    );
    const res = await deptListGET(
      new Request('http://console.local/api/erp/masterdata/departments'),
    );
    expect(res.status).toBe(403);
  });

  it('a stray 429 falls through as a passthrough — NO Retry-After branch, NO retry storm', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: {
            'Content-Type': 'application/json',
            'Retry-After': '1',
          },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await deptListGET(
      new Request('http://console.local/api/erp/masterdata/departments'),
    );
    // The proxy passes the 429 through (no fabricated backoff /
    // Retry-After branch — erp has no documented 429; § 2.4.8
    // honest difference from scm, identical to finance § 2.4.7).
    expect(res.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.headers.get('Retry-After')).toBeNull();
  });
});

describe('GET /api/erp/masterdata/departments/{id} proxy (read-only)', () => {
  it('threads `?asOf=` to the producer verbatim on detail (E3 CORE invariant)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(DEPT_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await deptDetailGET(
      new Request(
        'http://console.local/api/erp/masterdata/departments/dept-1?asOf=2025-06-01',
      ),
      { params: Promise.resolve({ id: 'dept-1' }) },
    );
    expect(res.status).toBe(200);
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.pathname).toBe('/api/erp/masterdata/departments/dept-1');
    expect(upstream.searchParams.get('asOf')).toBe('2025-06-01');
  });

  it('404 MASTERDATA_NOT_FOUND → 404 inline actionable', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('MASTERDATA_NOT_FOUND', 404)),
    );
    const res = await deptDetailGET(
      new Request('http://console.local/api/erp/masterdata/departments/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('MASTERDATA_NOT_FOUND');
  });
});

describe('GET /api/erp/masterdata/employees + employees/{id} proxies (read-only)', () => {
  it('attaches the GAP OIDC access token + threads asOf (list)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await empListGET(
      new Request(
        'http://console.local/api/erp/masterdata/employees?asOf=2025-01-01&departmentId=dept-1',
      ),
    );
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    const upstream = new URL(String(url));
    expect(upstream.searchParams.get('asOf')).toBe('2025-01-01');
    expect(upstream.searchParams.get('departmentId')).toBe('dept-1');
  });

  it('detail threads asOf verbatim', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await empDetailGET(
      new Request(
        'http://console.local/api/erp/masterdata/employees/emp-1?asOf=2025-06-01',
      ),
      { params: Promise.resolve({ id: 'emp-1' }) },
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.pathname).toBe('/api/erp/masterdata/employees/emp-1');
    expect(upstream.searchParams.get('asOf')).toBe('2025-06-01');
  });
});

describe('GET /api/erp/masterdata/job-grades + cost-centers + business-partners proxies (read-only)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  });

  it('job-grades list: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await jgListGET(
      new Request(
        'http://console.local/api/erp/masterdata/job-grades?asOf=2025-01-01',
      ),
    );
    expect(res.status).toBe(200);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.method).toBe('GET');
    expect(
      new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('asOf'),
    ).toBe('2025-01-01');
  });

  it('job-grades detail: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await jgDetailGET(
      new Request(
        'http://console.local/api/erp/masterdata/job-grades/jg-1?asOf=2025-01-01',
      ),
      { params: Promise.resolve({ id: 'jg-1' }) },
    );
    expect(res.status).toBe(200);
    expect(
      new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('asOf'),
    ).toBe('2025-01-01');
  });

  it('cost-centers list: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await ccListGET(
      new Request(
        'http://console.local/api/erp/masterdata/cost-centers?asOf=2025-01-01',
      ),
    );
    expect(
      new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('asOf'),
    ).toBe('2025-01-01');
  });

  it('cost-centers detail: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await ccDetailGET(
      new Request(
        'http://console.local/api/erp/masterdata/cost-centers/cc-1?asOf=2025-01-01',
      ),
      { params: Promise.resolve({ id: 'cc-1' }) },
    );
    expect(
      new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('asOf'),
    ).toBe('2025-01-01');
  });

  it('business-partners list: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_LIST_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await bpListGET(
      new Request(
        'http://console.local/api/erp/masterdata/business-partners?asOf=2025-01-01&partnerType=CUSTOMER',
      ),
    );
    const upstream = new URL(String(fetchMock.mock.calls[0][0]));
    expect(upstream.searchParams.get('asOf')).toBe('2025-01-01');
    expect(upstream.searchParams.get('partnerType')).toBe('CUSTOMER');
  });

  it('business-partners detail: GET + asOf passthrough', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DEPT_DETAIL_ENV));
    vi.stubGlobal('fetch', fetchMock);
    await bpDetailGET(
      new Request(
        'http://console.local/api/erp/masterdata/business-partners/bp-1?asOf=2025-01-01',
      ),
      { params: Promise.resolve({ id: 'bp-1' }) },
    );
    expect(
      new URL(String(fetchMock.mock.calls[0][0])).searchParams.get('asOf'),
    ).toBe('2025-01-01');
  });
});
