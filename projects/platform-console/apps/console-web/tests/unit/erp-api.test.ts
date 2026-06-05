import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * `features/erp-ops/api/erp-api.ts` — the security-critical core of
 * TASK-PC-FE-010 (the FOURTH non-GAP federated domain; the FIRST
 * internal-system-primary confirmation; ADR-MONO-013 Phase 6).
 * STRICTLY READ-ONLY.
 *
 * THE CENTRAL ASSERTIONS (console-integration-contract § 2.4.8 —
 * REUSE of the § 2.4.5 per-domain credential rule, NOT re-derived;
 * same outcome as wms / scm / finance / the EXACT INVERSE of the
 * FE-002..006 GAP assertion):
 *
 *   - every erp call's bearer is the **GAP OIDC ACCESS token** (the
 *     `console_access_token` cookie), NEVER the exchanged operator
 *     token;
 *   - the operator-token path is ABSENT for erp (the erp client
 *     does NOT call `getOperatorToken()` — pinned so a future
 *     refactor cannot blanket-apply one domain's auth to all
 *     domains; the #569 invariant is GAP-domain-scoped and does NOT
 *     generalise to erp);
 *   - the console sends NO `X-Tenant-Id` (erp resolves tenant from
 *     the JWT `tenant_id ∈ {erp,*}` claim producer-side —
 *     tenant-model divergence reused from § 2.4.5);
 *   - EVERY call is a pure GET — NO mutation artifacts anywhere (no
 *     Idempotency-Key, no X-Operator-Reason, no body, no erp write,
 *     no v2 approval-service / read-model-service / future
 *     admin-service surface);
 *   - the erp FLAT error envelope
 *     `{ code, message, details?, timestamp }` is parsed (NOT
 *     wms's NESTED `{ error: { code } }`);
 *   - **NO 429 / Retry-After / backoff branch** (§ 2.4.8 — identical
 *     to finance § 2.4.7; honest difference from scm § 2.4.6 — erp
 *     has no documented 429; a stray 429 falls through as a
 *     surfaced `ApiError` with NO retry, NO Retry-After honour);
 *   - **E3 `?asOf=` thread-through** — when the caller supplies
 *     `asOf=<past>`, the producer client receives `asOf=<past>`
 *     verbatim (the CORE invariant the task pins);
 *   - 401 → ApiError(401) (whole-session re-login); 403 →
 *     ApiError(403) inline; 404 MASTERDATA_NOT_FOUND → ApiError
 *     inline; 503/timeout → ErpUnavailableError (section degrades
 *     only).
 *
 * `next/headers` cookies() + getServerEnv() mocked (FE-001..009
 * lane).
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

import * as sessionModule from '@/shared/lib/session';

import {
  listDepartments,
  getDepartmentById,
  listEmployees,
  getEmployeeById,
  listJobGrades,
  getJobGradeById,
  listCostCenters,
  getCostCenterById,
  listBusinessPartners,
  getBusinessPartnerById,
  // TASK-PC-FE-046 — department write PILOT.
  createDepartment,
  updateDepartment,
  retireDepartment,
  moveDepartmentParent,
  // TASK-PC-FE-048 — the other four masters' writes.
  createEmployee,
  createJobGrade,
  createCostCenter,
  createBusinessPartner,
  retireEmployee,
} from '@/features/erp-ops/api/erp-api';
import {
  ApiError,
  ErpUnavailableError,
} from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';
import {
  DepartmentSchema,
  EmployeeSchema,
  EffectivePeriodSchema,
} from '@/features/erp-ops/api/types';

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

function erpError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({
      code,
      message,
      timestamp: '2026-05-20T00:00:00.000Z',
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const DEPT_LIST_ENVELOPE = {
  data: [
    {
      id: 'dept-1',
      code: 'DEPT-001',
      name: 'Sales',
      parentId: null,
      status: 'ACTIVE',
      effectivePeriod: {
        effectiveFrom: '2026-01-01',
        effectiveTo: null,
      },
    },
    {
      id: 'dept-2',
      code: 'DEPT-002',
      name: 'Legacy',
      parentId: null,
      status: 'RETIRED',
      effectivePeriod: {
        effectiveFrom: '2025-01-01',
        effectiveTo: '2025-12-31',
      },
    },
  ],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

const DEPT_DETAIL_ENVELOPE = {
  data: {
    id: 'dept-1',
    code: 'DEPT-001',
    name: 'Sales',
    parentId: null,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    audit: {
      createdAt: '2026-01-01T00:00:00Z',
      createdBy: 'operator-1',
      updatedAt: '2026-01-01T00:00:00Z',
      updatedBy: 'operator-1',
    },
  },
  meta: { timestamp: 'x' },
};

const EMP_LIST_ENVELOPE = {
  data: [
    {
      id: 'emp-1',
      employeeNumber: 'EMP-001',
      name: '홍길동',
      departmentId: 'dept-1',
      jobGradeId: 'jg-1',
      costCenterId: 'cc-1',
      status: 'ACTIVE',
      employmentStatus: 'EMPLOYED',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
    {
      id: 'emp-2',
      employeeNumber: 'EMP-002',
      name: 'Jane',
      departmentId: 'dept-1',
      jobGradeId: 'jg-1',
      costCenterId: 'cc-1',
      status: 'ACTIVE',
      employmentStatus: 'SEPARATED',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
  ],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — GAP OIDC token, NEVER getOperatorToken()
// ===========================================================================

describe('erp-api — per-domain credential selection (REUSE of § 2.4.5; the INVERSE of #569)', () => {
  it('sends the GAP OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-erp');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(DEPT_LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listDepartments({});

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-erp',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain('http://erp.local/api/erp/masterdata/departments');
  });

  it('uses getDomainFacingToken() (net-zero → base GAP token) and NEVER getOperatorToken() for erp (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    // ADR-MONO-020 D4 / § 2.7: domain-facing token (assumed-when-switched,
    // else base) — STILL never the operator token.
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(EMP_LIST_ENVELOPE)),
    );

    await listEmployees({});

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for erp — same shape as the
    // FE-007/FE-008/FE-009 assertions; a future blanket-apply
    // refactor would break this.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the GAP session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await listDepartments({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (erp resolves tenant from the JWT claim — tenant-model divergence)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(DEPT_LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await listDepartments({});

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

// ===========================================================================
// 2. STRICTLY read-only — every call is a pure GET, no mutation artifacts.
// ===========================================================================

describe('erp-api — STRICTLY read-only (no mutation artifacts anywhere; § 2.4.8)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('every read is a pure GET with NO mutation artifacts', async () => {
    // Producer-side detail body — used for every detail leg
    // (department / employee / job-grade / cost-center /
    // business-partner). The detail schemas all share the same
    // common shape (code/name/status/effectivePeriod) so a single
    // body satisfies all five detail parsers.
    const DETAIL_BODY = {
      data: {
        id: 'x',
        code: 'X',
        name: 'X',
        employeeNumber: 'EMP-X',
        partnerType: 'CUSTOMER',
        status: 'ACTIVE',
        effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
      },
      meta: { timestamp: 'x' },
    };
    // An empty list satisfies every list-response schema (the
    // `data: []` array bypasses the per-item shape check).
    const EMPTY_LIST = {
      data: [],
      meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
    };
    const calls: Array<[string, RequestInit]> = [];
    const fetchMock = vi.fn((u: string, init?: RequestInit) => {
      calls.push([String(u), init as RequestInit]);
      const url = new URL(String(u));
      // A list URL ends with `/{master}` (no trailing path
      // segment); a detail URL has `/{master}/{id}`.
      const isDetail = /\/(departments|employees|job-grades|cost-centers|business-partners)\/[^/]+$/
        .test(url.pathname);
      if (isDetail) {
        return Promise.resolve(jsonResponse(DETAIL_BODY));
      }
      return Promise.resolve(jsonResponse(EMPTY_LIST));
    });
    vi.stubGlobal('fetch', fetchMock);

    // Exercise all 10 read functions.
    await listDepartments({});
    await getDepartmentById('dept-1');
    await listEmployees({});
    await getEmployeeById('emp-1');
    await listJobGrades({});
    await getJobGradeById('jg-1');
    await listCostCenters({});
    await getCostCenterById('cc-1');
    await listBusinessPartners({});
    await getBusinessPartnerById('bp-1');

    expect(calls.length).toBe(10);
    for (const [, init] of calls) {
      const h = init.headers as Record<string, string>;
      expect(init.method).toBe('GET');
      expect(init.body).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Content-Type']).toBeUndefined();
    }
    // The api module exports the 10 masterdata read functions + 2
    // read-model read functions (TASK-PC-FE-049) PLUS the write
    // functions for ALL FIVE masters (TASK-PC-FE-046 department pilot →
    // TASK-PC-FE-048 the other four). Each master has create/update/retire;
    // department additionally has move-parent (hierarchy-specific). No v2
    // approval-service / future admin-service surface; read-model is
    // read-only (E5 — no write function).
    const mod = await import('@/features/erp-ops/api/erp-api');
    const exported = Object.keys(mod).sort();
    expect(exported).toEqual(
      [
        // 10 masterdata reads
        'getBusinessPartnerById',
        'getCostCenterById',
        'getDepartmentById',
        'getEmployeeById',
        'getJobGradeById',
        'listBusinessPartners',
        'listCostCenters',
        'listDepartments',
        'listEmployees',
        'listJobGrades',
        // TASK-PC-FE-049: 2 read-model reads (READ-ONLY, no write)
        'listEmployeeOrgViews',
        'getEmployeeOrgView',
        // department writes (+ move-parent)
        'createDepartment',
        'updateDepartment',
        'retireDepartment',
        'moveDepartmentParent',
        // employee writes
        'createEmployee',
        'updateEmployee',
        'retireEmployee',
        // job-grade writes
        'createJobGrade',
        'updateJobGrade',
        'retireJobGrade',
        // cost-center writes
        'createCostCenter',
        'updateCostCenter',
        'retireCostCenter',
        // business-partner writes
        'createBusinessPartner',
        'updateBusinessPartner',
        'retireBusinessPartner',
      ].sort(),
    );
    // Every master has create + update + retire (department also move-parent).
    for (const master of [
      'Department',
      'Employee',
      'JobGrade',
      'CostCenter',
      'BusinessPartner',
    ]) {
      expect(exported).toContain(`create${master}`);
      expect(exported).toContain(`update${master}`);
      expect(exported).toContain(`retire${master}`);
    }
    expect(exported).toContain('moveDepartmentParent');
  });

  it('the proxy directory: every master exposes create/update/retire POST routes; no PUT/PATCH/DELETE handlers', async () => {
    const proxyRoot = path.resolve(__dirname, '../../src/app/api/erp');
    function walk(p: string): string[] {
      const out: string[] = [];
      for (const name of readdirSync(p)) {
        const full = path.join(p, name);
        if (statSync(full).isDirectory()) out.push(...walk(full));
        else out.push(full);
      }
      return out;
    }
    const tsFiles = walk(proxyRoot).filter((f) => f.endsWith('.ts'));
    expect(tsFiles.length).toBeGreaterThan(0);

    let postRouteFiles = 0;
    let getRouteFiles = 0;
    for (const f of tsFiles) {
      const src = readFileSync(f, 'utf8');
      if (path.basename(f) === '_proxy.ts') continue;
      // Same-origin handlers are GET or POST only — the upstream PATCH on
      // update is set inside the api fn, never a PATCH route handler. PUT /
      // DELETE are never used.
      expect(src).not.toMatch(/export\s+async\s+function\s+PUT\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+DELETE\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+PATCH\b/);
      if (/export\s+async\s+function\s+POST\b/.test(src)) postRouteFiles += 1;
      if (/export\s+async\s+function\s+GET\b/.test(src)) getRouteFiles += 1;
    }
    // 5 masters × {list GET, detail GET} = 10 GET route files (masterdata)
    // + TASK-PC-FE-049: 2 read-model GET routes = 12
    // + TASK-PC-FE-051: 3 approval GET routes (requests list, requests/[id]
    //   detail, inbox) = 15
    // + TASK-PC-FE-052: 2 notification GET routes (notifications inbox,
    //   notifications/[id] detail) = 17 total GET route files.
    expect(getRouteFiles).toBe(17);
    // POST routes: 5 masters × {create on list route, update on [id] route,
    // retire on [id]/retire route} = 15, + department move-parent = 16
    // + TASK-PC-FE-051: 2 approval POST routes (requests create, the
    //   [id]/[transition] dynamic transition route) = 18
    // + TASK-PC-FE-052: 1 notification POST route (notifications/[id]/read
    //   idempotent mark-read) = 19.
    expect(postRouteFiles).toBe(19);
  });
});

// ===========================================================================
// 2b. Department WRITE PILOT (TASK-PC-FE-046 / § 2.4.8 *Department write
//     binding (PILOT)*) — method/headers/body wiring + credential reuse.
// ===========================================================================

describe('erp-api — department WRITE PILOT (§ 2.4.8 Department write binding)', () => {
  const MUTATION_BODY = {
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
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
    const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
    return {
      url: String(url),
      init: init as RequestInit,
      headers: (init as RequestInit).headers as Record<string, string>,
      body: JSON.parse(String((init as RequestInit).body ?? '{}')),
    };
  }

  it('create → POST + Idempotency-Key + body; GAP OIDC token, NEVER operator token / X-Operator-Reason / X-Tenant-Id', async () => {
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY, 201));
    vi.stubGlobal('fetch', fetchMock);

    const result = await createDepartment(
      { code: 'DEPT-001', name: 'Sales', parentId: null },
      'idem-create-1',
    );
    expect(DepartmentSchema.parse(result).code).toBe('DEPT-001');

    const { url, init, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/masterdata/departments');
    expect(headers['Idempotency-Key']).toBe('idem-create-1');
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(body).toMatchObject({ code: 'DEPT-001', name: 'Sales' });
    // Credential reuse: the domain-facing GAP token, NEVER the
    // operator token (the #569 invariant is GAP-domain-scoped).
    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('update → upstream PATCH + Idempotency-Key; no reason header (no producer slot)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY));
    vi.stubGlobal('fetch', fetchMock);

    await updateDepartment('dept-1', { name: 'Sales (renamed)' }, 'idem-upd-1');

    const { url, init, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('PATCH');
    expect(url).toBe('http://erp.local/api/erp/masterdata/departments/dept-1');
    expect(headers['Idempotency-Key']).toBe('idem-upd-1');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(body).toMatchObject({ name: 'Sales (renamed)' });
  });

  it('retire → POST .../retire with reason in BODY (producer slot), Idempotency-Key, no reason header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY));
    vi.stubGlobal('fetch', fetchMock);

    await retireDepartment('dept-1', { reason: '조직 개편' }, 'idem-ret-1');

    const { url, init, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe(
      'http://erp.local/api/erp/masterdata/departments/dept-1/retire',
    );
    expect(headers['Idempotency-Key']).toBe('idem-ret-1');
    // reason lives in the BODY (producer slot), never the header.
    expect(body.reason).toBe('조직 개편');
    expect(headers['X-Operator-Reason']).toBeUndefined();
  });

  it('move-parent → POST .../move-parent with newParentId/effectiveFrom/reason in body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY));
    vi.stubGlobal('fetch', fetchMock);

    await moveDepartmentParent(
      'dept-1',
      { newParentId: 'dept-9', effectiveFrom: '2026-07-01', reason: '재배치' },
      'idem-move-1',
    );

    const { url, init, headers, body } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe(
      'http://erp.local/api/erp/masterdata/departments/dept-1/move-parent',
    );
    expect(headers['Idempotency-Key']).toBe('idem-move-1');
    expect(body).toMatchObject({
      newParentId: 'dept-9',
      effectiveFrom: '2026-07-01',
      reason: '재배치',
    });
  });

  it('surfaces the mutation-only producer errors (409 duplicate / 422 effective-period / 403 permission) as ApiError', async () => {
    for (const [code, status] of [
      ['MASTERDATA_DUPLICATE_KEY', 409],
      ['MASTERDATA_EFFECTIVE_PERIOD_INVALID', 422],
      ['PERMISSION_DENIED', 403],
    ] as const) {
      const fetchMock = vi.fn().mockResolvedValue(erpError(code, status));
      vi.stubGlobal('fetch', fetchMock);
      await expect(
        createDepartment({ code: 'D', name: 'N' }, 'k'),
      ).rejects.toBeInstanceOf(ApiError);
    }
  });
});

// ===========================================================================
// 2c. The OTHER FOUR masters WRITE (TASK-PC-FE-048) — method/path/idempotency
//     + credential reuse (the shared callErp wire mechanics are already pinned
//     by the department write describe; this guards the per-master fns/paths).
// ===========================================================================

describe('erp-api — employees/job-grades/cost-centers/business-partners WRITE (§ 2.4.8)', () => {
  const MUTATION_BODY = {
    data: {
      id: 'm-1',
      code: 'C-1',
      employeeNumber: 'EMP-1',
      name: 'X',
      partnerType: 'CUSTOMER',
      status: 'ACTIVE',
      effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
    },
    meta: { timestamp: 'x' },
  };

  beforeEach(() => {
    cookieJar.clear();
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
  });

  function lastCall(fetchMock: ReturnType<typeof vi.fn>) {
    const [url, init] = fetchMock.mock.calls[fetchMock.mock.calls.length - 1];
    return {
      url: String(url),
      init: init as RequestInit,
      headers: (init as RequestInit).headers as Record<string, string>,
    };
  }

  it('create on each master → POST to its collection path + Idempotency-Key; GAP token, no X-Operator-Reason', async () => {
    const cases: Array<[() => Promise<unknown>, string]> = [
      [
        () =>
          createEmployee(
            { employeeNumber: 'EMP-1', name: '홍길동' },
            'idem-emp',
          ),
        'http://erp.local/api/erp/masterdata/employees',
      ],
      [
        () => createJobGrade({ code: 'G3', name: '사원' }, 'idem-jg'),
        'http://erp.local/api/erp/masterdata/job-grades',
      ],
      [
        () => createCostCenter({ code: 'CC-1', name: '영업CC' }, 'idem-cc'),
        'http://erp.local/api/erp/masterdata/cost-centers',
      ],
      [
        () =>
          createBusinessPartner(
            { code: 'BP-1', name: 'ACME', partnerType: 'CUSTOMER' },
            'idem-bp',
          ),
        'http://erp.local/api/erp/masterdata/business-partners',
      ],
    ];
    for (const [call, expectedUrl] of cases) {
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY, 201));
      vi.stubGlobal('fetch', fetchMock);
      await call();
      const { url, init, headers } = lastCall(fetchMock);
      expect(init.method).toBe('POST');
      expect(url).toBe(expectedUrl);
      expect(headers['Idempotency-Key']).toBeTruthy();
      expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
      expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
      expect(headers['X-Operator-Reason']).toBeUndefined();
      expect(headers['X-Tenant-Id']).toBeUndefined();
    }
  });

  it('retire → POST .../{id}/retire with reason in BODY (producer slot), no reason header', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(MUTATION_BODY));
    vi.stubGlobal('fetch', fetchMock);
    await retireEmployee('emp-1', '퇴직', 'idem-ret');
    const { url, init, headers } = lastCall(fetchMock);
    expect(init.method).toBe('POST');
    expect(url).toBe('http://erp.local/api/erp/masterdata/employees/emp-1/retire');
    expect(headers['Idempotency-Key']).toBe('idem-ret');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(JSON.parse(String(init.body)).reason).toBe('퇴직');
  });
});

