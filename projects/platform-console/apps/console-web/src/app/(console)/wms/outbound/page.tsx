import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  getOutboundSectionState,
  OutboundOpsScreen,
} from '@/features/wms-outbound-ops';
import {
  getWmsShipmentsState,
  WmsShipmentsScreen,
} from '@/features/wms-ops';
import type { WmsShipmentsSectionState } from '@/features/wms-ops';

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
 *
 * TASK-PC-FE-175 — the 택배/출고 read table (confirmed shipments: carrier /
 * tracking / shipped-at) moved here off the `/wms` 개요. It renders as a
 * SECTION below the outbound-order operations — the read-side companion to
 * the write-side operations. The page waterfall gates on OUTBOUND (the
 * primary content); the shipments section carries its OWN resilience
 * (`getWmsShipmentsState` — a 403/503 on shipments degrades only that
 * section, the outbound operations stay intact). Both share the same wms
 * eligibility, so the shipments fan-out is skipped when not eligible.
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

  // Outbound-order operations (primary content, gates the page) fetched in
  // parallel with the 택배/출고 shipments read (secondary section, own
  // resilience). Both share wms eligibility + the domain-facing IAM OIDC
  // token; each performs a whole-session `redirect('/login')` internally on a
  // 401 (no partial authed state).
  const [state, shipmentsState] = await Promise.all([
    getOutboundSectionState(eligible),
    getWmsShipmentsState(eligible),
  ]);

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

  return (
    <>
      <OutboundOpsScreen orders={state.orders} />
      <WmsShipmentsSection state={shipmentsState} />
    </>
  );
}

/**
 * The 택배/출고 read section (TASK-PC-FE-175) rendered below the outbound-order
 * operations. Server-side resilience gate mirroring the inventory page's
 * page-level waterfall, but SCOPED to this section only — a shipments 403/503
 * degrades just this block, never the outbound operations above it. On happy,
 * the seed hydrates the client `WmsShipmentsScreen` (filters + pagination).
 */
function WmsShipmentsSection({ state }: { state: WmsShipmentsSectionState }) {
  if (state.notEligible) {
    // Not wms-eligible: the outbound waterfall already surfaced the block —
    // render nothing extra here (no duplicate "no access" notice).
    return null;
  }
  if (state.forbidden) {
    return (
      <section aria-label="택배 / 출고 조회" className="mt-10">
        <h2 className="mb-3 text-lg font-medium text-foreground">택배 / 출고</h2>
        <div
          role="status"
          data-testid="wms-ship-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }
  if (state.degraded || !state.shipments) {
    return (
      <section aria-label="택배 / 출고 조회" className="mt-10">
        <h2 className="mb-3 text-lg font-medium text-foreground">택배 / 출고</h2>
        <div
          role="status"
          data-testid="wms-ship-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 출고/택배 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      </section>
    );
  }
  return (
    <WmsShipmentsScreen
      shipments={state.shipments}
      lagSeconds={state.lagSeconds}
    />
  );
}
