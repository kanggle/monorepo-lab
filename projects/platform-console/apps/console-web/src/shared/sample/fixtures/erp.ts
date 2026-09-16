import { SAMPLE_AS_OF, SAMPLE_LABEL_SUFFIX } from '../codes';
import { fixtureNotFound } from '../router';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read — see `iam.ts`/`ecommerce.ts`'s identical note).
 */
type ErpFixtureHandler = (path: string) => unknown;

/**
 * erp domain fixtures (TASK-PC-FE-285 — `ADR-MONO-074` execution 4/8): the 3
 * `callFlatEnvelopeGateway` surfaces —
 *   - `erp`            — masterdata (5 masters × list+detail) + read-model
 *                         employee org-view (list+detail) + read-model
 *                         delegation-facts (list+detail). All GET, all
 *                         server-side `getDomainFacingToken()`.
 *   - `erp_approval`    — approval-service requests (list+detail) + the
 *                          caller's inbox (list). Mutations (create/submit/
 *                          approve/reject/withdraw) are all non-GET, so the
 *                          router's generic `403 SAMPLE_READ_ONLY` branch
 *                          handles them — no fixture code needed (AC-5).
 *   - `erp_delegation`  — approval-service delegation GRANTS (list, filtered
 *                          by `role=DELEGATOR|DELEGATE`). create/revoke are
 *                          non-GET → the same generic 403 branch.
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Each handler is parsed by the SAME zod schema the real screen uses
 * (`tests/unit/sample-fixtures-schema-erp.test.ts`).
 *
 * R2ⓐ: human-readable strings (department/employee/job-grade/cost-center/
 * business-partner `name`, approval `title`) end with «(샘플)»; ids, codes,
 * enums, dates and actor references do not (`shared/sample/label-rule.ts` —
 * see that file's TASK-PC-FE-285 additions for the new keys this file
 * introduces). AC-7 (person-identifying values are obviously synthetic):
 * every employee name is an invented Korean name with the «(샘플)» suffix;
 * there is no employee email field on this surface (`EmployeeSchema` has
 * none — unlike IAM accounts / ecommerce users).
 *
 * ── ONE EMPLOYEE WORLD (AC-3 / AC-4) ─────────────────────────────────────
 * Every cross-reference below resolves inside this file's own arrays:
 *   - `department.parentId`, `employee.{departmentId,jobGradeId,costCenterId}`,
 *     `costCenter.departmentId` all resolve to a real row of the referenced
 *     master (`masterRefLabel` never falls back to the raw id — AC-4).
 *   - every approval's `approverId` / `submitterId` / stage `approverId`, and
 *     every EMPLOYEE-subject `subjectId`, is a real `EMPLOYEES` id (the
 *     "결재선의 결재자가 마스터 픽스처 안에서 해석된다" requirement — AC-4).
 *   - every delegation grant/fact's `delegatorId` / `delegateId` is a real
 *     `EMPLOYEES` id.
 *   - `employee-sample-0003`'s department (`dept-sample-0004`) is RETIRED —
 *     the headline E1 case the task spec calls out ("employee → retired
 *     department"), surfaced by `<RetiredReferenceBadge>` on `EmployeeDetail`.
 *
 * ── AC-3 counts are DERIVED, not typed ────────────────────────────────────
 * The `/erp` overview's 7 count tiles (`getErpOverviewState`) call the SAME
 * `list*` reads this file answers, with `?page=0&size=1`, and read
 * `meta.totalElements` off the SAME arrays below — there is no separate
 * "summary" object to drift out of sync (unlike ecommerce's hand-maintained
 * `*_SUMMARY` constants, which is exactly the shape TASK-PC-FE-284's
 * CORRECTION had to fix). `pendingApprovals` = the `ME` employee's inbox
 * (SUBMITTED/IN_REVIEW rows whose CURRENT stage approver is `ME`);
 * `activeDelegations` = delegation-facts filtered `status=ACTIVE` — both
 * counted by the SAME filter the real screen's own query applies, not a
 * separately-typed number.
 */