// ===========================================================================
// 3. E3 asOf thread-through — the CORE erp UX invariant.
// ===========================================================================

describe('erp-api — E3 `?asOf=` thread-through (§ 2.4.8 CORE invariant)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('threads `asOf=<past>` through to every LIST endpoint verbatim', async () => {
    // Producer-side: list envelope shape — same for every master
    // (data: []). An empty data array satisfies every list parser.
    const EMPTY_LIST = {
      data: [],
      meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
    };
    // Fresh Response per call (Response bodies are one-shot).
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(EMPTY_LIST)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await listDepartments({ asOf: '2025-01-01' });
    await listEmployees({ asOf: '2025-01-01' });
    await listJobGrades({ asOf: '2025-01-01' });
    await listCostCenters({ asOf: '2025-01-01' });
    await listBusinessPartners({ asOf: '2025-01-01' });

    expect(fetchMock).toHaveBeenCalledTimes(5);
    for (const [url] of fetchMock.mock.calls) {
      const u = new URL(String(url));
      expect(u.searchParams.get('asOf')).toBe('2025-01-01');
    }
  });

  it('threads `asOf=<past>` through to every DETAIL endpoint verbatim', async () => {
    // Producer-side: a detail body shape compatible with every
    // master detail schema (they all extend a common
    // `{ id, code, name, status, effectivePeriod }` shape and
    // tolerate passthrough fields).
    const DETAIL_BODY = {
      data: {
        id: 'x',
        code: 'X',
        name: 'X',
        employeeNumber: 'EMP-X',
        partnerType: 'CUSTOMER',
        status: 'ACTIVE',
        effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
      },
      meta: { timestamp: 'x' },
    };
    // Fresh Response per call (Response bodies are one-shot).
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(DETAIL_BODY)),
    );
    vi.stubGlobal('fetch', fetchMock);

    await getDepartmentById('dept-1', { asOf: '2025-01-01' });
    await getEmployeeById('emp-1', { asOf: '2025-01-01' });
    await getJobGradeById('jg-1', { asOf: '2025-01-01' });
    await getCostCenterById('cc-1', { asOf: '2025-01-01' });
    await getBusinessPartnerById('bp-1', { asOf: '2025-01-01' });

    expect(fetchMock).toHaveBeenCalledTimes(5);
    for (const [url] of fetchMock.mock.calls) {
      const u = new URL(String(url));
      expect(u.searchParams.get('asOf')).toBe('2025-01-01');
    }
  });

  it('rendered state matches the asOf-instant response (not current state)', async () => {
    // Producer-side: at asOf=2025-06-01 the department was ACTIVE
    // ("historical state"); current state (2026-) is RETIRED. The
    // test feeds the asOf-instant body and asserts the client
    // returns it verbatim (NO current-state substitution — the core
    // E3 defect to avoid).
    const asOfBody = {
      data: {
        id: 'dept-x',
        code: 'DEPT-X',
        name: 'Historical Sales',
        parentId: null,
        status: 'ACTIVE', // active at the asOf instant
        effectivePeriod: {
          effectiveFrom: '2025-01-01',
          effectiveTo: '2025-12-31',
        },
      },
      meta: { timestamp: '2025-06-01T00:00:00Z' },
    };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(asOfBody));
    vi.stubGlobal('fetch', fetchMock);

    const dept = await getDepartmentById('dept-x', { asOf: '2025-06-01' });

    // The asOf threaded through verbatim.
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.searchParams.get('asOf')).toBe('2025-06-01');
    // The rendered state matches the asOf-instant response — the
    // department was ACTIVE at that instant (current state is
    // irrelevant).
    expect(dept.status).toBe('ACTIVE');
    expect(dept.name).toBe('Historical Sales');
    expect(dept.effectivePeriod.effectiveTo).toBe('2025-12-31');
  });

  it('omits `asOf` from the query when not supplied (producer resolves to today UTC)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(DEPT_LIST_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);
    await listDepartments({});
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.searchParams.get('asOf')).toBeNull();
  });
});

