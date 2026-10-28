import { SAMPLE_LABEL_SUFFIX, SAMPLE_TENANT_ID } from '../codes';
import { fixtureNotFound } from '../router';
import {
  SAMPLE_METRICS_WINDOWS,
  instantInside,
  kstDate,
  samplePeriod,
  type SamplePeriod,
  type SampleWindow,
} from './metrics-windows';

/**
 * Same shape as `fixtures/index.ts`'s `FixtureHandler` — written out here
 * (rather than imported) so this module has no edge back to `index.ts`, which
 * imports THIS module (`shared/sample/**` must stay a DAG the isolation guard
 * can read; a type-only cycle is erased at compile time either way, but there
 * is no need to introduce one).
 */
type IamFixtureHandler = (path: string) => unknown;

/**
 * IAM domain fixtures (TASK-PC-FE-283 — `ADR-MONO-074` execution 2/8): the 9
 * `callAdminGateway` surfaces (accounts · audit · operators · rbac ·
 * subscriptions · partnerships · tenants · org_nodes · groups).
 *
 * Hand-authored synthetic data (ADR-MONO-074 A4 — no extraction path from any
 * backend). Each handler is parsed by the SAME zod schema the real screen uses
 * (`tests/unit/sample-fixtures-schema-iam.test.ts`).
 *
 * R2ⓐ: human-readable strings end with «(샘플)»; ids, codes, enums, dates and
 * amounts do not (`shared/sample/label-rule.ts` — see that file's TASK-PC-FE-283
 * additions for the new keys this file introduces). AC-7: every person-
 * identifying value (operator/account email, audit actor) is unambiguously
 * synthetic — `*.example` domains, invented Korean names.
 *
 * The router hands the handler the FULL path (query string included, since
 * TASK-PC-FE-283 widened `FixtureHandler` — see `fixtures/index.ts`), so each
 * list handler applies the screen's own filter/search/page params over its
 * seed rows (AC-4) and reports a REAL `totalElements` (the seed row count
 * after filtering, not a fixed number).
 */

const SUFFIX = SAMPLE_LABEL_SUFFIX;

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

/** `{ content, totalElements, page, size, totalPages }` (accounts/audit/operators). */
function paginateContent<T>(rows: readonly T[], page: number, size: number) {
  const totalElements = rows.length;
  const safeSize = Math.max(1, size);
  const totalPages = Math.ceil(totalElements / safeSize);
  const start = page * safeSize;
  return {
    content: rows.slice(start, start + safeSize),
    totalElements,
    page,
    size: safeSize,
    totalPages,
  };
}

/** `{ items, page, size, totalElements, totalPages }` (tenants/groups/partnerships). */
function paginateItems<T>(rows: readonly T[], page: number, size: number) {
  const totalElements = rows.length;
  const safeSize = Math.max(1, size);
  const totalPages = Math.ceil(totalElements / safeSize);
  const start = page * safeSize;
  return {
    items: rows.slice(start, start + safeSize),
    page,
    size: safeSize,
    totalElements,
    totalPages,
  };
}

// ===========================================================================
// accounts — GET /api/admin/accounts (list ↔ email single-lookup)
// ===========================================================================

const ACCOUNTS_PATH = '/api/admin/accounts';

export const IAM_ACCOUNTS = [
  {
    id: 'acc-sample-0001',
    email: `hana.kim.sample@example.com${SUFFIX}`,
    status: 'ACTIVE',
    createdAt: '2026-01-12T02:00:00Z',
  },
  {
    id: 'acc-sample-0002',
    email: `minjun.lee.sample@example.com${SUFFIX}`,
    status: 'ACTIVE',
    createdAt: '2026-02-03T05:30:00Z',
  },
  {
    id: 'acc-sample-0003',
    email: `soyeon.park.sample@example.com${SUFFIX}`,
    status: 'LOCKED',
    createdAt: '2026-03-18T09:15:00Z',
  },
  {
    id: 'acc-sample-0004',
    email: `jiho.choi.sample@example.com${SUFFIX}`,
    status: 'LOCKED',
    createdAt: '2026-04-22T12:45:00Z',
  },
  {
    id: 'acc-sample-0005',
    email: `yuna.jang.sample@example.com${SUFFIX}`,
    status: 'DELETED',
    createdAt: '2026-05-30T14:00:00Z',
  },
] as const;

/**
 * `email` is BOTH the table's human-readable display value (suffixed, R2ⓐ)
 * AND the search/lookup key (`use-accounts.ts` sends the raw typed value as
 * the `email` query param; `getAccountByEmail()` IS `searchAccounts({email})`
 * — coordinator finding, TASK-PC-FE-283). Normalising strips the suffix so a
 * visitor who types the PLAIN address matches, and a visitor who copy-pastes
 * the rendered (suffixed) cell ALSO matches — both spellings resolve to the
 * same row. Exact match (not substring): every seed address shares the
 * `sample`/`.example` vocabulary, so a substring match would make "실제로
 * 좁혀지지 않는" look like search doing nothing (AC-4's own failure mode).
 */
function normalizeEmail(value: string): string {
  return value.replace(SUFFIX, '').trim().toLowerCase();
}

function accountsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== ACCOUNTS_PATH) return undefined;

  const email = query.get('email');
  if (email && email.trim() !== '') {
    const needle = normalizeEmail(email);
    const matches = IAM_ACCOUNTS.filter((a) => normalizeEmail(a.email) === needle);
    // AC-3's "no such id → the real 404 shape" does NOT apply to this branch:
    // `GET /api/admin/accounts?email=…` is a FILTERED LIST read (0 or 1
    // results), not a detail-by-id endpoint — `accounts-api.ts` `getAccountByEmail`
    // documents "the producer has no dedicated GET-by-id" for accounts. So an
    // unmatched address is a normal empty PAGE (real producer behaviour),
    // never a `fixtureNotFound` 404 (unlike tenants/org-nodes/groups below,
    // which really do have a GET-by-id and really do 404).
    return {
      content: matches,
      totalElements: matches.length,
      page: 0,
      size: Math.max(1, matches.length),
      totalPages: matches.length > 0 ? 1 : 0,
    };
  }

  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = status ? IAM_ACCOUNTS.filter((a) => a.status === status) : IAM_ACCOUNTS;
  return paginateContent(rows, page, size);
}

// ===========================================================================
// audit — GET /api/admin/audit (unified admin / login_history / suspicious)
// ===========================================================================

const AUDIT_PATH = '/api/admin/audit';

const IAM_AUDIT_ADMIN_ROWS = [
  {
    source: 'admin',
    auditId: 'audit-sample-0001',
    actionCode: 'ACCOUNT_LOCK',
    operatorId: 'op-sample-0001',
    targetId: 'acc-sample-0003',
    reason: '의심스러운 로그인 시도',
    outcome: 'SUCCESS',
    occurredAt: '2026-09-10T03:00:00Z',
  },
  {
    source: 'admin',
    auditId: 'audit-sample-0002',
    actionCode: 'OPERATOR_CREATE',
    operatorId: 'op-sample-0002',
    targetId: 'op-sample-0005',
    reason: '신규 입사자 계정 생성',
    outcome: 'SUCCESS',
    occurredAt: '2026-09-11T06:20:00Z',
  },
  // TASK-PC-FE-374 — two more lock events (09-07 and 09-14 KST) so 계정 잠금
  // has a bar in the week window as well. Targets are the two LOCKED accounts.
  {
    source: 'admin',
    auditId: 'audit-sample-0003',
    actionCode: 'ACCOUNT_LOCK',
    operatorId: 'op-sample-0001',
    targetId: 'acc-sample-0004',
    reason: '비밀번호 반복 실패',
    outcome: 'SUCCESS',
    occurredAt: '2026-09-07T04:00:00Z',
  },
  {
    source: 'admin',
    auditId: 'audit-sample-0004',
    actionCode: 'ACCOUNT_LOCK',
    operatorId: 'op-sample-0002',
    targetId: 'acc-sample-0003',
    reason: '해외 IP 접속 시도',
    outcome: 'SUCCESS',
    occurredAt: '2026-09-14T06:00:00Z',
  },
] as const;

