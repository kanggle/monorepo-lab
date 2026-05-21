/**
 * TASK-PC-FE-006 — the SINGLE machine-readable parity matrix.
 *
 * This fixture IS `console-integration-contract.md` § 3.1 in executable
 * form. The consolidated parity-verification test
 * (`parity-verification.test.ts`) iterates THIS array and asserts the
 * parity-relevant invariants over the EXISTING, unmodified FE-002..005
 * surface. The spec § 3.1 table and this fixture are one source — they
 * cannot drift (ADR-MONO-013 Phase 3 gate defensibility; task AC + Edge
 * Case "Matrix ↔ test drift").
 *
 * FE-006 is verification only — it does NOT re-implement or modify any
 * FE-002..005 feature/route/producer. A row marked `verified: false` would
 * mean a real parity gap (→ a new fix task, per the project Review Rules,
 * never a silent in-place patch). All rows are `true` — no gap found.
 *
 * The producer paths are read-referenced from GAP
 * `global-account-platform/specs/contracts/http/admin-api.md` (unchanged,
 * GAP-owned). `producerSection` is the `admin-api.md` heading line for
 * traceability; `producerPath` is the path the matched feature client
 * targets (asserted against the mocked `fetch` URL).
 */

/** Read vs mutation — drives the header / no-mutation-artifact assertions. */
export type ParityKind = 'read' | 'mutation';

/**
 * Per-capability mutation-header obligation (the FE-004 per-endpoint
 * non-uniformity is the key correctness risk this column pins):
 *  - `reason+idem`  → `X-Operator-Reason` AND `Idempotency-Key`;
 *  - `reason-only`  → `X-Operator-Reason`, **`Idempotency-Key` MUST NOT**;
 *  - `reason-no-idem` (export) → reason required on a GET, **no idem**;
 *  - `none`         → no mutation artifacts at all (reads / self path).
 */
export type HeaderObligation =
  | 'reason+idem'
  | 'reason-only'
  | 'reason-no-idem'
  | 'none';

export interface ParityRow {
  /** § 3.1 row number (stable, matches the spec table). */
  readonly id: number;
  /** admin-web operator capability being verified. */
  readonly capability: string;
  /** Console feature module (Layered-by-Feature) that implements it. */
  readonly featureModule:
    | 'features/accounts'
    | 'features/audit'
    | 'features/operators'
    | 'features/dashboards';
  /** The in-console route that resolves the capability's screen. */
  readonly route: '/accounts' | '/audit' | '/operators' | '/dashboards';
  /** The route module file (asserted to exist). */
  readonly routeFile: string;
  /** console-integration-contract.md § the binding lives under. */
  readonly contractSection: '2.4.1' | '2.4.2' | '2.4.3' | '2.4.4';
  /** The GAP `admin-api.md` producer path the client targets (substring
   *  match against the mocked fetch URL). Empty = composed/no dedicated
   *  producer endpoint (detail / dashboards). */
  readonly producerPath: string;
  /** The `admin-api.md` heading line, for spec ↔ producer traceability. */
  readonly producerSection: string;
  readonly kind: ParityKind;
  readonly header: HeaderObligation;
  /** The feature-module export name the capability is invoked through
   *  (asserted present on the public API / api module). */
  readonly clientExport: string;
  /** Free-form notes mirrored from the § 3.1 table (composition basis,
   *  ADR-MONO-015 D2 not-Grafana, header non-uniformity, …). */
  readonly notes: string;
  /** false ⇒ a real parity gap (→ new fix task, never silently patched).
   *  All true here — FE-006 found no gap (no green-wash; the matrix
   *  reflects reality). */
  readonly verified: boolean;
}