const SUFFIX = SAMPLE_LABEL_SUFFIX;
const WARNING = `Eventually-consistent read-model${SUFFIX}`;

/** The sample visitor's own identity for "my inbox" / "my delegations"
 *  purposes (the approval-service / delegation-service `caller.sub`). There
 *  is no real session, so this is a fixture-level convention — documented
 *  once here rather than re-derived per handler. */
const ME = 'emp-sample-0001';

function splitPath(path: string): { pathname: string; query: URLSearchParams } {
  const [pathname, qs = ''] = path.split('?');
  return { pathname, query: new URLSearchParams(qs) };
}

function intParam(query: URLSearchParams, key: string, fallback: number): number {
  const raw = query.get(key);
  if (raw === null) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

/** `{ data, meta: { timestamp, page, size, totalElements } }` — the erp/
 *  read-model masterdata envelope shape. */
function pageEnvelope<T>(
  rows: readonly T[],
  page: number,
  size: number,
  extraMeta?: Record<string, unknown>,
) {
  const safeSize = Math.max(1, size);
  const start = page * safeSize;
  return {
    data: rows.slice(start, start + safeSize),
    meta: {
      timestamp: SAMPLE_AS_OF,
      page,
      size: safeSize,
      totalElements: rows.length,
      ...extraMeta,
    },
  };
}

// ===========================================================================
// departments — GET /api/erp/masterdata/departments (+ /{id})
// ===========================================================================

const DEPARTMENTS_PATH = '/api/erp/masterdata/departments';

export const ERP_DEPARTMENTS = [
  {
    id: 'dept-sample-0001',
    code: 'HQ',
    name: `본사${SUFFIX}`,
    parentId: null,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2020-01-01', effectiveTo: null },
  },
  {
    id: 'dept-sample-0002',
    code: 'SALES1',
    name: `영업1팀${SUFFIX}`,
    parentId: 'dept-sample-0001',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2021-03-01', effectiveTo: null },
  },
  {
    id: 'dept-sample-0003',
    code: 'DEV1',
    name: `개발1팀${SUFFIX}`,
    parentId: 'dept-sample-0001',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2021-03-01', effectiveTo: null },
  },
  {
    // E1 headline case (task spec) — an ACTIVE employee (emp-sample-0003)
    // references THIS retired department; `<RetiredReferenceBadge>` must
    // surface on that employee's detail, never silently hidden.
    id: 'dept-sample-0004',
    code: 'SALES2',
    name: `영업2팀${SUFFIX}`,
    parentId: 'dept-sample-0001',
    status: 'RETIRED',
    effectivePeriod: { effectiveFrom: '2019-01-01', effectiveTo: '2026-06-30' },
  },
] as const;

function departmentsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${DEPARTMENTS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_DEPARTMENTS.find((d) => d.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'department not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== DEPARTMENTS_PATH) return undefined;
  const active = query.get('active');
  const parentId = query.get('parentId');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ERP_DEPARTMENTS)[number][] = [...ERP_DEPARTMENTS];
  if (active !== null) {
    const wantActive = active === 'true';
    rows = rows.filter((d) => (d.status === 'ACTIVE') === wantActive);
  }
  if (parentId) rows = rows.filter((d) => d.parentId === parentId);
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// employees — GET /api/erp/masterdata/employees (+ /{id})
// ===========================================================================

const EMPLOYEES_PATH = '/api/erp/masterdata/employees';

export const ERP_EMPLOYEES = [
  {
    id: 'emp-sample-0001',
    employeeNumber: 'E-0001',
    name: `김하나${SUFFIX}`,
    departmentId: 'dept-sample-0002',
    jobGradeId: 'jg-sample-0001',
    costCenterId: 'cc-sample-0001',
    status: 'ACTIVE',
    employmentStatus: 'EMPLOYED',
    effectivePeriod: { effectiveFrom: '2021-03-01', effectiveTo: null },
  },
  {
    id: 'emp-sample-0002',
    employeeNumber: 'E-0002',
    name: `이민준${SUFFIX}`,
    departmentId: 'dept-sample-0003',
    jobGradeId: 'jg-sample-0002',
    costCenterId: 'cc-sample-0002',
    status: 'ACTIVE',
    employmentStatus: 'ON_LEAVE',
    effectivePeriod: { effectiveFrom: '2021-06-01', effectiveTo: null },
  },
  {
    // E1 headline case — references the RETIRED dept-sample-0004.
    id: 'emp-sample-0003',
    employeeNumber: 'E-0003',
    name: `박지훈${SUFFIX}`,
    departmentId: 'dept-sample-0004',
    jobGradeId: 'jg-sample-0001',
    costCenterId: 'cc-sample-0001',
    status: 'ACTIVE',
    employmentStatus: 'SEPARATED',
    effectivePeriod: { effectiveFrom: '2019-05-01', effectiveTo: null },
  },
  {
    // No FK refs at all — the "— (no reference)" rendering path; this is a
    // DIFFERENT case than emp-sample-0003 above (a present-but-retired ref).
    id: 'emp-sample-0004',
    employeeNumber: 'E-0004',
    name: `최수아${SUFFIX}`,
    departmentId: null,
    jobGradeId: null,
    costCenterId: null,
    status: 'ACTIVE',
    employmentStatus: 'EMPLOYED',
    effectivePeriod: { effectiveFrom: '2024-01-01', effectiveTo: null },
  },
] as const;

function employeesFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${EMPLOYEES_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_EMPLOYEES.find((e) => e.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'employee not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== EMPLOYEES_PATH) return undefined;
  const active = query.get('active');
  const departmentId = query.get('departmentId');
  const costCenterId = query.get('costCenterId');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ERP_EMPLOYEES)[number][] = [...ERP_EMPLOYEES];
  if (active !== null) {
    const wantActive = active === 'true';
    rows = rows.filter((e) => (e.status === 'ACTIVE') === wantActive);
  }
  if (departmentId) rows = rows.filter((e) => e.departmentId === departmentId);
  if (costCenterId) rows = rows.filter((e) => e.costCenterId === costCenterId);
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// job-grades — GET /api/erp/masterdata/job-grades (+ /{id})
// ===========================================================================

const JOB_GRADES_PATH = '/api/erp/masterdata/job-grades';

export const ERP_JOB_GRADES = [
  {
    id: 'jg-sample-0001',
    code: 'G1',
    name: `사원${SUFFIX}`,
    displayOrder: 1,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2020-01-01', effectiveTo: null },
  },
  {
    id: 'jg-sample-0002',
    code: 'G2',
    name: `대리${SUFFIX}`,
    displayOrder: 2,
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2020-01-01', effectiveTo: null },
  },
  {
    id: 'jg-sample-0003',
    code: 'G3',
    name: `차장${SUFFIX}`,
    displayOrder: 3,
    status: 'RETIRED',
    effectivePeriod: { effectiveFrom: '2020-01-01', effectiveTo: '2025-12-31' },
  },
] as const;

function jobGradesFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${JOB_GRADES_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_JOB_GRADES.find((g) => g.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'job grade not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== JOB_GRADES_PATH) return undefined;
  const active = query.get('active');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ERP_JOB_GRADES)[number][] = [...ERP_JOB_GRADES]
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder);
  if (active !== null) {
    const wantActive = active === 'true';
    rows = rows.filter((g) => (g.status === 'ACTIVE') === wantActive);
  }
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// cost-centers — GET /api/erp/masterdata/cost-centers (+ /{id})
// ===========================================================================

const COST_CENTERS_PATH = '/api/erp/masterdata/cost-centers';

export const ERP_COST_CENTERS = [
  {
    id: 'cc-sample-0001',
    code: 'CC-100',
    name: `영업비용센터${SUFFIX}`,
    departmentId: 'dept-sample-0002',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2021-03-01', effectiveTo: null },
  },
  {
    id: 'cc-sample-0002',
    code: 'CC-200',
    name: `개발비용센터${SUFFIX}`,
    departmentId: 'dept-sample-0003',
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2021-03-01', effectiveTo: null },
  },
  {
    id: 'cc-sample-0003',
    code: 'CC-300',
    name: `폐지비용센터${SUFFIX}`,
    departmentId: 'dept-sample-0001',
    status: 'RETIRED',
    effectivePeriod: { effectiveFrom: '2019-01-01', effectiveTo: '2025-06-30' },
  },
] as const;

function costCentersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${COST_CENTERS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_COST_CENTERS.find((c) => c.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'cost center not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== COST_CENTERS_PATH) return undefined;
  const active = query.get('active');
  const departmentId = query.get('departmentId');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ERP_COST_CENTERS)[number][] = [...ERP_COST_CENTERS];
  if (active !== null) {
    const wantActive = active === 'true';
    rows = rows.filter((c) => (c.status === 'ACTIVE') === wantActive);
  }
  if (departmentId) rows = rows.filter((c) => c.departmentId === departmentId);
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// business-partners — GET /api/erp/masterdata/business-partners (+ /{id})
// ===========================================================================

const BUSINESS_PARTNERS_PATH = '/api/erp/masterdata/business-partners';

export const ERP_BUSINESS_PARTNERS = [
  {
    id: 'bp-sample-0001',
    code: 'BP-001',
    name: `한빛머티리얼${SUFFIX}`,
    partnerType: 'SUPPLIER',
    paymentTerms: { termDays: 30, method: 'BANK_TRANSFER' },
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2020-01-01', effectiveTo: null },
  },
  {
    id: 'bp-sample-0002',
    code: 'BP-002',
    name: `경성상사${SUFFIX}`,
    partnerType: 'CUSTOMER',
    paymentTerms: { termDays: 14, method: 'CARD' },
    status: 'ACTIVE',
    effectivePeriod: { effectiveFrom: '2020-06-01', effectiveTo: null },
  },
  {
    id: 'bp-sample-0003',
    code: 'BP-003',
    name: `만료거래처${SUFFIX}`,
    partnerType: 'BOTH',
    paymentTerms: undefined,
    status: 'RETIRED',
    effectivePeriod: { effectiveFrom: '2018-01-01', effectiveTo: '2025-01-01' },
  },
] as const;

function businessPartnersFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${BUSINESS_PARTNERS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_BUSINESS_PARTNERS.find((p) => p.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'business partner not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== BUSINESS_PARTNERS_PATH) return undefined;
  const active = query.get('active');
  const partnerType = query.get('partnerType');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof ERP_BUSINESS_PARTNERS)[number][] = [...ERP_BUSINESS_PARTNERS];
  if (active !== null) {
    const wantActive = active === 'true';
    rows = rows.filter((p) => (p.status === 'ACTIVE') === wantActive);
  }
  if (partnerType) rows = rows.filter((p) => p.partnerType === partnerType);
  return pageEnvelope(rows, page, size);
}

// ===========================================================================
// read-model — employee org-view — GET /api/erp/read-model/employees (+ /{id})
// ===========================================================================

const ORGVIEW_PATH = '/api/erp/read-model/employees';

interface DeptPathNodeSeed {
  id: string;
  code: string;
  name: string;
}
interface DeptRefSeed extends DeptPathNodeSeed {
  path: DeptPathNodeSeed[];
}

function deptRef(id: string): DeptRefSeed | null {
  const d = ERP_DEPARTMENTS.find((x) => x.id === id);
  if (!d) return null;
  const parentPath: DeptRefSeed | null = d.parentId ? deptRef(d.parentId) : null;
  const path: DeptPathNodeSeed[] = [
    ...(parentPath?.path ?? []),
    { id: d.id, code: d.code, name: d.name },
  ];
  return { id: d.id, code: d.code, name: d.name, path };
}
function costCenterRef(id: string | null) {
  if (!id) return null;
  const c = ERP_COST_CENTERS.find((x) => x.id === id);
  return c ? { id: c.id, code: c.code, name: c.name } : null;
}
function jobGradeRef(id: string | null) {
  if (!id) return null;
  const g = ERP_JOB_GRADES.find((x) => x.id === id);
  return g ? { id: g.id, code: g.code, name: g.name, displayOrder: g.displayOrder } : null;
}

export const ERP_EMPLOYEE_ORG_VIEWS = ERP_EMPLOYEES.map((e) => ({
  id: e.id,
  employeeNumber: e.employeeNumber,
  name: e.name,
  status: e.status,
  effectivePeriod: e.effectivePeriod,
  department: e.departmentId ? deptRef(e.departmentId) : null,
  costCenter: costCenterRef(e.costCenterId),
  jobGrade: jobGradeRef(e.jobGradeId),
}));

function orgViewFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${ORGVIEW_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_EMPLOYEE_ORG_VIEWS.find((e) => e.id === id);
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'employee org-view not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF, warning: WARNING } };
  }

  if (pathname !== ORGVIEW_PATH) return undefined;
  const departmentId = query.get('departmentId');
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows = [...ERP_EMPLOYEE_ORG_VIEWS];
  if (departmentId) rows = rows.filter((e) => e.department?.id === departmentId);
  if (status) rows = rows.filter((e) => e.status === status);
  return pageEnvelope(rows, page, size, { warning: WARNING });
}

// ===========================================================================
// read-model — delegation facts — GET /api/erp/read-model/delegations (+ /{grantId})
// ===========================================================================

const DELEGATION_FACTS_PATH = '/api/erp/read-model/delegations';

export const ERP_DELEGATION_FACTS = [
  {
    grantId: 'grant-sample-0001',
    status: 'ACTIVE',
    delegatorId: 'emp-sample-0001',
    delegateId: 'emp-sample-0002',
    validFrom: '2026-09-01',
    scope: 'GLOBAL',
  },
  {
    // validTo already past "today" — the fact card's own status badge does
    // NOT compute an expired tone (only the grant list does — see
    // `DelegationFactCard`'s docstring), so this row still counts as an
    // ACTIVE fact for the `activeDelegations` overview tile (AC-3).
    grantId: 'grant-sample-0002',
    status: 'ACTIVE',
    delegatorId: 'emp-sample-0003',
    delegateId: 'emp-sample-0001',
    validFrom: '2026-08-01',
    validTo: '2026-08-31',
    scope: 'REQUEST',
    scopeRequestId: 'appr-sample-0001',
  },
  {
    // BE-018 edge case — a revoke seen before its grant: `scope` is ABSENT
    // (unknown), `validFrom` is ABSENT too. Never a crash (NON_NULL-absent
    // tolerance).
    grantId: 'grant-sample-0003',
    status: 'REVOKED',
    delegatorId: 'emp-sample-0002',
    delegateId: 'emp-sample-0003',
    reason: '장기 휴가 복귀',
    revokedAt: '2026-08-15T00:00:00Z',
  },
] as const;

function delegationFactsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${DELEGATION_FACTS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const grantId = decodeURIComponent(detailMatch[1]);
    const found = ERP_DELEGATION_FACTS.find((f) => f.grantId === grantId);
    // read-model-api.md § "GET /api/erp/read-model/delegations/{grantId}" —
    // 404 `MASTERDATA_NOT_FOUND` (a projection miss, not fabricated; NOT
    // `DELEGATION_NOT_FOUND` — that code is documented only for the
    // approval-service WRITE endpoint `POST …/delegations/{id}/revoke`,
    // which is unreachable in sample mode anyway — every non-GET is refused
    // by the router's generic 403 SAMPLE_READ_ONLY branch before any fixture
    // code runs).
    if (!found) return fixtureNotFound('MASTERDATA_NOT_FOUND', 'delegation fact not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF, warning: WARNING } };
  }

  if (pathname !== DELEGATION_FACTS_PATH) return undefined;
  const delegatorId = query.get('delegatorId');
  const delegateId = query.get('delegateId');
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows = [...ERP_DELEGATION_FACTS];
  if (delegatorId) rows = rows.filter((f) => f.delegatorId === delegatorId);
  if (delegateId) rows = rows.filter((f) => f.delegateId === delegateId);
  if (status) rows = rows.filter((f) => f.status === status);
  return pageEnvelope(rows, page, size, { warning: WARNING });
}

// ===========================================================================
// erp surface — dispatch (masterdata + org-view + delegation-facts, ALL under
// the single `flat:erp` fixture key — see `erp-client.ts`'s `logPrefix: 'erp'`).
// ===========================================================================

function erpFixture(path: string): unknown {
  return (
    departmentsFixture(path) ??
    employeesFixture(path) ??
    jobGradesFixture(path) ??
    costCentersFixture(path) ??
    businessPartnersFixture(path) ??
    orgViewFixture(path) ??
    delegationFactsFixture(path)
  );
}

// ===========================================================================
// approval-service — GET /api/erp/approval/requests (+ /{id}), /inbox
// ===========================================================================

const APPROVAL_REQUESTS_PATH = '/api/erp/approval/requests';
const APPROVAL_INBOX_PATH = '/api/erp/approval/inbox';

interface ApprovalHistoryEntrySeed {
  transition: string;
  actor: string;
  at: string;
  reason?: string;
  stage?: number;
}

interface ApprovalStageSeed {
  stageIndex: number;
  approverId: string;
  status: string;
}

interface ApprovalRequestSeed {
  id: string;
  status: string;
  subjectType: string;
  subjectId: string;
  title: string;
  approverId: string;
  submitterId: string;
  reason?: string;
  history: ApprovalHistoryEntrySeed[];
  createdAt: string;
  submittedAt?: string;
  finalizedAt?: string;
  stages?: ApprovalStageSeed[];
  currentStage?: number;
  totalStages?: number;
}

/**
 * World-consistent with `ERP_EMPLOYEES` / `ERP_DEPARTMENTS` (AC-4 — every
 * `approverId` / `submitterId` / stage `approverId` and every EMPLOYEE-type
 * `subjectId` is a real employee id; every DEPARTMENT-type `subjectId` is a
 * real department id).
 */
export const ERP_APPROVAL_REQUESTS: readonly ApprovalRequestSeed[] = [
  {
    // SUBMITTED, single-stage, current approver = ME → in the inbox.
    id: 'appr-sample-0001',
    status: 'SUBMITTED',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-sample-0002',
    title: `영업1팀 예산 증액 요청${SUFFIX}`,
    approverId: ME,
    submitterId: 'emp-sample-0002',
    createdAt: '2026-09-10T00:00:00Z',
    submittedAt: '2026-09-10T01:00:00Z',
    history: [{ transition: 'submit', actor: 'emp-sample-0002', at: '2026-09-10T01:00:00Z' }],
  },
  {
    // IN_REVIEW, multi-stage (v2.0) — stage 0 (ME) already APPROVED, stage 1
    // (emp-sample-0004) is the current pending approver → NOT ME → NOT in
    // the inbox (proves the inbox reads the CURRENT stage, not stage 0).
    id: 'appr-sample-0002',
    status: 'IN_REVIEW',
    subjectType: 'EMPLOYEE',
    subjectId: 'emp-sample-0003',
    title: `박지훈 직급 조정 요청${SUFFIX}`,
    approverId: 'emp-sample-0004',
    submitterId: 'emp-sample-0002',
    createdAt: '2026-09-05T00:00:00Z',
    submittedAt: '2026-09-05T01:00:00Z',
    stages: [
      { stageIndex: 0, approverId: ME, status: 'APPROVED' },
      { stageIndex: 1, approverId: 'emp-sample-0004', status: 'PENDING' },
    ],
    currentStage: 1,
    totalStages: 2,
    history: [
      { transition: 'submit', actor: 'emp-sample-0002', at: '2026-09-05T01:00:00Z' },
      { transition: 'approve', actor: ME, at: '2026-09-06T00:00:00Z', stage: 0 },
    ],
  },
  {
    // SUBMITTED, single-stage, current approver = ME → second inbox item
    // (proves the inbox list, not just a single row, renders).
    id: 'appr-sample-0003',
    status: 'SUBMITTED',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-sample-0003',
    title: `개발1팀 신규 인원 채용 승인${SUFFIX}`,
    approverId: ME,
    submitterId: 'emp-sample-0004',
    createdAt: '2026-09-12T00:00:00Z',
    submittedAt: '2026-09-12T02:00:00Z',
    history: [{ transition: 'submit', actor: 'emp-sample-0004', at: '2026-09-12T02:00:00Z' }],
  },
  {
    // Terminal — APPROVED. NOT in anyone's inbox (terminal states never are).
    id: 'appr-sample-0004',
    status: 'APPROVED',
    subjectType: 'EMPLOYEE',
    subjectId: 'emp-sample-0002',
    title: `이민준 부서 이동 승인${SUFFIX}`,
    approverId: 'emp-sample-0003',
    submitterId: ME,
    createdAt: '2026-08-01T00:00:00Z',
    submittedAt: '2026-08-01T01:00:00Z',
    finalizedAt: '2026-08-02T00:00:00Z',
    history: [
      { transition: 'submit', actor: ME, at: '2026-08-01T01:00:00Z' },
      { transition: 'approve', actor: 'emp-sample-0003', at: '2026-08-02T00:00:00Z' },
    ],
  },
  {
    // Terminal — REJECTED, with a reason (E4 — reject requires one).
    id: 'appr-sample-0005',
    status: 'REJECTED',
    subjectType: 'DEPARTMENT',
    subjectId: 'dept-sample-0004',
    title: `영업2팀 폐지 결재${SUFFIX}`,
    approverId: 'emp-sample-0002',
    submitterId: ME,
    reason: '예산 부족으로 반려',
    createdAt: '2026-06-01T00:00:00Z',
    submittedAt: '2026-06-01T01:00:00Z',
    finalizedAt: '2026-06-05T00:00:00Z',
    history: [
      { transition: 'submit', actor: ME, at: '2026-06-01T01:00:00Z' },
      {
        transition: 'reject',
        actor: 'emp-sample-0002',
        at: '2026-06-05T00:00:00Z',
        reason: '예산 부족으로 반려',
      },
    ],
  },
];

const NON_TERMINAL_STATUSES = new Set(['SUBMITTED', 'IN_REVIEW']);

/** The CURRENT approver — the top-level `approverId` already tracks this
 *  fixture-side (kept in sync with `stages[currentStage]` for multi-stage
 *  rows above), so the inbox predicate reads one field either way. */
function currentApprover(r: ApprovalRequestSeed): string {
  return r.approverId;
}

function requestSummary(r: ApprovalRequestSeed) {
  return {
    id: r.id,
    status: r.status,
    subjectType: r.subjectType,
    subjectId: r.subjectId,
    title: r.title,
    approverId: r.approverId,
    submitterId: r.submitterId,
    createdAt: r.createdAt,
    submittedAt: r.submittedAt,
    stages: r.stages,
    currentStage: r.currentStage,
    totalStages: r.totalStages,
  };
}

function approvalFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === APPROVAL_INBOX_PATH) {
    const page = intParam(query, 'page', 0);
    const size = intParam(query, 'size', 20);
    const rows = ERP_APPROVAL_REQUESTS.filter(
      (r) => NON_TERMINAL_STATUSES.has(r.status) && currentApprover(r) === ME,
    );
    return pageEnvelope(rows.map(requestSummary), page, size);
  }

  const detailMatch = pathname.match(new RegExp(`^${APPROVAL_REQUESTS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = ERP_APPROVAL_REQUESTS.find((r) => r.id === id);
    if (!found) return fixtureNotFound('APPROVAL_REQUEST_NOT_FOUND', 'approval request not found');
    return { data: found, meta: { timestamp: SAMPLE_AS_OF } };
  }

  if (pathname !== APPROVAL_REQUESTS_PATH) return undefined;
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = status
    ? ERP_APPROVAL_REQUESTS.filter((r) => r.status === status)
    : ERP_APPROVAL_REQUESTS;
  return pageEnvelope(rows.map(requestSummary), page, size);
}

// ===========================================================================
// erp_delegation — GET /api/erp/approval/delegations (?role=DELEGATOR|DELEGATE)
// ===========================================================================

const DELEGATIONS_PATH = '/api/erp/approval/delegations';

interface DelegationGrantSeed {
  id: string;
  delegatorId: string;
  delegateId: string;
  validFrom: string;
  validTo?: string;
  reason?: string;
  status: string;
  createdAt: string;
  createdBy: string;
  revokedAt?: string;
  revokedBy?: string;
}

/** Same 3 real-world grants `ERP_DELEGATION_FACTS` projects (world-consistent
 *  ids: `grant-sample-0001..0003`). */
export const ERP_DELEGATION_GRANTS: readonly DelegationGrantSeed[] = [
  {
    id: 'grant-sample-0001',
    delegatorId: ME,
    delegateId: 'emp-sample-0002',
    validFrom: '2026-09-01',
    status: 'ACTIVE',
    createdAt: '2026-08-25T00:00:00Z',
    createdBy: ME,
  },
  {
    id: 'grant-sample-0002',
    delegatorId: 'emp-sample-0003',
    delegateId: ME,
    validFrom: '2026-08-01',
    validTo: '2026-08-31',
    reason: '휴가 중 결재 위임',
    status: 'ACTIVE',
    createdAt: '2026-07-28T00:00:00Z',
    createdBy: 'emp-sample-0003',
  },
  {
    id: 'grant-sample-0003',
    delegatorId: 'emp-sample-0002',
    delegateId: 'emp-sample-0003',
    validFrom: '2026-07-01',
    status: 'REVOKED',
    createdAt: '2026-06-25T00:00:00Z',
    createdBy: 'emp-sample-0002',
    revokedAt: '2026-08-15T00:00:00Z',
    revokedBy: 'emp-sample-0002',
  },
];

function delegationGrantsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== DELEGATIONS_PATH) return undefined;
  const role = query.get('role');
  let rows = [...ERP_DELEGATION_GRANTS];
  if (role === 'DELEGATOR') rows = rows.filter((g) => g.delegatorId === ME);
  else if (role === 'DELEGATE') rows = rows.filter((g) => g.delegateId === ME);
  return {
    data: rows,
    meta: { timestamp: SAMPLE_AS_OF, page: 0, size: rows.length || 1, totalElements: rows.length },
  };
}

// ===========================================================================
// exports
// ===========================================================================

export const ERP_FIXTURE_HANDLERS: Readonly<Record<string, ErpFixtureHandler>> = {
  'flat:erp': erpFixture,
  'flat:erp_approval': approvalFixture,
  'flat:erp_delegation': delegationGrantsFixture,
};

/**
 * The AGGREGATE of every seed array per surface (not just one branch's
 * output), for the R2ⓐ label guard — same reasoning as `iam.ts`/`ecommerce.ts`.
 */
export const ERP_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'flat:erp': {
    departments: ERP_DEPARTMENTS,
    employees: ERP_EMPLOYEES,
    jobGrades: ERP_JOB_GRADES,
    costCenters: ERP_COST_CENTERS,
    businessPartners: ERP_BUSINESS_PARTNERS,
    employeeOrgViews: ERP_EMPLOYEE_ORG_VIEWS,
    delegationFacts: ERP_DELEGATION_FACTS,
    warning: WARNING,
  },
  'flat:erp_approval': { requests: ERP_APPROVAL_REQUESTS },
  'flat:erp_delegation': { grants: ERP_DELEGATION_GRANTS },
};
