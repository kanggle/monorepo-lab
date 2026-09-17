/**
 * Sample coverage ledger (ADR-MONO-074 A9 / TASK-PC-FE-282 AC-9).
 *
 * «Which backend GET has sample data» is a LEDGER, not an emergent property of
 * whatever fixtures happen to exist — so empty cells cannot grow silently.
 * `tests/unit/sample-coverage-ledger.test.ts` rebuilds the inventory from the
 * source tree and goes red when the two disagree in either direction.
 *
 * ── Granularity: one row per SURFACE ─────────────────────────────────────────
 * A surface is one gateway profile — the `logPrefix` every feature client
 * already hands its core (`accounts`, `wms_outbound`, `ecommerce_order`, …) —
 * plus the console-bff proxy routes and the registry. 🔵 Why not one row per
 * endpoint: endpoint paths are composed across two or three wrapper layers
 * (prefix constants, `encodeURIComponent` ids, query builders), so a path
 * extractor would measure the extractor, not the inventory. `logPrefix` is a
 * literal at exactly one definition site per profile, so the inventory derived
 * from it is exact. A new client profile without a row here fails the guard.
 *
 * `pending` rows are answered with `503 SAMPLE_NOT_READY`, which each core
 * already maps to its section-degrade state; the `(console)` shell then says
 * «이 화면의 샘플 데이터는 준비 중입니다» (see {@link SCREEN_COVERAGE}).
 * A `ready` row must have a fixture in `fixtures/index.ts`.
 */

/** Which HTTP core a surface is reached through. */
export type SampleCore =
  | 'iam'
  | 'registry'
  | 'wms'
  | 'ecommerce'
  | 'flat'
  | 'console-bff'
  | 'console-web';

export type CoverageStatus = 'ready' | 'pending';

export interface SurfaceCoverage {
  core: SampleCore;
  surface: string;
  status: CoverageStatus;
  /** The ticket that owns turning this row `ready`. */
  owner: string;
}

const FOUNDATION = 'TASK-PC-FE-282';
const IAM = 'TASK-PC-FE-283';
const ECOMMERCE = 'TASK-PC-FE-284';
const ERP = 'TASK-PC-FE-285';
const FINANCE = 'TASK-PC-FE-286';
const WMS = 'TASK-PC-FE-287';
const SCM = 'TASK-PC-FE-288';

export const SURFACE_COVERAGE: readonly SurfaceCoverage[] = [
  // ── ready (R3ⓐ — dashboards first) ─────────────────────────────────────────
  { core: 'registry', surface: 'registry', status: 'ready', owner: FOUNDATION },
  { core: 'console-bff', surface: 'operator-overview', status: 'ready', owner: FOUNDATION },
  { core: 'console-bff', surface: 'domain-health', status: 'ready', owner: FOUNDATION },
  { core: 'console-bff', surface: 'notifications-inbox', status: 'ready', owner: FOUNDATION },

  // ── iam (`callAdminGateway`) — TASK-PC-FE-283 ───────────────────────────────
  { core: 'iam', surface: 'accounts', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'audit', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'operators', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'rbac', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'subscriptions', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'partnerships', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'tenants', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'org_nodes', status: 'ready', owner: IAM },
  { core: 'iam', surface: 'groups', status: 'ready', owner: IAM },

  // ── ecommerce (`callEcommerceGateway`) — TASK-PC-FE-284 ────────────────────
  { core: 'ecommerce', surface: 'ecommerce', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_image', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_user', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_shipping', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_order', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_notification', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_seller', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_settlement', status: 'ready', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_promotion', status: 'ready', owner: ECOMMERCE },

  // ── erp (`callFlatEnvelopeGateway`) — TASK-PC-FE-285 ───────────────────────
  { core: 'flat', surface: 'erp', status: 'ready', owner: ERP },
  { core: 'flat', surface: 'erp_approval', status: 'ready', owner: ERP },
  { core: 'flat', surface: 'erp_delegation', status: 'ready', owner: ERP },

  // ── finance + ledger (`callFlatEnvelopeGateway`) — TASK-PC-FE-286 ─────────
  { core: 'flat', surface: 'finance', status: 'ready', owner: FINANCE },
  { core: 'flat', surface: 'ledger', status: 'ready', owner: FINANCE },

  // ── wms (`callWmsGateway`) — TASK-PC-FE-287 ─────────────────────────────────
  { core: 'wms', surface: 'wms', status: 'ready', owner: WMS },
  { core: 'wms', surface: 'wms_outbound', status: 'ready', owner: WMS },
  // 🔴 TASK-PC-FE-287 — this row's `core` was `'wms'` since TASK-PC-FE-282,
  // but `outbound-logistics-api.ts`'s `LOGISTICS_PROFILE` reaches this surface
  // via `callScmGateway` → `callFlatEnvelopeGateway`, which asks
  // `sampleGate({core: 'flat', surface: 'wms_outbound_logistics', …})` — NOT
  // `core: 'wms'` (`callWmsGateway` is never in this call path at all). A
  // `core: 'wms'` row can never match that request (`findSurfaceCoverage`
  // compares BOTH fields), so this surface was structurally unable to become
  // `ready` no matter what `status` said — every real call 503s forever. It
  // went unnoticed at `pending` because "never matches" and "pending" both
  // 503 identically; `sample-fixtures-schema-wms.test.ts`'s
  // "core=flat, not core=wms" cell (bite-proven) is what catches a regression
  // back to `'wms'` (`sample-coverage-ledger.test.ts`'s generic ledger↔router
  // check cannot: it always calls `sampleResponse` with the row's OWN `core`
  // field, so a self-consistently-wrong row still "promises what the router
  // does" to itself).
  { core: 'flat', surface: 'wms_outbound_logistics', status: 'ready', owner: WMS },

  // ── scm (`callScmGateway` → `callFlatEnvelopeGateway`) — TASK-PC-FE-288 ────
  { core: 'flat', surface: 'scm', status: 'ready', owner: SCM },
  { core: 'flat', surface: 'scm_replenishment', status: 'ready', owner: SCM },
  { core: 'flat', surface: 'scm_config', status: 'ready', owner: SCM },
];

