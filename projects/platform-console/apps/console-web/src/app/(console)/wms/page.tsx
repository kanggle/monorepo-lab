import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getWmsSectionState,
  getWmsOverviewState,
  WmsOpsScreen,
  WmsOverview,
} from '@/features/wms-ops';

export const dynamic = 'force-dynamic';

/**
 * wms operations section route (TASK-PC-FE-007 — ADR-MONO-013 Phase 4
 * slice 1, the first NON-IAM domain federated by the console). An
 * in-console nav destination, NOT a catalog product re-route — the catalog
 * `wms.baseRoute` stays data-driven (resolveConsoleRoute leaves non-GAP
 * products on their registry baseRoute; an `available:false` wms is handled
 * by the existing catalog "coming soon" path — this route does not
 * hard-crash when wms is unavailable).
 *
 * Server component. wms is reached server-side with the HttpOnly **GAP
 * OIDC access token** (NOT the IAM exchanged operator token — § 2.4.5
 * per-domain credential divergence; the #569 invariant is GAP-domain-
 * scoped and the wms gateway *requires* the IAM OIDC token).
 *
 * Eligibility (§ 2.4.5 tenant-model divergence): wms resolves the tenant
 * from the JWT `tenant_id` claim producer-side — the console sends no
 * tenant. To avoid fabricating a cross-tenant call, the page resolves the
 * operator's wms eligibility from the data-driven registry (the app layer
 * is the layer allowed to compose `features/*`) and passes it into
 * `getWmsSectionState()`. Resilience (§ 2.5): 401 → whole-session
 * re-login; 403 → inline "not available to your role"; 503/timeout → only
 * this section degrades (the `(console)` shell stays).
 */
export default async function WmsPage() {
  // Eligibility pre-flight from the data-driven registry (§ 2.2). A
  // registry 401 → whole-session re-login (no partial authed state);
  // a registry failure → treat as degraded (cannot prove ineligibility
  // from a failed registry — never block on an unproven negative).
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    const wms = catalog.products.find((p) => p.productKey === 'wms');
    eligible = Boolean(wms && wms.available && wms.tenants.length > 0);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  if (registryDegraded) {
    return (
      <section aria-labelledby="wms-heading">
        <h1 id="wms-heading" className="mb-6 text-2xl font-semibold">
          WMS 운영
        </h1>
        <div
          role="status"
          data-testid="wms-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          wms 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  // Fire the operator overview-snapshot fan-out (TASK-PC-FE-166) concurrently
  // with the section-state fan-out. Both are console-web DIRECT reads over the
  // wms admin-service (per PC-FE-168 the bff-domain landings reuse the direct
  // read leg, NOT a console-bff leg); both perform a whole-session
  // `redirect('/login')` internally on a 401 (no partial authed state). The
  // overview does per-cell degrade; the section-state gates the tables.
  const [overviewState, state] = await Promise.all([
    getWmsOverviewState(eligible),
    getWmsSectionState(eligible),
  ]);

  if (state.notEligible) {
    return (
      <section aria-labelledby="wms-heading">
        <h1 id="wms-heading" className="mb-6 text-2xl font-semibold">
          WMS 운영
        </h1>
        <div
          role="status"
          data-testid="wms-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            wms 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 wms 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
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

  if (state.forbidden) {
    return (
      <section aria-labelledby="wms-heading">
        <h1 id="wms-heading" className="mb-6 text-2xl font-semibold">
          WMS 운영
        </h1>
        <div
          role="status"
          data-testid="wms-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이
          필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded || !state.inventory || !state.alerts || !state.shipments) {
    return (
      <section aria-labelledby="wms-heading">
        <h1 id="wms-heading" className="mb-6 text-2xl font-semibold">
          WMS 운영
        </h1>
        <div
          role="status"
          data-testid="wms-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          wms 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <WmsOpsScreen
      inventory={state.inventory}
      alerts={state.alerts}
      shipments={state.shipments}
      lagSeconds={state.lagSeconds}
      overview={<WmsOverview state={overviewState} />}
    />
  );
}