export const PARITY_MATRIX: readonly ParityRow[] = [
  {
    id: 1,
    capability: 'accounts: search / list',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts',
    producerSection: 'GET /api/admin/accounts',
    kind: 'read',
    header: 'none',
    clientExport: 'searchAccounts',
    notes: 'email single-lookup OR page/size list',
    verified: true,
  },
  {
    id: 2,
    capability: 'accounts: detail',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts',
    producerSection: 'GET /api/admin/accounts (composed; no producer GET-by-id)',
    kind: 'read',
    header: 'none',
    clientExport: 'getAccountByEmail',
    notes:
      'composed from the search/list item + ops 3-8 — NO fabricated ' +
      'GET-by-id (consistent with FE-002 / admin-api.md has none)',
    verified: true,
  },
  {
    id: 3,
    capability: 'accounts: lock',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts/',
    producerSection: 'POST /api/admin/accounts/{accountId}/lock',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'lockAccount',
    notes: 'reason+confirm-gated',
    verified: true,
  },
  {
    id: 4,
    capability: 'accounts: unlock',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts/',
    producerSection: 'POST /api/admin/accounts/{accountId}/unlock',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'unlockAccount',
    notes: 'reason+confirm-gated',
    verified: true,
  },
  {
    id: 5,
    capability: 'accounts: bulk-lock',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts/bulk-lock',
    producerSection: 'POST /api/admin/accounts/bulk-lock',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'bulkLockAccounts',
    notes: 'single key per confirmed action; multi-select confirm',
    verified: true,
  },
  {
    id: 6,
    capability: 'accounts: revoke-session',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/sessions/',
    producerSection: 'POST /api/admin/sessions/{accountId}/revoke',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'revokeSessions',
    notes: 'reason+confirm-gated',
    verified: true,
  },
  {
    id: 7,
    capability: 'accounts: gdpr-delete',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts/',
    producerSection: 'POST /api/admin/accounts/{accountId}/gdpr-delete',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'gdprDeleteAccount',
    notes: 'irreversible — double-confirm + typed confirmation',
    verified: true,
  },
  {
    id: 8,
    capability: 'accounts: export',
    featureModule: 'features/accounts',
    route: '/accounts',
    routeFile: 'src/app/(console)/accounts/page.tsx',
    contractSection: '2.4.1',
    producerPath: '/api/admin/accounts/',
    producerSection: 'GET /api/admin/accounts/{accountId}/export',
    kind: 'read',
    header: 'reason-no-idem',
    clientExport: 'exportAccount',
    notes:
      'GET with producer-mandated audit reason (unmasked PII; producer ' +
      'meta-audits); NOT an idempotency-bearing mutation (no Idempotency-Key)',
    verified: true,
  },
  {
    id: 9,
    capability: 'audit: query',
    featureModule: 'features/audit',
    route: '/audit',
    routeFile: 'src/app/(console)/audit/page.tsx',
    contractSection: '2.4.2',
    producerPath: '/api/admin/audit',
    producerSection: 'GET /api/admin/audit',
    kind: 'read',
    header: 'none',
    clientExport: 'queryAudit',
    notes: 'source=admin or unfiltered; meta-audited producer-side',
    verified: true,
  },
  {
    id: 10,
    capability: 'security: login-history',
    featureModule: 'features/audit',
    route: '/audit',
    routeFile: 'src/app/(console)/audit/page.tsx',
    contractSection: '2.4.2',
    producerPath: '/api/admin/audit',
    producerSection: 'GET /api/admin/audit?source=login_history',
    kind: 'read',
    header: 'none',
    clientExport: 'queryAudit',
    notes:
      'intersection-permission audit.read ∧ security.event.read ' +
      '(producer-authoritative)',
    verified: true,
  },
  {
    id: 11,
    capability: 'security: suspicious',
    featureModule: 'features/audit',
    route: '/audit',
    routeFile: 'src/app/(console)/audit/page.tsx',
    contractSection: '2.4.2',
    producerPath: '/api/admin/audit',
    producerSection: 'GET /api/admin/audit?source=suspicious',
    kind: 'read',
    header: 'none',
    clientExport: 'queryAudit',
    notes: 'intersection-permission audit.read ∧ security.event.read',
    verified: true,
  },
  {
    id: 12,
    capability: 'operators: create',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators',
    producerSection: 'POST /api/admin/operators',
    kind: 'mutation',
    header: 'reason+idem',
    clientExport: 'createOperator',
    notes: 'producer requires BOTH; reason+elevated-confirm-gated',
    verified: true,
  },
  {
    id: 13,
    capability: 'operators: edit-roles',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators/',
    producerSection: 'PATCH /api/admin/operators/{operatorId}/roles',
    kind: 'mutation',
    header: 'reason-only',
    clientExport: 'editOperatorRoles',
    notes:
      'FE-004 per-endpoint header NON-uniformity: reason ONLY, ' +
      'Idempotency-Key MUST NOT be sent (producer omits it; full-replace ' +
      'PATCH is idempotent). [] allowed = remove all roles.',
    verified: true,
  },
  {
    id: 14,
    capability: 'operators: change-status',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators/',
    producerSection: 'PATCH /api/admin/operators/{operatorId}/status',
    kind: 'mutation',
    header: 'reason-only',
    clientExport: 'changeOperatorStatus',
    notes:
      'FE-004 per-endpoint header NON-uniformity: reason ONLY, ' +
      'Idempotency-Key MUST NOT be sent (idempotent PATCH). ACTIVE↔SUSPENDED.',
    verified: true,
  },
  {
    id: 15,
    capability: 'operators: change-password',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators/me/password',
    producerSection: 'PATCH /api/admin/operators/me/password',
    kind: 'mutation',
    header: 'none',
    clientExport: 'changeOwnPassword',
    notes:
      'SELF only (no admin-set-other); valid operator token only — ' +
      'no reason, no idem per the producer (204 No Content)',
    verified: true,
  },
  {
    id: 16,
    capability:
      'dashboards (ADR-MONO-015-refined composed operator overview, NOT Grafana)',
    featureModule: 'features/dashboards',
    route: '/dashboards',
    routeFile: 'src/app/(console)/dashboards/page.tsx',
    contractSection: '2.4.4',
    // No dedicated producer endpoint — a bounded fan-out composing the
    // EXISTING accounts/audit/operators reads (no new GAP producer;
    // ADR-MONO-015 D1). The test asserts exactly those 3 reads are hit.
    producerPath: '',
    producerSection:
      'composed: GET /api/admin/accounts + GET /api/admin/audit + ' +
      'GET /api/admin/operators (no new producer — ADR-MONO-015 D1)',
    kind: 'read',
    header: 'none',
    clientExport: 'getOperatorOverview',
    notes:
      'ADR-MONO-015 D2: composed operator overview, explicitly NOT ' +
      "Grafana. Per-source isolated: a leg's 403/503/timeout degrades " +
      'that card only; a 401 on ANY leg = whole-overview re-login (never ' +
      'a per-card degrade — no partial authed state).',
    verified: true,
  },
  {
    id: 17,
    capability: 'operators: change-profile',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators/me/profile',
    producerSection: 'PATCH /api/admin/operators/me/profile',
    kind: 'mutation',
    header: 'none',
    clientExport: 'updateOwnProfile',
    notes:
      'SELF only (no admin-set-other-profile); valid operator token only — ' +
      'no reason, no idem per the producer (204 No Content). Body shape ' +
      'mirrors read: { operatorContext: { defaultAccountId: string | null } }. ' +
      'Opaque to GAP (TASK-BE-304 § Decision authority — no finance verify).',
    verified: true,
  },
  {
    id: 18,
    capability: 'operators: admin-set-profile',
    featureModule: 'features/operators',
    route: '/operators',
    routeFile: 'src/app/(console)/operators/page.tsx',
    contractSection: '2.4.3',
    producerPath: '/api/admin/operators/',
    producerSection: 'PATCH /api/admin/operators/{operatorId}/profile',
    kind: 'mutation',
    header: 'reason-only',
    clientExport: 'setOperatorProfile',
    notes:
      'Admin-on-behalf-of: SUPER_ADMIN sets ANOTHER operator\'s ' +
      'operatorContext.defaultAccountId. FE-004 per-endpoint header ' +
      "NON-uniformity: reason ONLY, Idempotency-Key MUST NOT be sent " +
      '(mirror rows 13 + 14 /roles + /status; full-replace PATCH is ' +
      'idempotent). Self via this path → producer ' +
      '400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH; UI gates the ' +
      'per-row button when row is self (UX layer; producer is the authority). ' +
      'Body shape on the GAP wire: ' +
      '{ operatorContext: { defaultAccountId: string | null } }.',
    verified: true,
  },
] as const;

/** The composed-overview fan-out legs (row 16) — the EXISTING reads it
 *  reuses (ADR-MONO-015 D1, no new producer). The test asserts the
 *  overview hits exactly these three and no mutation artifacts. */
export const OVERVIEW_FANOUT_LEGS = [
  '/api/admin/accounts',
  '/api/admin/audit',
  '/api/admin/operators',
] as const;
