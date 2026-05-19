import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import { getScmSectionState, ScmOpsScreen } from '@/features/scm-ops';

export const dynamic = 'force-dynamic';

/**
 * scm operations section route (TASK-PC-FE-008 — ADR-MONO-013 Phase 4
 * slice 2, the SECOND non-GAP domain federated by the console; completes
 * Phase 4). An in-console nav destination, NOT a catalog product re-route
 * — the catalog `scm.baseRoute` stays data-driven (resolveConsoleRoute
 * leaves non-GAP products on their registry baseRoute; an
 * `available:false` scm is handled by the existing catalog "coming soon"
 * path — this route does not hard-crash when scm is unavailable).
 *
 * Server component. STRICTLY READ-ONLY. scm is reached server-side with
 * the HttpOnly **GAP OIDC access token** (NOT the GAP exchanged operator
 * token — § 2.4.6 reuses the § 2.4.5 per-domain credential rule; the #569
 * invariant is GAP-domain-scoped and the scm gateway *requires* the GAP
 * OIDC token).
 *
 * Eligibility (§ 2.4.6 reusing § 2.4.5 tenant-model divergence): scm
 * resolves the tenant from the JWT `tenant_id ∈ {scm,*}` claim
 * producer-side — the console sends no tenant. To avoid fabricating a
 * cross-tenant call, the page resolves the operator's scm eligibility from
 * the data-driven registry (the app layer is the layer allowed to compose
 * `features/*`) and passes it into `getScmSectionState()`. Resilience
 * (§ 2.5): 401 → whole-session re-login; 403 → inline "not scoped";
 * 429 → rate-limited notice; 503/timeout → only this section degrades
 * (the `(console)` shell + GAP/wms sections stay).
 */
export default async function ScmPage() {
  // Eligibility pre-flight from the data-driven registry (§ 2.2). A
  // registry 401 → whole-session re-login (no partial authed state); a
  // registry failure → treat as degraded (cannot prove ineligibility from
  // a failed registry — never block on an unproven negative).
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    const scm = catalog.products.find((p) => p.productKey === 'scm');
    eligible = Boolean(scm && scm.available && scm.tenants.length > 0);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  if (registryDegraded) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 운영
        </h1>
        <div
          role="status"
          data-testid="scm-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getScmSectionState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 운영
        </h1>
        <div
          role="status"
          data-testid="scm-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            scm 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 scm 테넌트 스코프가 부여되어 있지
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
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 운영
        </h1>
        <div
          role="status"
          data-testid="scm-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 / 역할
          확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.rateLimited) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 운영
        </h1>
        <div
          role="status"
          data-testid="scm-ratelimited"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 게이트웨이 요청이 일시적으로 제한되었습니다. 잠시 후 다시
          시도하세요. 콘솔의 다른 기능은 계속 사용할 수 있습니다.
        </div>
      </section>
    );
  }

  if (
    state.degraded ||
    !state.poList ||
    !state.snapshot ||
    !state.staleness
  ) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 운영
        </h1>
        <div
          role="status"
          data-testid="scm-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <ScmOpsScreen
      poList={state.poList}
      snapshot={state.snapshot}
      staleness={state.staleness}
    />
  );
}