// ===========================================================================
// 4. Confidential / audit-heavy — no token / PII / financial / sensitive logging
// ===========================================================================

describe('erp-api — confidential + audit-heavy (no token / PII / financial / sensitive logging; § 2.4.8)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('logs neither the token nor the response body (success path)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(EMP_LIST_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await listEmployees({});

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    expect(all).not.toContain('GAP-OIDC-ACCESS');
    // Employee PII — names / contact / employeeNumber must NEVER
    // appear in any log line.
    expect(all).not.toContain('홍길동');
    expect(all).not.toContain('EMP-001');
    expect(all).not.toContain('emp-1');
  });

  it('logs no business-partner financial details on a business-partners read', async () => {
    const BP_ENVELOPE = {
      data: [
        {
          id: 'bp-secret',
          code: 'BP-CONFIDENTIAL',
          name: 'ACME Corp',
          partnerType: 'CUSTOMER',
          paymentTerms: { termDays: 30, method: 'BANK_TRANSFER' },
          status: 'ACTIVE',
          effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
        },
      ],
      meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(BP_ENVELOPE)));
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await listBusinessPartners({});

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    expect(all).not.toContain('GAP-OIDC-ACCESS');
    // Financial details + identifiers MUST NOT appear in logs.
    expect(all).not.toContain('BP-CONFIDENTIAL');
    expect(all).not.toContain('ACME Corp');
    expect(all).not.toContain('BANK_TRANSFER');
    // Payment-terms structure must not leak. The bare value `30` (termDays)
    // is NOT a sound probe — `30` appears incidentally inside the log line's
    // ISO `ts` timestamp and the random `requestId` UUID, so asserting on it
    // is timing-flaky (GREEN locally, RED in CI). Probe the distinctive
    // payment-terms keys instead, which only appear if the body leaks.
    expect(all).not.toContain('termDays');
    expect(all).not.toContain('paymentTerms');
    expect(all).not.toContain('bp-secret');
  });

  it('logs no record id on a detail read (sanitised path uses `{id}` placeholder)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(DEPT_DETAIL_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getDepartmentById('dept-secret-id');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    expect(all).not.toContain('dept-secret-id');
    // The sanitised path placeholder IS present (proves the
    // logger ran and used the literal `{id}` placeholder).
    expect(all).toContain('/api/erp/masterdata/departments/{id}');
  });
});

