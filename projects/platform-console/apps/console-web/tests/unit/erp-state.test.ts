import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/erp-ops/api/erp-state.ts` — the FOUR per-route state
 * loaders (TASK-PC-FE-010 surface; TASK-PC-FE-076 drill-in split).
 * The monolithic `getErpSectionState` (one page, 9 legs) is replaced
 * by 4 focused loaders, each fetching ONLY its route's slice:
 *   - `getErpMastersState`    — 5 masters (`/erp`);
 *   - `getErpOrgViewState`    — read-model org-view (`/erp/orgview`);
 *   - `getErpApprovalState`   — requests + inbox (`/erp/approval`);
 *   - `getErpDelegationState` — delegation facts (`/erp/delegation`).
 *
 * Per loader (§ 2.4.8 / § 2.5):
 *   - not erp-eligible → `notEligible` block, NO erp call fabricated
 *     (the console never sends a tenant — erp resolves it from the
 *     JWT claim);
 *   - eligible → seeds first-page snapshot(s) of its slice in
 *     parallel (IAM OIDC token, server-side);
 *   - eligible + asOf → asOf threads through verbatim (the CORE E3
 *     invariant) on the masters + org-view loaders; approval +
 *     delegation have no asOf concept;
 *   - 403 → `forbidden` (inline, no crash);
 *   - 503 / timeout → `degraded` (this route only — shell intact);
 *   - **no 429 / Retry-After branch** — a stray 429 lands as a
 *     generic ApiError → `degraded` (no fabricated backoff path).
 *   - 401 → whole-session re-login (redirect).
 *
 * Per-route degrade ISOLATION (the point of the split): an
 * approval-service outage degrades `/erp/approval` ONLY — the masters
 * route is unaffected (it does not call the approval legs at all).
 *
 * The queryKey factories (also exported from `erp-state.ts`) are
 * unit-tested separately to pin the key shape (which the cache
 * relies on for refetches on asOf / page / filter changes).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
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

import {
  getErpMastersState,
  getErpOrgViewState,
  getErpApprovalState,
  getErpDelegationState,
  departmentsListKey,
  departmentDetailKey,
  employeesListKey,
  employeeDetailKey,
  jobGradesListKey,
  jobGradeDetailKey,
  costCentersListKey,
  costCenterDetailKey,
  businessPartnersListKey,
  businessPartnerDetailKey,
  normaliseAsOf,
  ERP_KEY,
} from '@/features/erp-ops/api/erp-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function listEnv(extra: Record<string, unknown> = {}) {
  return {
    data: [],
    meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x', ...extra },
  };
}
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

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getErpMastersState — `/erp` masters slice (§ 2.4.8 / PC-FE-076)', () => {
  it('not eligible → notEligible block, NO erp call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getErpMastersState(false);
    expect(state.notEligible).toBe(true);
    expect(state.departments).toBeNull();
    expect(state.employees).toBeNull();
    expect(state.jobGrades).toBeNull();
    expect(state.costCenters).toBeNull();
    expect(state.businessPartners).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds ONLY the 5 masters in parallel (IAM OIDC token; no org-view/approval/delegation legs)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // Fresh Response per call (one-shot body).
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);

    const state = await getErpMastersState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.departments).not.toBeNull();
    expect(state.employees).not.toBeNull();
    expect(state.jobGrades).not.toBeNull();
    expect(state.costCenters).not.toBeNull();
    expect(state.businessPartners).not.toBeNull();
    // EXACTLY 5 legs — the masters route fetches ONLY its slice
    // (the old monolith made 9; PC-FE-076 isolates each route).
    expect(fetchMock.mock.calls.length).toBe(5);
    for (const [url, init] of fetchMock.mock.calls) {
      const u = new URL(String(url));
      // No approval / read-model legs on the masters route.
      expect(u.pathname).not.toContain('/approval/');
      expect(u.pathname).not.toContain('/read-model/');
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h['X-Tenant-Id']).toBeUndefined();
    }
  });

  it('eligible + asOf → asOf threads through to every master leg verbatim (E3 CORE invariant)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getErpMastersState(true, '2025-01-01');
    expect(fetchMock.mock.calls.length).toBe(5);
    for (const [url] of fetchMock.mock.calls) {
      expect(new URL(String(url)).searchParams.get('asOf')).toBe('2025-01-01');
    }
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getErpMastersState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (erp masters route only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getErpMastersState(true);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('a stray 429 (no documented erp 429) → degraded (NOT a fabricated backoff)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/json', 'Retry-After': '1' },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpMastersState(true);
    expect(state.degraded).toBe(true);
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(5);
    for (const [, init] of fetchMock.mock.calls) {
      expect((init as RequestInit).method).toBe('GET');
    }
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('UNAUTHORIZED', 401))),
    );
    const err = await getErpMastersState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});

describe('getErpOrgViewState — `/erp/orgview` read-model slice (PC-FE-076)', () => {
  it('not eligible → block, NO call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpOrgViewState(false);
    expect(state.notEligible).toBe(true);
    expect(state.employeeOrgViews).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds ONLY the read-model org-view leg (1 fetch); asOf threads through', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpOrgViewState(true, '2025-01-01');
    expect(state.employeeOrgViews).not.toBeNull();
    expect(state.degraded).toBe(false);
    expect(fetchMock.mock.calls.length).toBe(1);
    const u = new URL(String(fetchMock.mock.calls[0]![0]));
    expect(u.pathname).toContain('/read-model/employees');
    expect(u.searchParams.get('asOf')).toBe('2025-01-01');
  });

  it('503 → degraded (the read-model leg IS this route degrade authority now — no longer best-effort)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getErpOrgViewState(true);
    expect(state.degraded).toBe(true);
    expect(state.employeeOrgViews).toBeNull();
  });

  it('403 → forbidden', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getErpOrgViewState(true);
    expect(state.forbidden).toBe(true);
  });
});

describe('getErpApprovalState — `/erp/approval` slice (PC-FE-076)', () => {
  it('not eligible → block, NO call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpApprovalState(false);
    expect(state.notEligible).toBe(true);
    expect(state.approvalRequests).toBeNull();
    expect(state.approvalInbox).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds ONLY the 2 approval legs (requests + inbox); no asOf concept', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpApprovalState(true);
    expect(state.approvalRequests).not.toBeNull();
    expect(state.approvalInbox).not.toBeNull();
    expect(fetchMock.mock.calls.length).toBe(2);
    for (const [url] of fetchMock.mock.calls) {
      const u = new URL(String(url));
      expect(u.pathname).toContain('/approval/');
      // approval is not effective-dated — never carries asOf.
      expect(u.searchParams.get('asOf')).toBeNull();
    }
  });

  it('503 → degraded (the approval legs ARE this route degrade authority now)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getErpApprovalState(true);
    expect(state.degraded).toBe(true);
  });
});

describe('getErpDelegationState — `/erp/delegation` slice (PC-FE-076)', () => {
  it('not eligible → block, NO call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpDelegationState(false);
    expect(state.notEligible).toBe(true);
    expect(state.delegationFacts).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds ONLY the delegation-fact read-model leg (1 fetch)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpDelegationState(true);
    expect(state.delegationFacts).not.toBeNull();
    expect(fetchMock.mock.calls.length).toBe(1);
    expect(
      new URL(String(fetchMock.mock.calls[0]![0])).pathname,
    ).toContain('/read-model/delegations');
  });

  it('503 → degraded', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getErpDelegationState(true);
    expect(state.degraded).toBe(true);
  });
});

describe('per-route degrade ISOLATION (the point of the PC-FE-076 split)', () => {
  it('an approval-service outage degrades the approval route ONLY — masters/orgview/delegation unaffected', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // Route by URL: ONLY the approval endpoints 503; everything else 200.
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/approval/')) {
        return Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503));
      }
      return Promise.resolve(jsonResponse(listEnv()));
    });
    vi.stubGlobal('fetch', fetchMock);

    const [masters, orgview, approval, delegation] = await Promise.all([
      getErpMastersState(true),
      getErpOrgViewState(true),
      getErpApprovalState(true),
      getErpDelegationState(true),
    ]);

    // Approval is the only degraded route.
    expect(approval.degraded).toBe(true);
    // The other three routes are healthy — under the old single page a
    // down approval-service was swallowed; now it is cleanly isolated.
    expect(masters.degraded).toBe(false);
    expect(masters.departments).not.toBeNull();
    expect(orgview.degraded).toBe(false);
    expect(orgview.employeeOrgViews).not.toBeNull();
    expect(delegation.degraded).toBe(false);
    expect(delegation.delegationFacts).not.toBeNull();
  });
});

// ===========================================================================
// queryKey factories — every key depends on `asOf` so a URL-param
// change triggers refetch.
// ===========================================================================

describe('erp-state — queryKey factories (asOf binding pins refetch on URL change)', () => {
  it('exposes a stable `ERP_KEY` namespace and per-master factories', () => {
    expect(ERP_KEY).toBe('erp-ops');
    // Sanity: a fixed input → a stable key.
    const k1 = departmentsListKey(undefined, 0, 20);
    const k2 = departmentsListKey(undefined, 0, 20);
    expect(k1).toEqual(k2);
  });

  it('queryKey changes when asOf changes (refetch fires)', () => {
    const a = departmentsListKey(undefined, 0, 20);
    const b = departmentsListKey('2025-01-01', 0, 20);
    expect(a).not.toEqual(b);
    expect(JSON.stringify(a)).not.toEqual(JSON.stringify(b));
  });

  it('detail queryKey binds id + asOf', () => {
    const a = departmentDetailKey('dept-1', undefined);
    const b = departmentDetailKey('dept-1', '2025-01-01');
    const c = departmentDetailKey('dept-2', undefined);
    expect(a).not.toEqual(b);
    expect(a).not.toEqual(c);
  });

  it('every master factory follows the same shape (binds asOf)', () => {
    expect(employeesListKey(undefined, 0, 20)).not.toEqual(
      employeesListKey('2025-01-01', 0, 20),
    );
    expect(employeeDetailKey('e-1', undefined)).not.toEqual(
      employeeDetailKey('e-1', '2025-01-01'),
    );
    expect(jobGradesListKey(undefined, 0, 20)).not.toEqual(
      jobGradesListKey('2025-01-01', 0, 20),
    );
    expect(jobGradeDetailKey('j-1', undefined)).not.toEqual(
      jobGradeDetailKey('j-1', '2025-01-01'),
    );
    expect(costCentersListKey(undefined, 0, 20)).not.toEqual(
      costCentersListKey('2025-01-01', 0, 20),
    );
    expect(costCenterDetailKey('c-1', undefined)).not.toEqual(
      costCenterDetailKey('c-1', '2025-01-01'),
    );
    expect(businessPartnersListKey(undefined, 0, 20)).not.toEqual(
      businessPartnersListKey('2025-01-01', 0, 20),
    );
    expect(businessPartnerDetailKey('b-1', undefined)).not.toEqual(
      businessPartnerDetailKey('b-1', '2025-01-01'),
    );
  });

  it('normaliseAsOf trims whitespace and turns "" into undefined', () => {
    expect(normaliseAsOf(null)).toBeUndefined();
    expect(normaliseAsOf(undefined)).toBeUndefined();
    expect(normaliseAsOf('')).toBeUndefined();
    expect(normaliseAsOf('  ')).toBeUndefined();
    expect(normaliseAsOf('2025-01-01')).toBe('2025-01-01');
    expect(normaliseAsOf('  2025-01-01  ')).toBe('2025-01-01');
  });
});
