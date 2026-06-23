import Link from 'next/link';
import { redirect } from 'next/navigation';
import {
  OperatorOverviewScreen,
  getOperatorOverviewState,
} from '@/features/operator-overview';
import {
  DomainHealthSummaryCard,
  getDomainHealthState,
} from '@/features/domain-health';

export const dynamic = 'force-dynamic';

/**
 * MVP "Operator Overview" cross-domain dashboard route
 * (TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7 MVP /
 * `console-integration-contract.md` § 2.4.9.1).
 *
 * The FIRST concrete `§ 2.4.9.X` composition route consumed by the
 * console. Generalises the GAP-only `features/dashboards` composed
 * overview (ADR-MONO-015 D1-B) across all 5 backend domains via the
 * new `console-bff`.
 *
 * Server component (architecture.md § Server vs Client Components;
 * AC-24). The initial envelope is composed server-side by the BFF
 * (the proxy route forwards 3 headers from `shared/lib/session` to
 * console-bff); per-card degrade lives INSIDE the 200 payload, so
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
  const state = await getOperatorOverviewState();

  if (state.unauthorized) {
    redirect('/login');
  }

  if (state.noTenant) {
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
          data-testid="operator-overview-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            6개 도메인 통합 개요는 테넌트 범위로 구성됩니다. 상단 테넌트
            스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
          </p>
          <Link
            href="/console"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
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
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            통합 개요를 일시적으로 불러올 수 없습니다.
          </p>
          <p>
            콘솔 자체는 정상 동작합니다. 각 도메인 화면으로 직접 이동하거나
            잠시 후 다시 시도하세요.
          </p>
        </div>
      </section>
    );
  }

  // Active tenant is guaranteed past the gates above, so the domain-health
  // fan-out won't NO_ACTIVE_TENANT here. The summary card degrades on its own
  // (null health → compact note) so it never blanks the overview. The health
  // call was started up-front (concurrently with the overview fetch) and is
  // awaited here only on the success path. (TASK-PC-FE-061 / TASK-PC-FE-117)
  const healthState = await healthPromise;

  return (
    <>
      <OperatorOverviewScreen overview={state.overview} />
      <DomainHealthSummaryCard state={healthState} />
    </>
  );
}
