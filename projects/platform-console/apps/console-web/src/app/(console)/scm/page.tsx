import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import { getScmOverviewState, ScmOverview } from '@/features/scm-ops';

export const dynamic = 'force-dynamic';

/**
 * scm 개요 (overview) route (TASK-PC-FE-008 read section → TASK-PC-FE-167
 * overview snapshot → TASK-PC-FE-220 slim-down). An in-console nav
 * destination, NOT a catalog product re-route. Server component, STRICTLY
 * READ-ONLY.
 *
 * TASK-PC-FE-220: 개요 now renders ONLY the operator overview-snapshot band
 * (`ScmOverview` — per-area count tiles, PO-status distribution, recent
 * POs). The combined procurement PO table + inventory-visibility
 * snapshot/SKU/staleness tables were split out to the dedicated
 * `/scm/procurement` (조달) and `/scm/inventory` (재고) routes. This keeps
 * 개요 to a domain-at-a-glance summary, consistent with every other
 * domain's 개요.
 *
 * Auth (§ 2.4.6 reusing § 2.4.5): scm is reached server-side with the
 * HttpOnly **IAM OIDC access token**. Eligibility (§ 2.4.6): scm resolves
 * the tenant from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the
 * console sends no tenant. To avoid fabricating a cross-tenant call, the
 * page resolves the operator's scm eligibility from the data-driven
 * registry and passes it into `getScmOverviewState()`. Resilience (§ 2.5):
 * 401 → whole-session re-login; not-eligible → inline "not scoped"; the
 * overview band does per-cell degrade for a downstream 403/503/timeout.
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
          SCM 개요
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

  if (!eligible) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 개요
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

  // Operator overview-snapshot fan-out (TASK-PC-FE-167) — console-web DIRECT
  // reads over the scm gateway (PC-FE-168 read-leg decision); redirects
  // '/login' on a 401 internally. The overview does per-cell degrade.
  const overviewState = await getScmOverviewState(eligible);

  return (
    <section aria-labelledby="scm-heading">
      <h1 id="scm-heading" className="mb-2 text-2xl font-semibold">
        SCM 개요
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        scm 도메인 운영 현황 요약. 발주 조회는 조달, 재고 가시성은 재고
        메뉴에서 볼 수 있습니다.
      </p>
      <ScmOverview state={overviewState} />
    </section>
  );
}