const IAM_AUDIT_LOGIN_ROWS = [
  {
    source: 'login_history',
    eventId: 'login-sample-0001',
    accountId: 'acc-sample-0001',
    outcome: 'SUCCESS',
    ipMasked: '203.0.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-12T01:00:00Z',
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0002',
    accountId: 'acc-sample-0002',
    outcome: 'FAILURE',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-12T02:10:00Z',
  },
  // TASK-PC-FE-374 — ten more login events spread over Sept 2..15 (KST). Each
  // KST date is noted; login-sample-0010 sits at UTC 15:30, i.e. already the
  // NEXT KST day (09-15), on purpose.
  {
    source: 'login_history',
    eventId: 'login-sample-0003',
    accountId: 'acc-sample-0001',
    outcome: 'SUCCESS',
    ipMasked: '203.0.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-01T23:30:00Z', // KST 09-02
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0004',
    accountId: 'acc-sample-0002',
    outcome: 'SUCCESS',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-03T02:00:00Z', // KST 09-03
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0005',
    accountId: 'acc-sample-0003',
    outcome: 'FAILURE',
    ipMasked: '45.33.***.**',
    geoCountry: 'US',
    occurredAt: '2026-09-04T14:00:00Z', // KST 09-04 23:00
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0006',
    accountId: 'acc-sample-0001',
    outcome: 'SUCCESS',
    ipMasked: '203.0.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-06T00:30:00Z', // KST 09-06
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0007',
    accountId: 'acc-sample-0004',
    outcome: 'FAILURE',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-08T10:00:00Z', // KST 09-08 19:00
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0008',
    accountId: 'acc-sample-0002',
    outcome: 'SUCCESS',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-09T03:00:00Z', // KST 09-09
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0009',
    accountId: 'acc-sample-0001',
    outcome: 'SUCCESS',
    ipMasked: '203.0.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-10T01:30:00Z', // KST 09-10
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0010',
    accountId: 'acc-sample-0002',
    outcome: 'FAILURE',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-14T15:30:00Z', // KST 09-15 00:30 (week window)
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0011',
    accountId: 'acc-sample-0001',
    outcome: 'SUCCESS',
    ipMasked: '203.0.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-14T01:00:00Z', // KST 09-14 (week window)
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0012',
    accountId: 'acc-sample-0002',
    outcome: 'SUCCESS',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-15T00:20:00Z', // KST 09-15 (week window)
  },
  // TASK-PC-FE-380 — two more FAILUREs inside the PREVIOUS-week window (09-07..09-08
  // 20:00 KST) so the week «지난 주» 로그인 실패 reads 3 (with login-sample-0007).
  {
    source: 'login_history',
    eventId: 'login-sample-0013',
    accountId: 'acc-sample-0003',
    outcome: 'FAILURE',
    ipMasked: '45.33.***.**',
    geoCountry: 'US',
    occurredAt: '2026-09-07T03:00:00Z', // KST 09-07 12:00
  },
  {
    source: 'login_history',
    eventId: 'login-sample-0014',
    accountId: 'acc-sample-0004',
    outcome: 'FAILURE',
    ipMasked: '198.51.***.**',
    geoCountry: 'KR',
    occurredAt: '2026-09-08T02:30:00Z', // KST 09-08 11:30
  },
] as const;

const IAM_AUDIT_SUSPICIOUS_ROWS = [
  {
    source: 'suspicious',
    eventId: 'suspicious-sample-0001',
    accountId: 'acc-sample-0003',
    outcome: 'BLOCKED',
    ipMasked: '45.33.***.**',
    geoCountry: 'US',
    occurredAt: '2026-09-13T08:45:00Z',
  },
] as const;

export const IAM_AUDIT_ROWS = [
  ...IAM_AUDIT_ADMIN_ROWS,
  ...IAM_AUDIT_LOGIN_ROWS,
  ...IAM_AUDIT_SUSPICIOUS_ROWS,
];

function auditFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== AUDIT_PATH) return undefined;

  const source = query.get('source');
  const accountId = query.get('accountId');
  const actionCode = query.get('actionCode');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);

  let rows: typeof IAM_AUDIT_ROWS = IAM_AUDIT_ROWS;
  if (source) rows = rows.filter((r) => r.source === source);
  if (accountId) {
    rows = rows.filter((r) =>
      'accountId' in r ? r.accountId === accountId : 'targetId' in r && r.targetId === accountId,
    );
  }
  if (actionCode) {
    rows = rows.filter((r) => 'actionCode' in r && r.actionCode === actionCode);
  }
  return paginateContent(rows, page, size);
}

// ===========================================================================
// operators — GET /api/admin/operators (+ /me, /grantable-roles, /{id}/assignments)
// ===========================================================================

const OPERATORS_PATH = '/api/admin/operators';

export const IAM_OPERATORS = [
  {
    operatorId: 'op-sample-0001',
    email: `hana.kim.sample@example.com${SUFFIX}`,
    displayName: `김하나${SUFFIX}`,
    status: 'ACTIVE',
    roles: ['SUPER_ADMIN'],
    totpEnrolled: true,
    lastLoginAt: '2026-09-14T00:00:00Z',
    createdAt: '2025-11-01T00:00:00Z',
  },
  {
    operatorId: 'op-sample-0002',
    email: `minjun.lee.sample@example.com${SUFFIX}`,
    displayName: `이민준${SUFFIX}`,
    status: 'ACTIVE',
    roles: ['TENANT_ADMIN'],
    totpEnrolled: true,
    lastLoginAt: '2026-09-13T10:00:00Z',
    createdAt: '2025-12-05T00:00:00Z',
  },
  {
    operatorId: 'op-sample-0003',
    email: `soyeon.park.sample@example.com${SUFFIX}`,
    displayName: `박소연${SUFFIX}`,
    status: 'ACTIVE',
    roles: ['SUPPORT_READONLY'],
    totpEnrolled: false,
    lastLoginAt: '2026-09-10T08:00:00Z',
    createdAt: '2026-01-15T00:00:00Z',
  },
  {
    operatorId: 'op-sample-0004',
    email: `jiho.choi.sample@example.com${SUFFIX}`,
    displayName: `최지호${SUFFIX}`,
    status: 'SUSPENDED',
    roles: ['SUPPORT_LOCK'],
    totpEnrolled: false,
    lastLoginAt: null,
    createdAt: '2026-02-20T00:00:00Z',
  },
  {
    operatorId: 'op-sample-0005',
    email: `yuna.jang.sample@example.com${SUFFIX}`,
    displayName: `장유나${SUFFIX}`,
    status: 'ACTIVE',
    roles: ['SECURITY_ANALYST'],
    totpEnrolled: true,
    lastLoginAt: '2026-09-14T06:00:00Z',
    createdAt: '2026-03-01T00:00:00Z',
  },
] as const;

const IAM_GRANTABLE_ROLES = [
  'TENANT_ADMIN',
  'TENANT_BILLING_ADMIN',
  'SUPPORT_LOCK',
  'SUPPORT_READONLY',
  'SECURITY_ANALYST',
];

/**
 * TASK-MONO-772 S5 — pending operator invitations (`GET
 * /api/admin/operator-invitations`, same `operators` gateway surface). One
 * delivered, one whose mail FAILED (the warning + «다시 보내기» state a visitor
 * should be able to see), one expired. 🔴 No token / link field — the real
 * producer never returns one either.
 */
const INVITATIONS_PATH = '/api/admin/operator-invitations';

