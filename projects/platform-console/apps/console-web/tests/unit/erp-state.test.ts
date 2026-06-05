import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/erp-ops/api/erp-state.ts` — the server-side section
 * state (TASK-PC-FE-010 / § 2.4.8):
 *   - not erp-eligible → `notEligible` block, NO erp call
 *     fabricated (no cross-tenant call; the console never sends a
 *     tenant — erp resolves it from the JWT claim);
 *   - eligible → seeds first-page snapshots of every master
 *     (departments / employees / job-grades / cost-centers /
 *     business-partners) in parallel (GAP OIDC token, server-side);
 *   - eligible + asOf → asOf threads through to every leg
 *     verbatim (the CORE E3 invariant);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 503 / timeout → `degraded` (finance section only — shell
 *     intact);
 *   - **no 429 / Retry-After branch** — a stray 429 lands as a
 *     generic ApiError → `degraded` (no fabricated backoff path).
 *
 * 401 → whole-session re-login (redirect) is exercised here too.
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

import {
  getErpSectionState,
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

describe('getErpSectionState — eligibility + asOf thread-through (§ 2.4.8)', () => {
  it('not eligible → notEligible block, NO erp call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getErpSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.departments).toBeNull();
    expect(state.employees).toBeNull();
    expect(state.jobGrades).toBeNull();
    expect(state.costCenters).toBeNull();
    expect(state.businessPartners).toBeNull();
    // TASK-PC-FE-049: org-view is also null when not eligible.
    expect(state.employeeOrgViews).toBeNull();
    // TASK-PC-FE-051: approval legs also absent (no fabricated call).
    expect(state.approvalRequests).toBeNull();
    expect(state.approvalInbox).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds all 5 masters + read-model org-view + approval legs in parallel (GAP OIDC token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // Each call must return a FRESH Response (Response bodies are
    // one-shot; the parallel legs would otherwise share a consumed
    // body and the second leg onward would throw).
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);

    const state = await getErpSectionState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.departments).not.toBeNull();
    expect(state.employees).not.toBeNull();
    expect(state.jobGrades).not.toBeNull();
    expect(state.costCenters).not.toBeNull();
    expect(state.businessPartners).not.toBeNull();
    // TASK-PC-FE-049: read-model org-view seeded.
    expect(state.employeeOrgViews).not.toBeNull();
    // TASK-PC-FE-051: approval requests + inbox seeded.
    expect(state.approvalRequests).not.toBeNull();
    expect(state.approvalInbox).not.toBeNull();
    // TASK-PC-FE-055: delegation-fact read-model leg seeded.
    expect(state.delegationFacts).not.toBeNull();
    // 9 legs: 5 masterdata + 1 read-model org-view (FE-049) + 2 approval
    // (FE-051 — requests list + inbox) + 1 delegation-fact read-model (FE-055).
    expect(fetchMock.mock.calls.length).toBe(9);
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h['X-Tenant-Id']).toBeUndefined();
    }
  });

  it('eligible + asOf → asOf threads through to every masterdata/read-model leg verbatim (E3 CORE invariant); approval legs are exempt (no asOf concept)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    // Fresh Response per call (one-shot body).
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(listEnv())),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getErpSectionState(true, '2025-01-01');
    // 9 legs total (6 asOf-bearing masterdata + 1 read-model org-view + 2 approval + 1 delegation-fact).
    // The delegation-fact leg does NOT thread asOf (it uses page/size only at seed; no asOf concept).
    expect(fetchMock.mock.calls.length).toBe(9);
    for (const [url] of fetchMock.mock.calls) {
      const u = new URL(String(url));
      if (u.pathname.includes('/approval/')) {
        // approval-service has no asOf (single-stage workflow, not an
        // effective-dated master read) — exempt from the E3 thread-through.
        expect(u.searchParams.get('asOf')).toBeNull();
      } else if (u.pathname.includes('/read-model/delegations')) {
        // TASK-PC-FE-055: delegation-fact read-model has no asOf at the seed level
        // (the delegation-fact list uses page/size filters; asOf is not a producer-
        // defined filter for this endpoint — it's not an effective-dated master).
        expect(u.searchParams.get('asOf')).toBeNull();
      } else {
        expect(u.searchParams.get('asOf')).toBe('2025-01-01');
      }
    }
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getErpSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (erp section only — shell + GAP/wms/scm/finance sections intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(erpError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getErpSectionState(true);
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
          headers: {
            'Content-Type': 'application/json',
            'Retry-After': '1',
          },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const state = await getErpSectionState(true);
    // A 429 lands as an ApiError, which erp-state treats as a
    // degrade (any-other-error → degrade), NOT a retry storm.
    expect(state.degraded).toBe(true);
    // Each leg attempted exactly once (no retry from a 429 honour;
    // 6 asOf legs + 2 approval legs + 1 delegation-fact leg = 9 fetches max —
    // TASK-PC-FE-049 adds the read-model leg, TASK-PC-FE-051 the 2 approval
    // legs, TASK-PC-FE-055 the delegation-fact leg).
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(9);
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
    const err = await getErpSectionState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
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