export function findSurfaceCoverage(
  core: SampleCore,
  surface: string,
): SurfaceCoverage | undefined {
  return SURFACE_COVERAGE.find((row) => row.core === core && row.surface === surface);
}

/**
 * A representative GET path per `ready` surface, keyed `<core>:<surface>`
 * (TASK-PC-FE-283). `sample-coverage-ledger.test.ts`'s "the ledger promises
 * what the router does" section asks every `ready` row for a `200` — the 4
 * TASK-PC-FE-282 dashboard/registry fixtures answer ANY path (they ignore the
 * argument), so a bare `/` worked there. The 9 IAM domain fixtures answer
 * concrete producer paths (`/api/admin/accounts`, …) and return `undefined`
 * (→ 503) for an unmatched one — so the guard needs a path each fixture can
 * actually answer, not a placeholder both sides silently agree on. Absent
 * entries fall back to `/` (preserves the TASK-PC-FE-282 rows unchanged).
 */
export const SURFACE_SAMPLE_PATH: Readonly<Record<string, string>> = {
  'iam:accounts': '/api/admin/accounts?page=0&size=20&tenantId=sample',
  'iam:audit': '/api/admin/audit?page=0&size=20&tenantId=sample',
  'iam:operators': '/api/admin/operators?page=0&size=20&tenantId=sample',
  'iam:rbac': '/api/admin/roles',
  'iam:subscriptions': '/api/admin/subscriptions',
  'iam:partnerships': '/api/admin/partnerships?page=0&size=20',
  'iam:tenants': '/api/admin/tenants?page=0&size=20',
  'iam:org_nodes': '/api/admin/org-nodes',
  'iam:groups': '/api/admin/groups?page=0&size=20',
  // TASK-PC-FE-284
  'ecommerce:ecommerce': '/api/admin/products?page=0&size=20',
  'ecommerce:ecommerce_image': '/api/admin/products/prod-sample-0001/images',
  'ecommerce:ecommerce_order': '/api/admin/orders?page=0&size=20',
  'ecommerce:ecommerce_user': '/api/admin/users?page=0&size=20',
  'ecommerce:ecommerce_promotion': '/api/promotions?page=0&size=20',
  'ecommerce:ecommerce_shipping': '/api/shippings?page=0&size=20',
  'ecommerce:ecommerce_notification': '/api/notifications/templates?page=0&size=20',
  'ecommerce:ecommerce_seller': '/api/admin/sellers?page=0&size=20',
  'ecommerce:ecommerce_settlement': '/api/admin/settlements/periods?page=0&size=20',
  // TASK-PC-FE-285
  'flat:erp': '/api/erp/masterdata/departments?page=0&size=20',
  'flat:erp_approval': '/api/erp/approval/requests?page=0&size=20',
  'flat:erp_delegation': '/api/erp/approval/delegations',
  // TASK-PC-FE-286
  'flat:finance': '/api/finance/accounts/sample-account-0001/balances',
  'flat:ledger': '/api/finance/ledger/trial-balance',
  // TASK-PC-FE-287
  'wms:wms': '/api/v1/admin/dashboard/inventory?page=0&size=20',
  'wms:wms_outbound': '/api/v1/outbound/orders?page=0&size=20',
  'flat:wms_outbound_logistics': '/api/v1/logistics/dispatches/by-shipment/ship-sample-0001',
  // TASK-PC-FE-288
  'flat:scm': '/api/v1/procurement/po?page=0&size=20',
  'flat:scm_replenishment': '/api/v1/demand-planning/suggestions?page=0&size=20',
  'flat:scm_config': '/api/v1/demand-planning/policies/SKU-SAMPLE-001',
};