// ===========================================================================
// 5. E2 effective-period + honest enum surfacing (RETIRED + SEPARATED;
//    unknown → generic label, no throw)
// ===========================================================================

describe('erp-api / types — honest enum surfacing + tolerant parsing (§ 2.4.8)', () => {
  it('a RETIRED department status parses without throwing (surfaced honestly downstream)', () => {
    expect(() =>
      DepartmentSchema.parse({
        id: 'd',
        code: 'D',
        name: 'X',
        status: 'RETIRED',
        effectivePeriod: { effectiveFrom: '2025-01-01', effectiveTo: '2025-12-31' },
      }),
    ).not.toThrow();
  });

  it('a SEPARATED employee parses without throwing (surfaced honestly downstream)', () => {
    expect(() =>
      EmployeeSchema.parse({
        id: 'e',
        employeeNumber: 'E',
        name: 'X',
        status: 'ACTIVE',
        employmentStatus: 'SEPARATED',
        effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
      }),
    ).not.toThrow();
  });

  it('an unknown / future master status parses without throwing (generic label downstream)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            ...DEPT_DETAIL_ENVELOPE.data,
            status: 'FUTURE_LIMBO_STATE',
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const d = await getDepartmentById('dept-1');
    expect(d.status).toBe('FUTURE_LIMBO_STATE');
  });

  it('an unknown / future employment status parses without throwing', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            {
              id: 'emp-9',
              employeeNumber: 'EMP-009',
              name: 'X',
              status: 'ACTIVE',
              employmentStatus: 'FUTURE_LEAVE_TYPE',
              effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
            },
          ],
          meta: { page: 0, size: 20, totalElements: 1 },
        }),
      ),
    );
    const r = await listEmployees({});
    expect(r.data[0].employmentStatus).toBe('FUTURE_LEAVE_TYPE');
  });

  it('EffectivePeriod requires effectiveFrom; effectiveTo is nullable (E2 shape)', () => {
    // Active (effectiveTo: null) parses.
    expect(() =>
      EffectivePeriodSchema.parse({ effectiveFrom: '2026-01-01', effectiveTo: null }),
    ).not.toThrow();
    // Retired (effectiveTo: past) parses.
    expect(() =>
      EffectivePeriodSchema.parse({
        effectiveFrom: '2025-01-01',
        effectiveTo: '2025-12-31',
      }),
    ).not.toThrow();
    // Missing effectiveFrom fails.
    const r = EffectivePeriodSchema.safeParse({ effectiveTo: null });
    expect(r.success).toBe(false);
  });
});

