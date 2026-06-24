import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getOutboundSectionState,
  OutboundOpsScreen,
} from '@/features/wms-outbound-ops';

export const dynamic = 'force-dynamic';

/**
 * wms outbound operations section route (TASK-PC-FE-057 — the second wms
 * surface; the on-screen operator leg of ADR-MONO-022 § D7). An in-console
 * nav destination, NOT a catalog product re-route.
 *
 * Server component. wms is reached server-side with the HttpOnly
 * **domain-facing IAM OIDC access token** (NOT the IAM exchanged operator
 * token — § 2.4.5.1 per-domain credential divergence; the wms gateway
 * *requires* the IAM OIDC token).
 *
 * Eligibility (§ 2.4.5 tenant-model divergence, inherited by § 2.4.5.1): wms
 * resolves the tenant from the JWT `tenant_id=wms` claim producer-side — the
 * console sends no tenant. The page resolves the operator's wms eligibility
 * from the data-driven registry (`productKey=wms`) and passes it into
 * `getOutboundSectionState()`. Waterfall mirrors `wms/page.tsx`:
 * registryDegraded → notEligible → forbidden → degraded → happy.
 */
export default async function WmsOutboundPage() {
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
      <section aria-labelledby="wms-outbound-heading">
        <h1
          id="wms-outbound-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 출고
        </h1>
        <div
          role="status"
          data-testid="outbound-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          wms 출고 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getOutboundSectionState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="wms-outbound-heading">
        <h1
          id="wms-outbound-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 출고
        </h1>
        <div
          role="status"
          data-testid="outbound-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            wms 출고 운영 화면에 대한 접근 권한이 없습니다.
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
      <section aria-labelledby="wms-outbound-heading">
        <h1
          id="wms-outbound-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 출고
        </h1>
        <div
          role="status"
          data-testid="outbound-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded || !state.orders) {
    return (
      <section aria-labelledby="wms-outbound-heading">
        <h1
          id="wms-outbound-heading"
          className="mb-6 text-2xl font-semibold"
        >
          WMS 출고
        </h1>
        <div
          role="status"
          data-testid="outbound-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          wms 출고 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <OutboundOpsScreen orders={state.orders} />;
}
