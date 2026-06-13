import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getReplenishmentSectionState,
  ReplenishmentScreen,
} from '@/features/scm-replenishment';

export const dynamic = 'force-dynamic';

/**
 * scm replenishment-suggestions operator screen route (TASK-PC-FE-077 — the
 * ADR-MONO-027 wms→scm replenishment loop's console operator gate; the FIRST
 * scm operator-MUTATION surface). A sub-route of the existing `/scm` section,
 * registry-driven (`productKey=scm`); an in-console nav destination under the
 * scm group.
 *
 * Server component. scm demand-planning is reached server-side with the
 * HttpOnly **domain-facing IAM OIDC access token** (NOT the IAM exchanged
 * operator token — § 2.4.6.1 reuses the § 2.4.5/§ 2.4.6 per-domain credential
 * rule; the #569 invariant is GAP-domain-scoped and the scm gateway *requires*
 * the IAM OIDC token). The two operator actions (approve / dismiss) ride the
 * SAME credential as the reads (scm has no operator-token exchange).
 *
 * Eligibility (§ 2.4.6.1 reusing § 2.4.6 tenant-model divergence): scm resolves
 * the tenant from the JWT `tenant_id ∈ {scm,*}` claim producer-side — the
 * console sends no tenant. The page resolves the operator's scm eligibility
 * from the data-driven registry and passes it into
 * `getReplenishmentSectionState()`. Resilience (§ 2.5): 401 → whole-session
 * re-login; 403 → inline "not scoped"; 429 → rate-limited notice; 503/timeout
 * → only this section degrades (the `(console)` shell + the FE-008 scm read
 * section stay).
 */
export default async function ReplenishmentPage() {
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
      <section aria-labelledby="repl-heading">
        <h1 id="repl-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 운영
        </h1>
        <div
          role="status"
          data-testid="repl-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 보충 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getReplenishmentSectionState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="repl-heading">
        <h1 id="repl-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 운영
        </h1>
        <div
          role="status"
          data-testid="repl-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            scm 보충 운영 화면에 대한 접근 권한이 없습니다.
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
      <section aria-labelledby="repl-heading">
        <h1 id="repl-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 운영
        </h1>
        <div
          role="status"
          data-testid="repl-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 보충 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 / 역할
          확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.rateLimited) {
    return (
      <section aria-labelledby="repl-heading">
        <h1 id="repl-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 운영
        </h1>
        <div
          role="status"
          data-testid="repl-ratelimited"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 게이트웨이 요청이 일시적으로 제한되었습니다. 잠시 후 다시
          시도하세요. 콘솔의 다른 기능은 계속 사용할 수 있습니다.
        </div>
      </section>
    );
  }

  if (state.degraded || !state.suggestions) {
    return (
      <section aria-labelledby="repl-heading">
        <h1 id="repl-heading" className="mb-6 text-2xl font-semibold">
          SCM 보충 운영
        </h1>
        <div
          role="status"
          data-testid="repl-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 보충 추천 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <ReplenishmentScreen suggestions={state.suggestions} />;
}