// ===========================================================================
// 6. erp FLAT error envelope (NOT wms NESTED) + § 2.5 mapping.
//    NO 429 / Retry-After branch is taken (erp has no documented 429).
// ===========================================================================

describe('erp-api — erp FLAT error envelope (NOT wms NESTED) + § 2.5 + no-429-branch', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) — whole-session re-login (no partial authed state)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('UNAUTHORIZED', 401)),
    );
    const err = await listDepartments({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('TENANT_FORBIDDEN', 403)),
    );
    const err = await listEmployees({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('403 DATA_SCOPE_FORBIDDEN → ApiError(403) inline (E6 data-scope rejection)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('DATA_SCOPE_FORBIDDEN', 403)),
    );
    const err = await getEmployeeById('emp-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('DATA_SCOPE_FORBIDDEN');
  });

  it('403 EXTERNAL_TRAFFIC_REJECTED → ApiError(403) inline (E7 internal-only boundary)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('EXTERNAL_TRAFFIC_REJECTED', 403)),
    );
    const err = await listDepartments({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('EXTERNAL_TRAFFIC_REJECTED');
  });

  it('404 MASTERDATA_NOT_FOUND → ApiError(404) inline actionable (the erp v1 reality)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('MASTERDATA_NOT_FOUND', 404)),
    );
    const err = await getDepartmentById('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    // The code comes from the FLAT top-level `code`, NOT a nested
    // `error.code` — proves the erp-shape parser (a wms-nested
    // parser would yield the synthetic HTTP_404).
    expect(err.code).toBe('MASTERDATA_NOT_FOUND');
  });

  it('a wms-NESTED { error: { code } } body is NOT mis-parsed as erp (no accidental cross-wire)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            error: { code: 'WMS_NESTED_SHAPE', message: 'x' },
          }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const err = await listEmployees({}).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    // The erp parser reads the FLAT top-level `code` — a
    // wms-nested body has none, so it degrades to the synthetic
    // fallback (NOT 'WMS_NESTED_SHAPE') and never crashes. Pins
    // per-domain envelope correctness — each domain owns its own
    // parser even when the wire shape is identical.
    expect(err.code).toBe('HTTP_422');
  });

  it('503 → ErpUnavailableError (ONLY the erp section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(erpError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listDepartments({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → ErpUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await listEmployees({}).catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await getDepartmentById('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404'); // synthetic fallback, no throw
  });

  // The HEADLINE no-429 assertion (identical to finance § 2.4.7 —
  // erp has no documented 429). A 429 from erp MUST fail-fast
  // through the default-error path (a surfaced ApiError), NOT
  // through a Retry-After / backoff branch. Exactly ONE fetch is
  // made (no retry / no storm).
  it('NO 429 handling path exists for erp — a stray 429 surfaces as a generic ApiError, NO retry, NO Retry-After honour', async () => {
    let n = 0;
    const fetchMock = vi.fn(() => {
      n += 1;
      return Promise.resolve(
        new Response(
          JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
          {
            status: 429,
            headers: {
              'Content-Type': 'application/json',
              'Retry-After': '1', // a Retry-After header the client must IGNORE
            },
          },
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const err = await listDepartments({}).catch((e) => e);
    // The client surfaced the 429 as a generic ApiError (NOT a
    // bounded-backoff ScmRateLimitedError sibling, NOT a retried
    // success). The presence of an `ApiError(429)` rather than a
    // domain-specific RateLimitedError is itself the assertion.
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    // EXACTLY ONE fetch — no retry, no Retry-After honour, no
    // storm (the precise "no 429 handling" invariant — § 2.4.8).
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });

  it('the erp source carries NO 429/Retry-After handling code (grep-asserted)', () => {
    const apiSrc = readFileSync(
      path.resolve(__dirname, '../../src/features/erp-ops/api/erp-api.ts'),
      'utf8',
    );
    // Strip block-comment + line-comment content so we only look at
    // executable code. The doc-comments narrate WHY there is no
    // 429 path — they are allowed to mention 429 / Retry-After.
    //
    // CRLF-safe: normalise `\r\n` → `\n` BEFORE stripping so that the
    // line-comment regex's `.*$` (which does not match `\r`) reaches
    // the end-of-line anchor reliably on Windows checkouts. Without
    // this, a line-comment with a trailing `\r` survives the strip
    // and trips the `Retry-After` assertion below (TASK-PC-FE-012).
    const stripped = apiSrc
      .replace(/\r\n/g, '\n')
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .replace(/\/\/.*$/gm, '');
    // Line-ending regression guard — if a `\r` survives the strip,
    // the assertions below would silently false-pass on a
    // different CRLF case in the future.
    expect(stripped).not.toMatch(/\r/);
    expect(/\bRetry-After\b/i.test(stripped)).toBe(false);
    expect(/\b429\b/.test(stripped)).toBe(false);
    expect(/RateLimited/.test(stripped)).toBe(false);
  });
});