/**
 * Screen ledger — every `(console)` page route and whether its sample data is
 * ready. The shell reads this to say «준비 중» on a pending screen (the section
 * degrade states drop the error code, so the screen cannot say it itself).
 * `static` screens make no backend call at all (the guides).
 *
 * The guard rebuilds the route list from `src/app/(console)/**\/page.tsx`.
 */
export type ScreenStatus = CoverageStatus | 'static';

export const SCREEN_COVERAGE: Readonly<Record<string, ScreenStatus>> = {
  // ready (TASK-PC-FE-282)
  '/console': 'ready',
  '/dashboards/overview': 'ready',
  '/dashboards/health': 'ready',

  // static — no backend call
  '/iam/guide': 'static',
  '/wms/guide': 'static',
  '/scm/guide': 'static',
  '/finance/guide': 'static',
  '/erp/guide': 'static',
  '/ecommerce/guide': 'static',

  // iam — TASK-PC-FE-283
  '/account': 'ready',
  '/accounts': 'ready',
  '/audit': 'ready',
  '/dashboards': 'ready',
  '/iam': 'ready',
  '/operator-groups': 'ready',
  '/operators': 'ready',
  '/org-hierarchy': 'ready',
  '/partnerships': 'ready',
  '/permission-sets': 'ready',
  '/permissions': 'ready',
  '/subscriptions': 'ready',
  '/tenants': 'ready',
  '/tenants/[tenantId]': 'ready',

  // ecommerce — TASK-PC-FE-284
  '/ecommerce': 'ready',
  '/ecommerce/notifications/templates': 'ready',
  '/ecommerce/notifications/templates/new': 'ready',
  '/ecommerce/notifications/templates/[id]/edit': 'ready',
  '/ecommerce/orders': 'ready',
  '/ecommerce/orders/[id]': 'ready',
  '/ecommerce/products': 'ready',
  '/ecommerce/products/new': 'ready',
  '/ecommerce/products/[id]': 'ready',
  '/ecommerce/products/[id]/edit': 'ready',
  '/ecommerce/promotions': 'ready',
  '/ecommerce/promotions/new': 'ready',
  '/ecommerce/promotions/[id]': 'ready',
  '/ecommerce/promotions/[id]/edit': 'ready',
  '/ecommerce/sellers': 'ready',
  '/ecommerce/sellers/new': 'ready',
  '/ecommerce/sellers/[id]': 'ready',
  '/ecommerce/settlements': 'ready',
  '/ecommerce/settlements/periods/[id]': 'ready',
  '/ecommerce/shippings': 'ready',
  '/ecommerce/users': 'ready',
  '/ecommerce/users/[id]': 'ready',

  // erp — TASK-PC-FE-285
  '/erp': 'ready',
  '/erp/approval': 'ready',
  '/erp/delegation': 'ready',
  '/erp/masters': 'ready',
  '/erp/orgview': 'ready',

  // finance + ledger — TASK-PC-FE-286
  '/finance': 'ready',
  '/finance/accounts': 'ready',
  '/ledger': 'ready',

  // wms — TASK-PC-FE-287
  '/wms': 'ready',
  '/wms/inbound': 'ready',
  '/wms/inventory': 'ready',
  '/wms/master': 'ready',
  '/wms/operations': 'ready',
  '/wms/outbound': 'ready',

  // scm — TASK-PC-FE-288
  '/scm': 'ready',
  '/scm/config': 'ready',
  '/scm/inventory': 'ready',
  '/scm/procurement': 'ready',
  '/scm/replenishment': 'ready',
};

/**
 * The ledger status of a concrete pathname (`/ecommerce/orders/ord-1` matches
 * `/ecommerce/orders/[id]`). An unknown pathname is `pending` — the shell errs
 * toward saying «준비 중» rather than staying silent.
 */
export function screenStatusFor(pathname: string): ScreenStatus {
  const clean = pathname.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  if (clean in SCREEN_COVERAGE) return SCREEN_COVERAGE[clean];
  for (const [route, status] of Object.entries(SCREEN_COVERAGE)) {
    if (!route.includes('[')) continue;
    const pattern = new RegExp(
      `^${route.replace(/\[[^\]]+\]/g, '[^/]+')}$`,
    );
    if (pattern.test(clean)) return status;
  }
  return 'pending';
}
