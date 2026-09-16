import { SAMPLE_LABEL_SUFFIX, SAMPLE_TENANT_ID } from '../codes';
import { fixtureNotFound } from '../router';

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

function operatorsFixture(path: string): unknown {
  const { pathname, query } = splitPath(path);

  if (pathname === '/api/admin/me') {
    return IAM_OPERATORS[0];
  }
  if (pathname === `${OPERATORS_PATH}/grantable-roles`) {
    return { roles: IAM_GRANTABLE_ROLES };
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
};
