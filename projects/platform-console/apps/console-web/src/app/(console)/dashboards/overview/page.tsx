import { redirect } from 'next/navigation';
import {
  OperatorOverviewScreen,
  getOperatorOverviewState,
} from '@/features/operator-overview';
import {
  DomainHealthSummaryCard,
  getDomainHealthState,
  deriveHealthByDomain,
} from '@/features/domain-health';
import { getCatalog, ServiceCatalog } from '@/features/catalog';
import type { ProductKey } from '@/shared/api/registry-types';
import { ApiError } from '@/shared/api/errors';
import { NoTenantNotice } from '@/widgets/no-tenant-notice';

export const dynamic = 'force-dynamic';

/**
 * MVP "Operator Overview" cross-domain dashboard route
 * (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7 MVP /
 * `console-integration-contract.md` § 2.4.9.1).
 *
 * The FIRST concrete `§ 2.4.9.X` composition route consumed by the
 * console. Generalises the GAP-only `features/dashboards` composed
 * overview (ADR-MONO-015 D1-B) across all 5 backend domains — composed
 * in this server since TASK-PC-FE-302 (ADR-MONO-081).
 *
 * Server component (architecture.md § Server vs Client Components;
 * AC-24). The initial envelope is composed server-side by the route
 * `/api/console/dashboards/operator-overview` from the session tokens in
 * `shared/lib/session`; per-card degrade lives INSIDE the 200 payload, so
 * the page never branches on per-card status — only on the three
 * whole-fan-out outcomes:
 *
 *   - 401 on the BFF call → `redirect('/login')` (whole-overview
 *     re-login; no partial authed state).
 *   - 400 NO_ACTIVE_TENANT → render the "select a tenant" gate (never
 *     an empty `X-Tenant-Id` to the BFF; the proxy fast-fails before
 *     any outbound).
 *   - 502 BAD_GATEWAY (proxy → bff transport / unexpected status —
 *     the BFF itself never emits 503 per D5.B) → render a banner-only
 *     "overview unavailable" state; the `(console)` shell stays
 *     intact (per-source isolation generalised to the whole envelope).
 *
 * 🔴 TASK-PC-FE-310 — this page also absorbs the old `/console` catalog
 * home: the sidebar's standalone «카탈로그» entry is gone
 * (`console-nav-config.ts`), and its product × tenant grid (`getCatalog()` +
 * `ServiceCatalog`) now lives HERE —
 *
 *   - no active tenant (the gate below) → the grid renders DIRECTLY as this
 *     page's primary content (the catalog always WAS the tenant picker for a
 *     no-tenant operator; Goal 2a);
 *   - an active tenant (the success branch) → the grid lives in a closed-by-
 *     default «제품·테넌트 전체» `<details>` BELOW the overview cards (Goal
 *     2b — still reachable, no longer competing with the primary content);
 *   - the whole-overview BFF-unavailable banner → the SAME section, but OPEN
 *     (the overview being down must not also hide the way to a domain
 *     screen — Failure Scenario 1).
 *
 * `getCatalog()` is fired concurrently up-front, exactly like
 * `healthPromise` below (TASK-PC-FE-117 pattern) — a sequential fetch would
 * slow down every branch, not just the one that renders the grid. Its OWN
 * 401 (registry leg rejected the session) forces the same whole-session
 * re-login as the overview's 401 (Edge Case: never a partial-authed catalog
 * next to a re-login overview). Any OTHER catalog failure does not throw —
 * `getCatalog()` already converts a registry timeout/5xx/circuit-open into
 * `{ products: [], degraded: true }` (see that function's own doc comment),
 * so `ServiceCatalog` renders its own `catalog-degraded` notice and the rest
 * of the overview is untouched (Failure Scenario 4).
 */
