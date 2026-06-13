import Link from 'next/link';
import { redirect } from 'next/navigation';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import {
  DomainHealthCard,
  getDomainHealthState,
} from '@/features/domain-health';

export const dynamic = 'force-dynamic';

/**
 * ecommerce operations section route (TASK-MONO-241 — ADR-MONO-030 Step 4
 * facet a-후속). The drill-in destination for the `ecommerce` catalog tile
 * (`baseRoute=/ecommerce`, added by TASK-MONO-240) — this route closes the
 * "tile click → 404" gap MONO-240 left open. An in-console nav destination,
 * NOT a catalog product re-route — the catalog `ecommerce.baseRoute` stays
 * data-driven; an `available:false` ecommerce is handled by the catalog's
 * existing "coming soon" path (this route does not hard-crash when ecommerce
 * is unavailable).
 *
 * Server component. STRICTLY READ-ONLY. v1 content = the ecommerce
 * domain-health summary (the `/actuator/health` leg surfaced as the 6th
 * Domain Health card, § 2.4.9.2) + a "상세 운영 표면 준비중" note. The rich
 * operations surface (product/order/seller management) and the
 * operator-overview snapshot leg are deferred follow-ups (facet a-후속-2 /
 * later facets).
 *
 * Eligibility (§ 2.2): resolved from the data-driven registry — the app layer
 * is the layer allowed to compose `features/*`. A registry 401 → whole-session
 * re-login (no partial authed state); a registry failure → degraded (cannot
 * prove ineligibility from a failed registry). Resilience (§ 2.5): only this
 * section degrades on a transient failure; the `(console)` shell + other
 * sections stay intact (mirrors the scm/wms section degrade discipline).
 */
export default async function EcommercePage() {
  // Eligibility pre-flight from the data-driven registry (§ 2.2).
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    if (catalog.degraded) {
      // Registry timeout / 5xx / circuit-open — cannot prove ineligibility
      // from a degraded registry; render the degraded note, never block on an
      // unproven negative (§ 2.5).
      registryDegraded = true;
    } else {
      const ecommerce = catalog.products.find(
        (p) => p.productKey === 'ecommerce',
      );
      eligible = Boolean(
        ecommerce && ecommerce.available && ecommerce.tenants.length > 0,
      );
    }
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  const heading = (
    <h1 id="ecommerce-heading" className="mb-6 text-2xl font-semibold">
      E-Commerce 운영
    </h1>
  );

  if (registryDegraded) {
    return (
      <section aria-labelledby="ecommerce-heading">
        {heading}
        <div
          role="status"
          data-testid="ecommerce-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  if (!eligible) {
    return (
      <section aria-labelledby="ecommerce-heading">
        {heading}
        <div
          role="status"
          data-testid="ecommerce-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 ecommerce 테넌트 스코프가 부여되어 있지
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

  // Eligible — surface the ecommerce domain-health card (the public
  // /actuator/health leg, § 2.4.9.2). The health fan-out is tenant-agnostic
  // infra liveness; a BFF-unavailable / unauthorized envelope collapses to a
  // compact note WITHOUT blanking the section (degrade-safe).
  const healthState = await getDomainHealthState();
  if (healthState.unauthorized) {
    redirect('/login');
  }
  const ecommerceCard =
    healthState.health?.cards.find((c) => c.domain === 'ecommerce') ?? null;

  return (
    <section aria-labelledby="ecommerce-heading" data-testid="ecommerce-section">
      {heading}

      <div className="mb-8">
        <h2 className="mb-3 text-lg font-semibold text-foreground">
          도메인 상태
        </h2>
        {ecommerceCard && healthState.health ? (
          <div className="max-w-sm">
            <DomainHealthCard
              card={ecommerceCard}
              healthForRetry={healthState.health}
            />
          </div>
        ) : (
          <div
            role="status"
            data-testid="ecommerce-health-unavailable"
            className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
          >
            ecommerce 도메인 상태를 일시적으로 불러올 수 없습니다. 콘솔의 다른
            기능은 계속 사용할 수 있습니다.
          </div>
        )}
      </div>

      {/* 상품 운영 (TASK-PC-FE-081 — § 2.4.10 product CRUD). */}
      <div className="mb-8">
        <h2 className="mb-3 text-lg font-semibold text-foreground">운영</h2>
        <div className="flex flex-wrap gap-3">
          <Link
            href="/ecommerce/products"
            data-testid="ecommerce-products-link"
            className="rounded-md border border-border bg-background px-4 py-3 text-sm font-medium text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            상품 운영 →
          </Link>
        </div>
      </div>

      <div
        data-testid="ecommerce-ops-coming-soon"
        className="rounded-md border border-dashed border-border bg-background px-4 py-6 text-sm text-muted-foreground"
      >
        <p className="mb-1 font-medium text-foreground">
          추가 운영 표면 준비중
        </p>
        <p>
          주문 · 셀러 관리 운영 화면은 후속 작업에서 제공됩니다. 상품 운영(목록 ·
          상세 · 등록 · 수정 · 삭제 · 옵션 · 재고)은 위 “상품 운영”에서 사용할 수
          있습니다.
        </p>
      </div>
    </section>
  );
}
