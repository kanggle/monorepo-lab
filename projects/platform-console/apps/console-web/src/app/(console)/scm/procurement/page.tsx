import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getScmProcurementState,
  ScmProcurementScreen,
} from '@/features/scm-ops';

export const dynamic = 'force-dynamic';

/**
 * scm 조달 (procurement PO list) route — split out of the former combined
 * `/scm` screen (TASK-PC-FE-220; read section TASK-PC-FE-008). Server
 * component, STRICTLY READ-ONLY. Same eligibility pre-flight + resilience
 * discipline as `/scm` / `/scm/inventory` (§ 2.4.6): the page resolves scm
 * eligibility from the data-driven registry and passes it into
 * `getScmProcurementState()` so no cross-tenant call is ever fabricated for
 * a non-eligible operator. 401 → whole-session re-login; 403 → inline "not
 * scoped"; 429 → rate-limited notice; 503/timeout → this section degrades
 * only (the console shell + other sections stay).
 */
export default async function ScmProcurementPage() {
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
          SCM 조달
        </h1>
        <div
          role="status"
          data-testid="scm-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 조달 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getScmProcurementState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 조달
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
          SCM 조달
        </h1>
        <div
          role="status"
          data-testid="scm-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 조달 화면을 조회할 권한이 없습니다. (테넌트 스코프 / 역할
          확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.rateLimited) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 조달
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

  if (state.degraded || !state.poList) {
    return (
      <section aria-labelledby="scm-heading">
        <h1 id="scm-heading" className="mb-6 text-2xl font-semibold">
          SCM 조달
        </h1>
        <div
          role="status"
          data-testid="scm-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          scm 조달 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <ScmProcurementScreen poList={state.poList} />;
}