export const IAM_OPERATOR_INVITATIONS = [
  {
    invitationId: 'inv-sample-0001',
    tenantId: SAMPLE_TENANT_ID,
    email: `seojun.han.sample@example.com${SUFFIX}`,
    displayName: `한서준${SUFFIX}`,
    roles: ['SUPPORT_READONLY'],
    status: 'PENDING',
    expired: false,
    expiresAt: '2026-09-21T00:00:00Z',
    createdAt: '2026-09-14T00:00:00Z',
    invitedBy: 'op-sample-0002',
    delivery: { status: 'SENT', attemptedAt: '2026-09-14T00:00:01Z' },
    acceptedAt: null,
    acceptedOperatorId: null,
    cancelledAt: null,
  },
  {
    invitationId: 'inv-sample-0002',
    tenantId: SAMPLE_TENANT_ID,
    email: `jiwoo.yoon.sample@example.com${SUFFIX}`,
    displayName: `윤지우${SUFFIX}`,
    roles: ['SUPPORT_LOCK'],
    status: 'PENDING',
    expired: false,
    expiresAt: '2026-09-20T00:00:00Z',
    createdAt: '2026-09-13T00:00:00Z',
    invitedBy: 'op-sample-0002',
    delivery: { status: 'FAILED_TRANSIENT', attemptedAt: '2026-09-13T00:00:01Z' },
    acceptedAt: null,
    acceptedOperatorId: null,
    cancelledAt: null,
  },
  {
    invitationId: 'inv-sample-0003',
    tenantId: SAMPLE_TENANT_ID,
    email: `doyun.kang.sample@example.com${SUFFIX}`,
    displayName: `강도윤${SUFFIX}`,
    roles: ['SECURITY_ANALYST'],
    status: 'PENDING',
    expired: true,
    expiresAt: '2026-09-01T00:00:00Z',
    createdAt: '2026-08-25T00:00:00Z',
    invitedBy: 'op-sample-0001',
    delivery: { status: 'SENT', attemptedAt: '2026-08-25T00:00:01Z' },
    acceptedAt: null,
    acceptedOperatorId: null,
    cancelledAt: null,
  },
] as const;

function operatorsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === INVITATIONS_PATH) {
    const status = query.get('status') ?? 'PENDING';
    const rows = IAM_OPERATOR_INVITATIONS.filter((i) => i.status === status);
    return paginateContent(rows, intParam(query, 'page', 0), intParam(query, 'size', 20));
  }

  if (pathname === '/api/admin/me') {
    // TASK-MONO-784 S6 — the additive `memberships[]` field (per-company
    // roles). The sample operator's one company mirrors its `roles` so the
    // shape matches a real single-company response (no second company
    // invented — ADR-MONO-074 A4 "no extraction path" extends to not
    // fabricating a scenario the real demo seed doesn't have either).
    return { ...IAM_OPERATORS[0], memberships: [{ tenantId: SAMPLE_TENANT_ID, roles: IAM_OPERATORS[0].roles }] };
  }
  if (pathname === `${OPERATORS_PATH}/grantable-roles`) {
    return { roles: IAM_GRANTABLE_ROLES };
  }
  // TASK-MONO-777 — the operator e-mail lookup. No sample operator carries a
  // console `sub` (the account id the erp link needs), so every e-mail answers
  // the producer's «not found» shape — never an invented account id.
  if (pathname === `${OPERATORS_PATH}/lookup`) {
    return { content: [] };
  }
  const assignmentsMatch = pathname.match(
    new RegExp(`^${OPERATORS_PATH}/([^/]+)/assignments$`),
  );
  if (assignmentsMatch) {
    const operatorId = decodeURIComponent(assignmentsMatch[1]);
    const known = IAM_OPERATORS.some((o) => o.operatorId === operatorId);
    if (!known) {
      return fixtureNotFound('OPERATOR_NOT_FOUND', 'operator not found');
    }
    // Home-tenant-only sample operators — no explicit cross-tenant assignment.
    return { assignments: [] };
  }
  if (pathname !== OPERATORS_PATH) return undefined;

  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = status ? IAM_OPERATORS.filter((o) => o.status === status) : IAM_OPERATORS;
  return paginateContent(rows, page, size);
}

// ===========================================================================
// rbac — GET /api/admin/roles + GET /api/admin/permissions
// ===========================================================================

const ROLES_PATH = '/api/admin/roles';
const PERMISSIONS_PATH = '/api/admin/permissions';

export const IAM_PERMISSIONS = [
  'account.read',
  'account.lock',
  'operator.manage',
  'audit.read',
  'security.event.read',
  'org.manage',
  'group.manage',
  'tenant.manage',
  'subscription.manage',
];

export const IAM_ROLES = [
  {
    id: 1,
    name: `플랫폼 최고관리자${SUFFIX}`,
    description: `모든 테넌트·모든 기능에 접근할 수 있는 최상위 역할${SUFFIX}`,
    permissions: [...IAM_PERMISSIONS],
  },
  {
    id: 2,
    name: `테넌트 관리자${SUFFIX}`,
    description: `자기 테넌트 범위의 운영자·그룹을 관리하는 역할${SUFFIX}`,
    permissions: ['account.read', 'operator.manage', 'group.manage'],
  },
  {
    id: 3,
    name: `읽기 전용 지원${SUFFIX}`,
    description: `계정 조회만 가능한 지원 역할${SUFFIX}`,
    permissions: ['account.read'],
  },
  {
    id: 4,
    name: `보안 분석가${SUFFIX}`,
    description: `감사·보안 이벤트를 조회하는 역할${SUFFIX}`,
    permissions: ['audit.read', 'security.event.read'],
  },
];

function rbacFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname === ROLES_PATH) return { scope: 'global', roles: IAM_ROLES };
  if (pathname === PERMISSIONS_PATH) return { scope: 'global', permissions: IAM_PERMISSIONS };
  return undefined;
}

// ===========================================================================
// subscriptions — NO producer GET exists (ADR-MONO-023: "subscribed" is
// derived from the registry catalog — `features/subscriptions/components/
// SubscriptionsScreen.tsx` reads `getCatalog()`, already `ready` since
// TASK-PC-FE-282; `subscriptions-client.ts`'s `logPrefix: 'subscriptions'` is
// used ONLY by the two write endpoints). This handler exists solely to turn
// the ledger row `ready` (DoD: "원장의 IAM `pending` 0") — no real screen ever
// issues a GET on this surface, so it is never actually exercised by the app.
// ===========================================================================

function subscriptionsFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  if (pathname !== '/api/admin/subscriptions') return undefined;
  return {};
}

// ===========================================================================
// tenants — GET /api/admin/tenants (list) + GET /api/admin/tenants/{tenantId}
// ===========================================================================

const TENANTS_PATH = '/api/admin/tenants';

export const IAM_TENANTS = [
  {
    tenantId: SAMPLE_TENANT_ID,
    displayName: `샘플 테넌트${SUFFIX}`,
    tenantType: 'B2B_ENTERPRISE',
    status: 'ACTIVE',
    createdAt: '2025-10-01T00:00:00Z',
    updatedAt: '2026-09-01T00:00:00Z',
  },
  {
    tenantId: 'globex-sample',
    displayName: `글로벡스${SUFFIX}`,
    tenantType: 'B2B_ENTERPRISE',
    status: 'ACTIVE',
    createdAt: '2025-11-12T00:00:00Z',
    updatedAt: '2026-08-20T00:00:00Z',
  },
  {
    tenantId: 'initech-sample',
    displayName: `이니텍${SUFFIX}`,
    tenantType: 'B2C_CONSUMER',
    status: 'SUSPENDED',
    createdAt: '2026-01-05T00:00:00Z',
    updatedAt: '2026-07-15T00:00:00Z',
  },
] as const;

function tenantsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const detailMatch = pathname.match(new RegExp(`^${TENANTS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const tenantId = decodeURIComponent(detailMatch[1]);
    const found = IAM_TENANTS.find((t) => t.tenantId === tenantId);
    if (!found) return fixtureNotFound('TENANT_NOT_FOUND', 'tenant not found');
    return found;
  }

  if (pathname !== TENANTS_PATH) return undefined;
  const status = query.get('status');
  const tenantType = query.get('tenantType');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof IAM_TENANTS)[number][] = [...IAM_TENANTS];
  if (status) rows = rows.filter((t) => t.status === status);
  if (tenantType) rows = rows.filter((t) => t.tenantType === tenantType);
  return paginateItems(rows, page, size);
}

// ===========================================================================
// org_nodes — GET /api/admin/org-nodes (+ /{id}, /{id}/tenants, /{id}/admins)
// ===========================================================================

const ORG_NODES_PATH = '/api/admin/org-nodes';

export const IAM_ORG_NODES = [
  {
    orgNodeId: 'org-sample-0001',
    parentId: null,
    name: `본사${SUFFIX}`,
    depth: 1,
    ceiling: { mode: 'UNBOUNDED' },
    createdAt: '2025-09-01T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    orgNodeId: 'org-sample-0002',
    parentId: 'org-sample-0001',
    name: `영업본부${SUFFIX}`,
    depth: 2,
    ceiling: { mode: 'BOUNDED', domains: ['wms', 'erp'] },
    createdAt: '2025-09-10T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
  {
    orgNodeId: 'org-sample-0003',
    parentId: 'org-sample-0001',
    name: `물류센터${SUFFIX}`,
    depth: 2,
    ceiling: { mode: 'BOUNDED', domains: ['wms'] },
    createdAt: '2025-09-11T00:00:00Z',
    updatedAt: '2026-06-01T00:00:00Z',
  },
] as const;

const IAM_ORG_NODE_TENANTS: Readonly<Record<string, readonly string[]>> = {
  'org-sample-0001': [SAMPLE_TENANT_ID, 'globex-sample', 'initech-sample'],
  'org-sample-0002': ['globex-sample'],
  'org-sample-0003': [SAMPLE_TENANT_ID],
};

const IAM_ORG_NODE_ADMINS: Readonly<Record<string, readonly unknown[]>> = {
  'org-sample-0001': [
    {
      operatorId: 'op-sample-0001',
      displayName: `김하나${SUFFIX}`,
      roleName: 'ORG_ADMIN',
      grantedAt: '2025-09-02T00:00:00Z',
    },
  ],
  'org-sample-0002': [],
  'org-sample-0003': [],
};

function orgNodesFixture(path: string): unknown {
  const { pathname } = splitPath(path);

  const tenantsMatch = pathname.match(new RegExp(`^${ORG_NODES_PATH}/([^/]+)/tenants$`));
  if (tenantsMatch) {
    const id = decodeURIComponent(tenantsMatch[1]);
    if (!IAM_ORG_NODES.some((n) => n.orgNodeId === id)) {
      return fixtureNotFound('ORG_NODE_NOT_FOUND', 'org node not found');
    }
    return { tenantIds: IAM_ORG_NODE_TENANTS[id] ?? [] };
  }

  const adminsMatch = pathname.match(new RegExp(`^${ORG_NODES_PATH}/([^/]+)/admins$`));
  if (adminsMatch) {
    const id = decodeURIComponent(adminsMatch[1]);
    if (!IAM_ORG_NODES.some((n) => n.orgNodeId === id)) {
      return fixtureNotFound('ORG_NODE_NOT_FOUND', 'org node not found');
    }
    return { items: IAM_ORG_NODE_ADMINS[id] ?? [] };
  }

  const detailMatch = pathname.match(new RegExp(`^${ORG_NODES_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = IAM_ORG_NODES.find((n) => n.orgNodeId === id);
    if (!found) return fixtureNotFound('ORG_NODE_NOT_FOUND', 'org node not found');
    return found;
  }

  if (pathname === ORG_NODES_PATH) return { items: IAM_ORG_NODES };
  return undefined;
}

// ===========================================================================
// groups — GET /api/admin/groups (+ /{id}, /{id}/members, /{id}/grants)
// ===========================================================================

const GROUPS_PATH = '/api/admin/groups';

export const IAM_GROUPS = [
  {
    groupId: 'group-sample-0001',
    tenantId: SAMPLE_TENANT_ID,
    name: `영업1팀${SUFFIX}`,
    description: `영업1팀 운영자 그룹${SUFFIX}`,
    memberCount: 2,
    grantCount: 1,
    createdAt: '2026-01-20T00:00:00Z',
    updatedAt: '2026-08-01T00:00:00Z',
  },
  {
    groupId: 'group-sample-0002',
    tenantId: SAMPLE_TENANT_ID,
    name: `보안 담당${SUFFIX}`,
    description: null,
    memberCount: 1,
    grantCount: 2,
    createdAt: '2026-02-14T00:00:00Z',
    updatedAt: '2026-08-05T00:00:00Z',
  },
] as const;

const IAM_GROUP_MEMBERS: Readonly<Record<string, readonly unknown[]>> = {
  'group-sample-0001': [
    { operatorId: 'op-sample-0002', displayName: `이민준${SUFFIX}`, addedAt: '2026-01-21T00:00:00Z' },
    { operatorId: 'op-sample-0003', displayName: `박소연${SUFFIX}`, addedAt: '2026-01-22T00:00:00Z' },
  ],
  'group-sample-0002': [
    { operatorId: 'op-sample-0005', displayName: `장유나${SUFFIX}`, addedAt: '2026-02-15T00:00:00Z' },
  ],
};

const IAM_GROUP_GRANTS: Readonly<Record<string, readonly unknown[]>> = {
  'group-sample-0001': [
    { grantId: 'grant-sample-0001', type: 'ROLE', roleName: 'TENANT_ADMIN', grantedAt: '2026-01-21T00:00:00Z' },
  ],
  'group-sample-0002': [
    { grantId: 'grant-sample-0002', type: 'ROLE', roleName: 'SECURITY_ANALYST', grantedAt: '2026-02-15T00:00:00Z' },
    {
      grantId: 'grant-sample-0003',
      type: 'TENANT_ASSIGNMENT',
      tenantId: SAMPLE_TENANT_ID,
      grantedAt: '2026-02-16T00:00:00Z',
    },
  ],
};

function groupsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  const membersMatch = pathname.match(new RegExp(`^${GROUPS_PATH}/([^/]+)/members$`));
  if (membersMatch) {
    const id = decodeURIComponent(membersMatch[1]);
    if (!IAM_GROUPS.some((g) => g.groupId === id)) {
      return fixtureNotFound('GROUP_NOT_FOUND', 'group not found');
    }
    return { items: IAM_GROUP_MEMBERS[id] ?? [] };
  }

  const grantsMatch = pathname.match(new RegExp(`^${GROUPS_PATH}/([^/]+)/grants$`));
  if (grantsMatch) {
    const id = decodeURIComponent(grantsMatch[1]);
    if (!IAM_GROUPS.some((g) => g.groupId === id)) {
      return fixtureNotFound('GROUP_NOT_FOUND', 'group not found');
    }
    return { items: IAM_GROUP_GRANTS[id] ?? [] };
  }

  const detailMatch = pathname.match(new RegExp(`^${GROUPS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const id = decodeURIComponent(detailMatch[1]);
    const found = IAM_GROUPS.find((g) => g.groupId === id);
    if (!found) return fixtureNotFound('GROUP_NOT_FOUND', 'group not found');
    return found;
  }

  if (pathname !== GROUPS_PATH) return undefined;
  const tenantId = query.get('tenantId');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  const rows = tenantId ? IAM_GROUPS.filter((g) => g.tenantId === tenantId) : IAM_GROUPS;
  return paginateItems(rows, page, size);
}

// ===========================================================================
// partnerships — GET /api/admin/partnerships (host-side + partner-side)
// ===========================================================================

const PARTNERSHIPS_PATH = '/api/admin/partnerships';

export const IAM_PARTNERSHIPS = [
  {
    partnershipId: 'partnership-sample-0001',
    hostTenantId: SAMPLE_TENANT_ID,
    partnerTenantId: 'globex-sample',
    status: 'ACTIVE',
    delegatedScope: { domains: ['wms', 'erp'], roles: ['SUPPORT_READONLY'] },
    myRole: 'host',
    invitedAt: '2026-04-01T00:00:00Z',
    acceptedAt: '2026-04-02T00:00:00Z',
    participantCount: 1,
  },
  {
    partnershipId: 'partnership-sample-0002',
    hostTenantId: 'initech-sample',
    partnerTenantId: SAMPLE_TENANT_ID,
    status: 'PENDING',
    delegatedScope: { domains: ['finance'], roles: [] },
    myRole: 'partner',
    invitedAt: '2026-05-10T00:00:00Z',
    acceptedAt: null,
    participantCount: 0,
  },
  {
    partnershipId: 'partnership-sample-0003',
    hostTenantId: SAMPLE_TENANT_ID,
    partnerTenantId: 'initech-sample',
    status: 'SUSPENDED',
    delegatedScope: { domains: ['scm'], roles: ['SUPPORT_LOCK'] },
    myRole: 'host',
    invitedAt: '2026-03-01T00:00:00Z',
    acceptedAt: '2026-03-03T00:00:00Z',
    participantCount: 2,
  },
] as const;

function partnershipsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== PARTNERSHIPS_PATH) return undefined;

  const role = query.get('role');
  const status = query.get('status');
  const page = intParam(query, 'page', 0);
  const size = intParam(query, 'size', 20);
  let rows: (typeof IAM_PARTNERSHIPS)[number][] = [...IAM_PARTNERSHIPS];
  if (role) rows = rows.filter((p) => p.myRole === role);
  if (status) rows = rows.filter((p) => p.status === status);
  return paginateItems(rows, page, size);
}

// ===========================================================================
// metrics — GET /api/admin/metrics?period=&tenantId= (the 개요 «실적» strip,
// ADR-MONO-084 P6, TASK-MONO-811)
// ===========================================================================
//
// Derived from the SAME seed rows the 계정 and 감사 screens show — never a
// separate table of invented totals — so a visitor who opens the audit list
// from the strip's month finds the very events it counted:
//   - 로그인 성공 / 실패 (`loginSuccess` / `loginFailure`) — `login_history`
//     audit rows with outcome SUCCESS / FAILURE, by `occurredAt`.
//   - 계정 잠금 (`accountLocked`) — `ACCOUNT_LOCK` admin audit rows, by
//     `occurredAt`. (The seed's two LOCKED accounts carry no lock time, so the
//     lock events are the only honest source.)
//   - 신규 계정 (`newAccount`) — accounts by `createdAt`. Every seed account
//     was created before the sample windows, so this is honestly "0".
// The sample world has one tenant and no consumer pool, so the producer's
// consumer-pool exclusion has nothing to drop here. `tenantId` is not
// filtered (like every other IAM fixture — the visitor sees the sample tenant).

const METRICS_PATH = '/api/admin/metrics';

const IAM_METRIC_KEYS = ['loginSuccess', 'loginFailure', 'accountLocked', 'newAccount'] as const;
type IamMetricKey = (typeof IAM_METRIC_KEYS)[number];

/** Each metric's attributed instants, from the seed rows. */
const IAM_METRIC_INSTANTS: Readonly<Record<IamMetricKey, readonly string[]>> = {
  loginSuccess: IAM_AUDIT_LOGIN_ROWS.filter((r) => r.outcome === 'SUCCESS').map((r) => r.occurredAt),
  loginFailure: IAM_AUDIT_LOGIN_ROWS.filter((r) => r.outcome === 'FAILURE').map((r) => r.occurredAt),
  accountLocked: IAM_AUDIT_ADMIN_ROWS.filter((r) => r.actionCode === 'ACCOUNT_LOCK').map(
    (r) => r.occurredAt,
  ),
  newAccount: IAM_ACCOUNTS.map((a) => a.createdAt),
};

function countIn(key: IamMetricKey, win: SampleWindow, date?: string): string {
  return String(
    IAM_METRIC_INSTANTS[key].filter(
      (at) => instantInside(at, win) && (date === undefined || kstDate(at) === date),
    ).length,
  );
}

function computeIamMetrics(period: SamplePeriod) {
  const w = SAMPLE_METRICS_WINDOWS[period];
  return {
    period,
    timezone: 'Asia/Seoul',
    current: w.current,
    previous: w.previous,
    // Integer STRINGS, like the producer (F5); all counts.
    metrics: IAM_METRIC_KEYS.map((key) => ({
      key,
      unit: 'count',
      currency: null,
      current: countIn(key, w.current),
      previous: countIn(key, w.previous),
    })),
    // Every date of the current window, zero-filled.
    series: w.dates.map((date) => ({
      date,
      values: Object.fromEntries(IAM_METRIC_KEYS.map((key) => [key, countIn(key, w.current, date)])),
    })),
  };
}

function metricsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);
  if (pathname !== METRICS_PATH) return undefined;
  const p = samplePeriod(query);
  // No / unknown period: the producer answers 400; the console always sends
  // one, so the sample leaves it unanswered (-> the router's 503 «not ready»).
  return p ? computeIamMetrics(p) : undefined;
}

// ===========================================================================
// domain_permission_sets — GET /api/admin/domain-roles (catalogue) +
// GET /api/admin/domain-permission-sets[/{setId}] (ADR-MONO-083, TASK-PC-FE-382)
// ===========================================================================
//
// Wire shapes are the PRODUCER's (`admin-api.md` § 도메인 업무 권한 세트 — « Domain
// role wire shape » / « Domain permission set wire shape »), not a console
// re-modelling: `{domainKey, role, access, fineGrained, label}` and
// `{setId, tenantId, systemKey, readOnly, name, description, roles[{domainKey,role}],
// domains, grantCount, groupCount, createdAt, updatedAt}`.
//
// 🔴 Role keys, preset `systemKey`s and the presets' fixed `setId`s are the REAL
// ones (the catalogue table + § 시스템 프리셋 + the V0052/V0056–V0059 seed): the
// screens look labels up by key, and a made-up key renders as «unknown». The only
// invented rows are the two TENANT sets (named with «(샘플)»). The catalogue's
// `label` and the presets' `name`/`description` carry the suffix too (R2ⓐ — the
// label guard treats `label`/`name`/`description` as human-readable).
//
// Writes (POST/PATCH/DELETE) need no handler: the router refuses every non-GET
// with 403 SAMPLE_READ_ONLY (ADR-074 R1ⓐ).
//
// The sample world has ONE acting tenant ({@link SAMPLE_TENANT_ID}) and the
// router hands a fixture only the path (no `X-Tenant-Id`), so — like every other
// IAM fixture — the list is the sample tenant's: presets first, then its sets.

const DOMAIN_ROLES_PATH = '/api/admin/domain-roles';
const DOMAIN_PERMISSION_SETS_PATH = '/api/admin/domain-permission-sets';

type DomainAccess = 'READ' | 'WRITE' | 'OPERATE';

/** `[domainKey, role, access, label, fineGrained]` — the catalogue table, in its order. */
const DOMAIN_ROLE_ROWS: readonly (readonly [string, string, DomainAccess, string, boolean])[] = [
  ['wms', 'INVENTORY_READ', 'READ', '재고 조회', true],
  ['wms', 'INVENTORY_WRITE', 'WRITE', '재고 변경(조정 · 이동)', true],
  ['wms', 'INBOUND_READ', 'READ', '입고 조회', true],
  ['wms', 'INBOUND_WRITE', 'WRITE', '입고 변경', true],
  ['wms', 'OUTBOUND_READ', 'READ', '출고 조회', true],
  ['wms', 'OUTBOUND_WRITE', 'WRITE', '출고 변경', true],
  ['wms', 'MASTER_READ', 'READ', '기준정보 조회', true],
  ['wms', 'WMS_OPERATOR', 'OPERATE', 'WMS 운영 조치(알림 확인 · 운영 조치 — 업무 API 를 열지 않는다)', true],
  ['scm', 'SCM_PROCUREMENT_READ', 'READ', '발주 조회', true],
  ['scm', 'SCM_PROCUREMENT_WRITE', 'WRITE', '발주 변경(생성 · 제출 · 확정 · 취소 · 공급사 등록)', true],
  ['scm', 'SCM_DEMAND_PLANNING_READ', 'READ', '수요계획 조회', true],
  ['scm', 'SCM_DEMAND_PLANNING_WRITE', 'WRITE', '수요계획 변경(승인 · 기각 · 정책 · 매핑)', true],
  ['scm', 'SCM_LOGISTICS_READ', 'READ', '물류(배차) 조회', true],
  ['scm', 'SCM_LOGISTICS_WRITE', 'WRITE', '물류 변경(발송 재시도)', true],
  ['scm', 'SCM_INVENTORY_VISIBILITY_READ', 'READ', '재고 가시성 조회', true],
  ['scm', 'SCM_INVENTORY_VISIBILITY_WRITE', 'WRITE', '재고 가시성 변경(3PL 노드 등록 · 관측 재고)', true],
  ['erp', 'ERP_MASTERDATA_READ', 'READ', '기준정보 조회', true],
  ['erp', 'ERP_MASTERDATA_WRITE', 'WRITE', '기준정보 변경(생성 · 수정 · 퇴역 · 부서 이동 · 계정 연결 제안/철회)', true],
  ['erp', 'ERP_APPROVAL_READ', 'READ', '결재 조회', true],
  ['erp', 'ERP_APPROVAL_WRITE', 'WRITE', '결재 변경(상신 · 승인 · 반려 · 회수 · 위임)', true],
  ['finance', 'FINANCE_ACCOUNT_READ', 'READ', '계좌 조회', true],
  ['finance', 'FINANCE_ACCOUNT_WRITE', 'WRITE', '계좌 변경(개설 · KYC 승급 · 충전 · 홀드 · 이체)', true],
  ['finance', 'FINANCE_LEDGER_READ', 'READ', '원장 조회', true],
  ['finance', 'FINANCE_LEDGER_WRITE', 'WRITE', '원장 업무 변경(수동 분개 · 정산 · 재평가 · 명세 수입 · 대사 해소 · 환율 갱신)', true],
  ['finance', 'FINANCE_LEDGER_CONTROL', 'WRITE', '원장 통제(기간 개시 · 마감 · 환율 덮어쓰기 · 환차 허용오차 · 원가흐름 설정)', true],
  ['ecommerce', 'ECOMMERCE_ORDER_READ', 'READ', '주문 조회', true],
  ['ecommerce', 'ECOMMERCE_ORDER_WRITE', 'WRITE', '주문 변경(상태 전이)', true],
  ['ecommerce', 'ECOMMERCE_PRODUCT_READ', 'READ', '상품 · 판매자 조회', true],
  ['ecommerce', 'ECOMMERCE_PRODUCT_WRITE', 'WRITE', '상품 · 판매자 변경(상품 · 옵션 · 재고 · 이미지 · 판매자 · 구성원 초대 · 검색 재색인)', true],
  ['ecommerce', 'ECOMMERCE_MEMBER_READ', 'READ', '회원 조회', true],
  ['ecommerce', 'ECOMMERCE_SETTLEMENT_READ', 'READ', '정산 조회', true],
  ['ecommerce', 'ECOMMERCE_SETTLEMENT_WRITE', 'WRITE', '정산 변경(수수료율 · 기간 개시 · 마감 · 지급 실행)', true],
  ['ecommerce', 'ECOMMERCE_PROMOTION_READ', 'READ', '프로모션 조회', true],
  ['ecommerce', 'ECOMMERCE_PROMOTION_WRITE', 'WRITE', '프로모션 변경(등록 · 수정 · 삭제 · 쿠폰 발급)', true],
  ['ecommerce', 'ECOMMERCE_SHIPPING_READ', 'READ', '배송 조회', true],
  ['ecommerce', 'ECOMMERCE_SHIPPING_WRITE', 'WRITE', '배송 변경(상태 · 추적 갱신)', true],
  ['ecommerce', 'ECOMMERCE_NOTIFICATION_READ', 'READ', '알림 템플릿 조회', true],
  ['ecommerce', 'ECOMMERCE_NOTIFICATION_WRITE', 'WRITE', '알림 템플릿 변경(등록 · 수정)', true],
  ['fan', 'FAN_OPERATOR', 'OPERATE', 'fan 전체 업무(세분 미지원)', false],
  ['mes', 'MES_OPERATOR', 'OPERATE', 'MES 전체 업무(세분 미지원)', false],
];

export const IAM_DOMAIN_ROLES = DOMAIN_ROLE_ROWS.map(([domainKey, role, access, label, fineGrained]) => ({
  domainKey,
  role,
  access,
  fineGrained,
  label: `${label}${SUFFIX}`,
}));

const DOMAIN_ROLE_ORDER = new Map(DOMAIN_ROLE_ROWS.map(([, role], i) => [role, i]));
const DOMAIN_OF_ROLE = new Map(DOMAIN_ROLE_ROWS.map(([domainKey, role]) => [role, domainKey]));

/** Catalogue order, no duplicates (the producer's `roles` rule); `domains` ascending. */
function setRoles(roleNames: readonly string[]) {
  const sorted = [...new Set(roleNames)].sort(
    (a, b) => (DOMAIN_ROLE_ORDER.get(a) ?? 0) - (DOMAIN_ROLE_ORDER.get(b) ?? 0),
  );
  const roles = sorted.map((role) => ({ domainKey: DOMAIN_OF_ROLE.get(role) as string, role }));
  const domains = [...new Set(roles.map((r) => r.domainKey))].sort();
  return { roles, domains };
}

const rolesOfDomain = (domainKey: string) =>
  DOMAIN_ROLE_ROWS.filter(([d]) => d === domainKey).map(([, role]) => role);
const ALL_ROLE_NAMES = DOMAIN_ROLE_ROWS.map(([, role]) => role);

/**
 * `[setId suffix, systemKey, name, description, role names, grantCount, groupCount]`
 * — § 시스템 프리셋, in the producer's list order. `setId` = the seed's fixed
 * `00000083-0000-7000-8000-0000000000NN`; `name`/`description` = the seed's text
 * (V0052/V0056–V0059). The counts are the SAMPLE tenant's (synthetic).
 */
const PRESET_ROWS: readonly (readonly [
  string,
  string,
  string,
  string,
  readonly string[],
  number,
  number,
])[] = [
  ['001', 'wms-inventory-read', '재고 조회', 'WMS 재고 조회', ['INVENTORY_READ'], 2, 0],
  ['002', 'wms-inventory-operate', '재고 운영', 'WMS 재고 조회 · 변경', ['INVENTORY_READ', 'INVENTORY_WRITE'], 1, 1],
  ['003', 'wms-inbound-operate', '입고 운영', 'WMS 입고 조회 · 변경', ['INBOUND_READ', 'INBOUND_WRITE'], 0, 0],
  ['004', 'wms-outbound-operate', '출고 운영', 'WMS 출고 조회 · 변경', ['OUTBOUND_READ', 'OUTBOUND_WRITE'], 0, 0],
  ['005', 'wms-master-read', '기준정보 조회', 'WMS 기준정보 조회', ['MASTER_READ'], 0, 0],
  ['006', 'wms-ops-actions', 'WMS 운영 조치', 'WMS 알림 확인 · 운영 조치', ['WMS_OPERATOR'], 0, 0],
  ['007', 'wms-full', 'WMS 전체', 'WMS 카탈로그 역할 전부', rolesOfDomain('wms'), 0, 0],
  ['008', 'scm-full', 'SCM 전체', 'SCM 전체 업무(세분 미지원)', rolesOfDomain('scm'), 0, 0],
  ['016', 'scm-read', 'SCM 조회', 'SCM 발주 · 수요계획 · 물류 · 재고 가시성 조회', [
    'SCM_PROCUREMENT_READ',
    'SCM_DEMAND_PLANNING_READ',
    'SCM_LOGISTICS_READ',
    'SCM_INVENTORY_VISIBILITY_READ',
  ], 1, 0],
  ['017', 'scm-procurement-operate', '발주 운영', 'SCM 발주 조회 · 변경', ['SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE'], 0, 0],
  ['018', 'scm-demand-planning-operate', '수요계획 운영', 'SCM 수요계획 조회 · 변경', ['SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE'], 0, 0],
  ['019', 'scm-logistics-operate', '물류 운영', 'SCM 물류(배차) 조회 · 발송 재시도', ['SCM_LOGISTICS_READ', 'SCM_LOGISTICS_WRITE'], 0, 0],
  ['020', 'scm-inventory-visibility-operate', '재고 가시성 운영', 'SCM 재고 가시성 조회 · 3PL 온보딩', ['SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE'], 0, 0],
  ['009', 'erp-full', 'ERP 전체', 'ERP 전체 업무(세분 미지원)', rolesOfDomain('erp'), 0, 0],
  ['021', 'erp-read', 'ERP 조회', 'ERP 기준정보 · 결재 조회', ['ERP_MASTERDATA_READ', 'ERP_APPROVAL_READ'], 1, 0],
  ['022', 'erp-masterdata-operate', '기준정보 운영', 'ERP 기준정보 조회 · 변경', ['ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE'], 0, 0],
  ['023', 'erp-approval-operate', '결재 운영', 'ERP 결재 조회 · 변경', ['ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'], 0, 0],
  ['010', 'finance-full', 'Finance 전체', 'Finance 전체 업무(세분 미지원)', rolesOfDomain('finance'), 0, 0],
  ['024', 'finance-read', 'Finance 조회', 'Finance 계좌 · 원장 조회', ['FINANCE_ACCOUNT_READ', 'FINANCE_LEDGER_READ'], 1, 0],
  ['025', 'finance-account-operate', '계좌 운영', 'Finance 계좌 조회 · 변경', ['FINANCE_ACCOUNT_READ', 'FINANCE_ACCOUNT_WRITE'], 0, 0],
  ['026', 'finance-ledger-operate', '원장 운영', 'Finance 원장 조회 · 업무 변경(통제 제외)', ['FINANCE_LEDGER_READ', 'FINANCE_LEDGER_WRITE'], 0, 0],
  ['011', 'ecommerce-full', 'E-commerce 전체', 'E-commerce 전체 업무(세분 미지원)', rolesOfDomain('ecommerce'), 0, 0],
  ['027', 'ecommerce-read', 'E-commerce 조회', 'E-commerce 주문 · 상품 · 회원 · 정산 · 프로모션 · 배송 · 알림 조회', [
    'ECOMMERCE_ORDER_READ',
    'ECOMMERCE_PRODUCT_READ',
    'ECOMMERCE_MEMBER_READ',
    'ECOMMERCE_SETTLEMENT_READ',
    'ECOMMERCE_PROMOTION_READ',
    'ECOMMERCE_SHIPPING_READ',
    'ECOMMERCE_NOTIFICATION_READ',
  ], 1, 0],
  ['028', 'ecommerce-order-operate', '주문 운영', 'E-commerce 주문 조회 · 변경', ['ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE'], 0, 0],
  ['029', 'ecommerce-product-operate', '상품 운영', 'E-commerce 상품 · 판매자 조회 · 변경', ['ECOMMERCE_PRODUCT_READ', 'ECOMMERCE_PRODUCT_WRITE'], 0, 0],
  ['030', 'ecommerce-settlement-operate', '정산 운영', 'E-commerce 정산 조회 · 변경', ['ECOMMERCE_SETTLEMENT_READ', 'ECOMMERCE_SETTLEMENT_WRITE'], 0, 0],
  ['031', 'ecommerce-promotion-operate', '프로모션 운영', 'E-commerce 프로모션 조회 · 변경', ['ECOMMERCE_PROMOTION_READ', 'ECOMMERCE_PROMOTION_WRITE'], 0, 0],
  ['032', 'ecommerce-shipping-operate', '배송 운영', 'E-commerce 배송 조회 · 변경', ['ECOMMERCE_SHIPPING_READ', 'ECOMMERCE_SHIPPING_WRITE'], 0, 0],
  ['033', 'ecommerce-notification-operate', '알림 운영', 'E-commerce 알림 템플릿 조회 · 변경', ['ECOMMERCE_NOTIFICATION_READ', 'ECOMMERCE_NOTIFICATION_WRITE'], 0, 0],
  ['012', 'fan-full', 'fan 전체', 'fan 전체 업무(세분 미지원)', rolesOfDomain('fan'), 0, 0],
  ['013', 'mes-full', 'MES 전체', 'MES 전체 업무(세분 미지원)', rolesOfDomain('mes'), 0, 0],
  ['014', 'all-domains-full', '모든 도메인 전체', '카탈로그 역할 전부', ALL_ROLE_NAMES, 1, 0],
  ['015', 'legacy-compat', '이전 호환', '이전 백필 전용(MIGRATION) — 카탈로그 역할 전부', ALL_ROLE_NAMES, 0, 0],
];

const PRESET_CREATED_AT = '2026-10-14T00:00:00Z';

const IAM_DOMAIN_PRESETS = PRESET_ROWS.map(
  ([n, systemKey, name, description, roleNames, grantCount, groupCount]) => ({
    setId: `00000083-0000-7000-8000-000000000${n}`,
    tenantId: null,
    systemKey,
    readOnly: true,
    name: `${name}${SUFFIX}`,
    description: `${description}${SUFFIX}`,
    ...setRoles(roleNames),
    grantCount,
    groupCount,
    createdAt: PRESET_CREATED_AT,
    updatedAt: PRESET_CREATED_AT,
  }),
);

/** The sample tenant's own sets (the contract's wire-shape example id range `…d001`). */
const IAM_DOMAIN_TENANT_SETS = [
  {
    setId: '0192f3a0-0000-7000-8000-00000000d001',
    tenantId: SAMPLE_TENANT_ID,
    systemKey: null,
    readOnly: false,
    name: `재고팀${SUFFIX}`,
    description: `재고 조회 · 변경${SUFFIX}`,
    ...setRoles(['INVENTORY_READ', 'INVENTORY_WRITE']),
    grantCount: 4,
    groupCount: 1,
    createdAt: '2026-10-14T09:00:00Z',
    updatedAt: '2026-10-14T09:00:00Z',
  },
  {
    setId: '0192f3a0-0000-7000-8000-00000000d002',
    tenantId: SAMPLE_TENANT_ID,
    systemKey: null,
    readOnly: false,
    name: `발주 · 결재 담당${SUFFIX}`,
    description: null,
    ...setRoles(['SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE', 'ERP_APPROVAL_READ']),
    grantCount: 2,
    groupCount: 0,
    createdAt: '2026-10-15T01:30:00Z',
    updatedAt: '2026-10-16T05:00:00Z',
  },
] as const;

/** Presets first (§ 시스템 프리셋 order), then the tenant's sets by `createdAt` ascending. */
export const IAM_DOMAIN_PERMISSION_SETS = [...IAM_DOMAIN_PRESETS, ...IAM_DOMAIN_TENANT_SETS];

function domainPermissionSetsFixture(path: string): unknown {
  const { pathname } = splitPath(path);

  if (pathname === DOMAIN_ROLES_PATH) return { items: IAM_DOMAIN_ROLES };
  if (pathname === DOMAIN_PERMISSION_SETS_PATH) return { items: IAM_DOMAIN_PERMISSION_SETS };

  const detailMatch = pathname.match(new RegExp(`^${DOMAIN_PERMISSION_SETS_PATH}/([^/]+)$`));
  if (detailMatch) {
    const setId = decodeURIComponent(detailMatch[1]);
    const found = IAM_DOMAIN_PERMISSION_SETS.find((s) => s.setId === setId);
    if (!found) {
      return fixtureNotFound('DOMAIN_PERMISSION_SET_NOT_FOUND', 'domain permission set not found');
    }
    return found;
  }
  return undefined;
}

// ===========================================================================
// tenant_entry_policy — GET /api/admin/tenants/{tenantId}/entry-policy
// (+ /enrolment-summary) (TASK-MONO-771 S5 console slice, TASK-PC-FE-382)
// ===========================================================================
//
// Producer shapes (`admin-api.md` § Tenant Entry Policy): `{tenantId, requireMfa,
// updatedAt, updatedBy}` (every key always present; `null` stamps = never set) and
// `{tenantId, operators, enrolled, notEnrolled, unlinked}`.
//
// One policy per REGISTRY tenant ({@link IAM_TENANTS}). Any other tenantId answers
// 200 with the contract's «no row» default `{tenantId, requireMfa:false, updatedAt:null,
// updatedBy:null}` — the producer GET never 404s (꺼짐은 정상 상태) and does no tenant
// existence check on reads. The summary of such a tenant is all zeros (it has no
// members), consistent with the policy endpoint; the contract is silent there.
//
// The enrolment summary is DERIVED from {@link IAM_OPERATORS} (ACTIVE operators ·
// `totpEnrolled`), so it agrees with the 운영자 screen: `operators = enrolled +
// notEnrolled + unlinked`. The operators fixture is tenant-agnostic (the visitor
// sees one operator pool), so every sample tenant reports the same counts. Every
// seed operator has a linked account → `unlinked` is honestly 0.

const ENTRY_POLICY_PATH_RE = new RegExp(
  `^${TENANTS_PATH}/([^/]+)/entry-policy(/enrolment-summary)?$`,
);

const ENTRY_POLICY_BY_TENANT: Readonly<
  Record<string, { requireMfa: boolean; updatedAt: string | null; updatedBy: string | null }>
> = {
  // 켜 둔 테넌트 — 켠 사람 · 시각이 보인다.
  [SAMPLE_TENANT_ID]: {
    requireMfa: true,
    updatedAt: '2026-09-10T03:00:00Z',
    updatedBy: 'op-sample-0001',
  },
  // 한 번도 안 켠 테넌트 — 행이 없을 때의 응답(`false` + null 스탬프).
  'globex-sample': { requireMfa: false, updatedAt: null, updatedBy: null },
  // 켰다가 끈 테넌트 — 끄기는 행을 지우지 않으므로 마지막 변경자 · 시각이 남는다.
  'initech-sample': {
    requireMfa: false,
    updatedAt: '2026-08-01T00:00:00Z',
    updatedBy: 'op-sample-0002',
  },
};

export const IAM_ENTRY_POLICIES = IAM_TENANTS.map((t) => ({
  tenantId: t.tenantId,
  ...ENTRY_POLICY_BY_TENANT[t.tenantId],
}));

const ACTIVE_OPERATORS = IAM_OPERATORS.filter((o) => o.status === 'ACTIVE');
const ENROLLED_OPERATORS = ACTIVE_OPERATORS.filter((o) => o.totpEnrolled).length;

export const IAM_ENROLMENT_SUMMARIES = IAM_TENANTS.map((t) => ({
  tenantId: t.tenantId,
  operators: ACTIVE_OPERATORS.length,
  enrolled: ENROLLED_OPERATORS,
  notEnrolled: ACTIVE_OPERATORS.length - ENROLLED_OPERATORS,
  unlinked: 0,
}));

function tenantEntryPolicyFixture(path: string): unknown {
  const { pathname } = splitPath(path);
  const match = pathname.match(ENTRY_POLICY_PATH_RE);
  if (!match) return undefined;

  const tenantId = decodeURIComponent(match[1]);
  if (match[2]) {
    return (
      IAM_ENROLMENT_SUMMARIES.find((r) => r.tenantId === tenantId) ?? {
        tenantId,
        operators: 0,
        enrolled: 0,
        notEnrolled: 0,
        unlinked: 0,
      }
    );
  }
  // 행이 없으면 404 가 아니라 «꺼짐» 기본 본문 — 계약 § GET entry-policy.
  return (
    IAM_ENTRY_POLICIES.find((r) => r.tenantId === tenantId) ?? {
      tenantId,
      requireMfa: false,
      updatedAt: null,
      updatedBy: null,
    }
  );
}

// ===========================================================================
// exports
// ===========================================================================

export const IAM_FIXTURE_HANDLERS: Readonly<Record<string, IamFixtureHandler>> = {
  'iam:accounts': accountsFixture,
  'iam:audit': auditFixture,
  'iam:operators': operatorsFixture,
  'iam:rbac': rbacFixture,
  'iam:subscriptions': subscriptionsFixture,
  'iam:tenants': tenantsFixture,
  'iam:org_nodes': orgNodesFixture,
  'iam:groups': groupsFixture,
  'iam:partnerships': partnershipsFixture,
  'iam:iam_metrics': metricsFixture,
  // TASK-PC-FE-382
  'iam:domain_permission_sets': domainPermissionSetsFixture,
  'iam:tenant_entry_policy': tenantEntryPolicyFixture,
};

/**
 * The AGGREGATE of every seed array per surface (not just one branch's
 * output) — so the R2ⓐ label guard (`sample-label-rule.test.ts`) scans every
 * string any path on that surface could ever answer with, including the
 * sub-resource maps (org-node tenants/admins, group members/grants) that a
 * single representative request would not reach.
 */
export const IAM_FIXTURE_DOCUMENTS: Readonly<Record<string, unknown>> = {
  'iam:accounts': { content: IAM_ACCOUNTS },
  'iam:audit': { content: IAM_AUDIT_ROWS },
  'iam:operators': {
    content: IAM_OPERATORS,
    grantableRoles: IAM_GRANTABLE_ROLES,
    invitations: IAM_OPERATOR_INVITATIONS,
  },
  'iam:rbac': { roles: IAM_ROLES, permissions: IAM_PERMISSIONS },
  'iam:subscriptions': {},
  'iam:tenants': { items: IAM_TENANTS },
  // 🔴 `IAM_ORG_NODE_TENANTS`/`IAM_ORG_NODE_ADMINS`/`IAM_GROUP_MEMBERS`/
  // `IAM_GROUP_GRANTS` are Record<orgNodeId|groupId, Row[]> maps — nesting
  // them AS-IS under a document key means the label guard classifies each
  // row's strings by the MAP's id key ('org-sample-0001', …), not by a
  // semantic key, and an id key is itself unclassified → false
  // `unclassified-key` violations (caught by the guard bite below).
  // `Object.values(...).flat()` drops the id-keying and re-exposes the rows
  // as a plain array under a real, classified key — array items are OBJECTS,
  // so `findLabelViolations` uses each row's OWN keys regardless of the
  // array's own key name.
  'iam:org_nodes': {
    items: IAM_ORG_NODES,
    tenantIds: Object.values(IAM_ORG_NODE_TENANTS).flat(),
    admins: Object.values(IAM_ORG_NODE_ADMINS).flat(),
  },
  'iam:groups': {
    items: IAM_GROUPS,
    members: Object.values(IAM_GROUP_MEMBERS).flat(),
    grants: Object.values(IAM_GROUP_GRANTS).flat(),
  },
  'iam:partnerships': { items: IAM_PARTNERSHIPS },
  'iam:iam_metrics': { week: computeIamMetrics('week'), month: computeIamMetrics('month') },
  // TASK-PC-FE-382 — `catalogue`/`sets`/`policies`/`summaries` are arrays of OBJECTS,
  // so each row is classified by its OWN keys (the array's key name is not read).
  'iam:domain_permission_sets': { catalogue: IAM_DOMAIN_ROLES, sets: IAM_DOMAIN_PERMISSION_SETS },
  'iam:tenant_entry_policy': { policies: IAM_ENTRY_POLICIES, summaries: IAM_ENROLMENT_SUMMARIES },
};