export default async function OperatorOverviewPage() {
  // Speculatively fire the domain-health fan-out concurrently with the
  // operator-overview fetch (TASK-PC-FE-117). The two are independent BFF
  // round-trips, so starting them together turns the success-path latency
  // from `overview + health` into `max(overview, health)`.
  //
  // This deliberately reverses TASK-PC-FE-061's "fetch only in the success
  // branch" posture: on the gated branches below (unauthorized / noTenant /
  // bffUnavailable) this speculative call is wasted. The trade-off is net
  // positive — those branches are degraded/rare (noTenant is effectively
  // first-entry-only since the active-tenant default of TASK-PC-FE-036),
  // while the hot success path runs on every load. `getDomainHealthState()`
  // never throws, so leaving `healthPromise` un-awaited on a gated branch
  // raises no unhandled rejection.
  const healthPromise = getDomainHealthState();
  // TASK-PC-FE-310 — the catalog leg joins the same up-front fan-out; every
  // branch below needs the grid (directly, in the closed section, or in the
  // open section), so there is no branch where starting it late would help.
  const catalogPromise = getCatalog();
  const state = await getOperatorOverviewState();

  if (state.unauthorized) {
    redirect('/login?error=session_expired');
  }

  // Resolve the catalog leg's OWN auth failure the same way `console/page.tsx`
  // used to (pre-fold) — a 401 here means the session itself is dead, not just
  // this leg, so it forces the identical whole-session re-login as the
  // overview's own `unauthorized` branch above (never a partial-authed state
  // where the overview renders but the catalog silently 401s).
  let catalog;
  try {
    catalog = await catalogPromise;
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login?error=session_expired');
    }
    throw err;
  }

  if (state.noTenant) {
    // 🔴 Edge Case "레지스트리 제품 0개 / 테넌트 0개" — if the grid would have
    // nothing to pick from, there is nothing this screen can hand the
    // operator to resolve their own no-tenant state, so the original
    // NoTenantNotice copy (zero vs select) is ALSO shown beneath it.
    const catalogEmpty = !catalog.degraded && catalog.products.length === 0;
    return (
      <section aria-labelledby="operator-overview-heading">
        <h1
          id="operator-overview-heading"
          className="mb-6 text-2xl font-semibold"
        >
          운영자 통합 개요
        </h1>
        <p className="mb-6 text-sm text-muted-foreground">
          아래에서 제품의 테넌트를 고르면 그 테넌트로 전환됩니다.
        </p>
        <ServiceCatalog
          catalog={catalog}
          headingLevel="h2"
          headingText="제품·테넌트"
        />
        {catalogEmpty &&
          (await NoTenantNotice({
            testId: 'operator-overview-no-tenant',
            description: (
              <>
                6개 도메인 통합 개요는 테넌트 범위로 구성됩니다. 상단 테넌트
                스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
              </>
            ),
          }))}
      </section>
    );
  }

  if (state.bffUnavailable || !state.overview) {
    return (
      <section aria-labelledby="operator-overview-heading">
        <h1
          id="operator-overview-heading"
          className="mb-6 text-2xl font-semibold"
        >
          운영자 통합 개요
        </h1>
        <div
          role="status"
          data-testid="operator-overview-bff-unavailable"
          className="mb-6 rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            통합 개요를 일시적으로 불러올 수 없습니다.
          </p>
          <p>
            콘솔 자체는 정상 동작합니다. 각 도메인 화면으로 직접 이동하거나
            잠시 후 다시 시도하세요.
          </p>
        </div>
        {/* TASK-PC-FE-310 — OPEN by default here (Goal/Failure Scenario 1):
            the overview itself being down must not also hide the one
            remaining way into a domain screen. */}
        <details open data-testid="overview-catalog-section">
          <summary className="cursor-pointer select-none text-sm font-medium text-foreground">
            제품·테넌트 전체
          </summary>
          <div className="mt-4">
            <ServiceCatalog
              catalog={catalog}
              headingLevel="h2"
              headingText="제품·테넌트"
            />
          </div>
        </details>
      </section>
    );
  }

  // Active tenant is guaranteed past the gates above, so the domain-health
  // fan-out won't NO_ACTIVE_TENANT here. The summary card degrades on its own
  // (null health → compact note) so it never blanks the overview. The health
  // call was started up-front (concurrently with the overview fetch) and is
  // awaited here only on the success path. (TASK-PC-FE-061 / TASK-PC-FE-117)
  const healthState = await healthPromise;
  // TASK-PC-FE-310 — the tile-tone map the catalog grid needs, derived from
  // the SAME `healthState` the summary card above reads (one fetch, two
  // consumers) via the single shared mapping (`features/domain-health`'s
  // `deriveHealthByDomain`, extracted from the pre-fold `console/page.tsx` so
  // the two consumers can never drift apart — Failure Scenario 5).
  const { healthByDomain, healthNotice } =
    deriveHealthByDomain<ProductKey>(healthState);

  return (
    <>
      <OperatorOverviewScreen overview={state.overview} />
      <DomainHealthSummaryCard state={healthState} />
      {/* TASK-PC-FE-310 — CLOSED by default here: an active-tenant operator
          already has their primary content above; the full product × tenant
          grid is a secondary "go elsewhere" surface, not competing for
          attention on every load (Goal 2b). */}
      <details className="mt-6" data-testid="overview-catalog-section">
        <summary className="cursor-pointer select-none text-sm font-medium text-foreground">
          제품·테넌트 전체
        </summary>
        <div className="mt-4">
          <ServiceCatalog
            catalog={catalog}
            headingLevel="h2"
            headingText="제품·테넌트"
            healthByDomain={healthByDomain}
            healthState={healthNotice}
          />
        </div>
      </details>
    </>
  );
}
