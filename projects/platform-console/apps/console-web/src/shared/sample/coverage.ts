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

  // ── ecommerce (`callEcommerceGateway`) ─────────────────────────────────────
  { core: 'ecommerce', surface: 'ecommerce', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_image', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_user', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_shipping', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_order', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_notification', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_seller', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_settlement', status: 'pending', owner: ECOMMERCE },
  { core: 'ecommerce', surface: 'ecommerce_promotion', status: 'pending', owner: ECOMMERCE },

  // ── erp (`callFlatEnvelopeGateway`) ────────────────────────────────────────
  { core: 'flat', surface: 'erp', status: 'pending', owner: ERP },
  { core: 'flat', surface: 'erp_approval', status: 'pending', owner: ERP },
  { core: 'flat', surface: 'erp_delegation', status: 'pending', owner: ERP },

  // ── finance + ledger (`callFlatEnvelopeGateway`) ───────────────────────────
  { core: 'flat', surface: 'finance', status: 'pending', owner: FINANCE },
  { core: 'flat', surface: 'ledger', status: 'pending', owner: FINANCE },

  // ── wms (`callWmsGateway`) ─────────────────────────────────────────────────
  { core: 'wms', surface: 'wms', status: 'pending', owner: WMS },
  { core: 'wms', surface: 'wms_outbound', status: 'pending', owner: WMS },
  { core: 'wms', surface: 'wms_outbound_logistics', status: 'pending', owner: WMS },

  // ── scm (`callScmGateway` → `callFlatEnvelopeGateway`) ─────────────────────
  { core: 'flat', surface: 'scm', status: 'pending', owner: SCM },
  { core: 'flat', surface: 'scm_replenishment', status: 'pending', owner: SCM },
  { core: 'flat', surface: 'scm_config', status: 'pending', owner: SCM },
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
  '/ecommerce': 'pending',
  '/ecommerce/notifications/templates': 'pending',
  '/ecommerce/notifications/templates/new': 'pending',
  '/ecommerce/notifications/templates/[id]/edit': 'pending',
  '/ecommerce/orders': 'pending',
  '/ecommerce/orders/[id]': 'pending',
  '/ecommerce/products': 'pending',
  '/ecommerce/products/new': 'pending',
  '/ecommerce/products/[id]': 'pending',
  '/ecommerce/products/[id]/edit': 'pending',
  '/ecommerce/promotions': 'pending',
  '/ecommerce/promotions/new': 'pending',
  '/ecommerce/promotions/[id]': 'pending',
  '/ecommerce/promotions/[id]/edit': 'pending',
  '/ecommerce/sellers': 'pending',
  '/ecommerce/sellers/new': 'pending',
  '/ecommerce/sellers/[id]': 'pending',
  '/ecommerce/settlements': 'pending',
  '/ecommerce/settlements/periods/[id]': 'pending',
  '/ecommerce/shippings': 'pending',
  '/ecommerce/users': 'pending',
  '/ecommerce/users/[id]': 'pending',

  // erp — TASK-PC-FE-285
  '/erp': 'pending',
  '/erp/approval': 'pending',
  '/erp/delegation': 'pending',
  '/erp/masters': 'pending',
  '/erp/orgview': 'pending',

  // finance + ledger — TASK-PC-FE-286
  '/finance': 'pending',
  '/finance/accounts': 'pending',
  '/ledger': 'pending',

  // wms — TASK-PC-FE-287
  '/wms': 'pending',
  '/wms/inbound': 'pending',
  '/wms/inventory': 'pending',
  '/wms/master': 'pending',
  '/wms/operations': 'pending',
  '/wms/outbound': 'pending',

  // scm — TASK-PC-FE-288
  '/scm': 'pending',
  '/scm/config': 'pending',
  '/scm/inventory': 'pending',
  '/scm/procurement': 'pending',
  '/scm/replenishment': 'pending',
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
