import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getWmsOperationsState,
  WmsOperationsScreen,
} from '@/features/wms-ops';

export const dynamic = 'force-dynamic';

/**
 * wms **운영설정**(operations settings) section route (TASK-PC-FE-224 —
 * surfaces the previously-uncoded `GET /settings` read (§ 5.1) alongside
 * the already-exported-but-zero-consumer `getProjectionStatus()` (§ 6.2);
 * the SIXTH wms surface, after `/wms` § 2.4.5, `/wms/outbound` § 2.4.5.1,
 * `/wms/inventory` § 2.4.5, `/wms/inbound` § 2.4.5, and `/wms/master`
 * § 2.4.5). An in-console nav destination, NOT a catalog product re-route.
 *
 * Server component. wms is reached server-side with the HttpOnly **domain-
 * facing IAM OIDC access token** (NOT the IAM exchanged operator token —
 * § 2.4.5 per-domain credential divergence; the wms gateway *requires* the
 * IAM OIDC token).
 *
 * Eligibility (§ 2.4.5 tenant-model divergence): wms resolves the tenant
 * from the JWT `tenant_id=wms` claim producer-side — the console sends no
 * tenant. The page resolves the operator's wms eligibility from the
 * data-driven registry (`productKey=wms`) and passes it into
 * `getWmsOperationsState()`. Waterfall mirrors `wms/master/page.tsx`:
 * registryDegraded → notEligible → happy — UNLIKE every other single-read
 * wms section page, there is NO page-level forbidden/degraded branch here:
 * settings and projection-status fan out as TWO INDEPENDENT cells (mirrors
 * `getWmsOverviewState`'s `cell()` pattern) — `WmsOperationsScreen` owns
 * each section's own forbidden/degraded rendering, so one leg failing never
 * blanks the other (task Failure Scenarios).
 *
 * Out of Scope (task § Out of Scope): settings WRITE (config change) and
 * operator RBAC (users/roles/assignments) — `WMS_ADMIN` scope + console
 * write-admin range, out of this READ-ONLY task. `WMS_VIEWER` read only.
 */
export default async function WmsOperationsPage() {
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
      <section aria-labelledby="wms-operations-heading">
        <h1
          id="wms-operations-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 운영설정
        </h1>
        <div
          role="status"
          data-testid="wms-operations-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          wms 운영설정 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getWmsOperationsState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="wms-operations-heading">
        <h1
          id="wms-operations-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 운영설정
        </h1>
        <div
          role="status"
          data-testid="wms-operations-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            wms 운영설정 화면에 대한 접근 권한이 없습니다.
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

  return <WmsOperationsScreen state={state} />;
}
