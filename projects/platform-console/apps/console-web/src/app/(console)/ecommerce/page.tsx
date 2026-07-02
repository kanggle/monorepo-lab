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
 * The 7 shipped ecommerce operator areas, mirroring the `ConsoleSidebarNav`
 * ecommerce children (§ 2.4.10). The 상품 tile keeps its original
 * `ecommerce-products-link` testid (back-compat); the rest use nav-parallel
 * testids. A new area must be added here AND in `ConsoleSidebarNav`.
 */
const ECOMMERCE_OPS_AREAS: ReadonlyArray<{
  href: string;
  label: string;
  testid: string;
}> = [
  { href: '/ecommerce/products', label: '상품 운영', testid: 'ecommerce-products-link' },
  { href: '/ecommerce/orders', label: '주문 운영', testid: 'ecommerce-orders-link' },
  { href: '/ecommerce/shippings', label: '배송 운영', testid: 'ecommerce-shippings-link' },
  { href: '/ecommerce/promotions', label: '프로모션 운영', testid: 'ecommerce-promotions-link' },
  { href: '/ecommerce/users', label: '사용자', testid: 'ecommerce-users-link' },
  { href: '/ecommerce/sellers', label: '셀러 운영', testid: 'ecommerce-sellers-link' },
  {
    href: '/ecommerce/notifications/templates',
    label: '알림 템플릿',
    testid: 'ecommerce-notifications-link',
  },
];

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
 * Server component. STRICTLY READ-ONLY. Content = the ecommerce domain-health
 * summary (the `/actuator/health` leg surfaced as the 6th Domain Health card,
 * § 2.4.9.2) + an operator-area **quick-launch grid** linking all 7 shipped
 * operator surfaces (products / orders / users / promotions / shippings /
 * notifications / sellers — TASK-PC-FE-081…090 + 154). The grid mirrors the
 * `ConsoleSidebarNav` ecommerce children (the primary nav path); it is a
 * redundant in-section convenience entry, so a new area must be added in both
 * places. (TASK-PC-FE-155 retired the Phase-1 products-only link + the stale
 * "주문·셀러 준비중" note that predated PC-FE-083…090.)
 *
 * Eligibility (§ 2.2): resolved from the data-driven registry — the app layer
 * is the layer allowed to compose `features/*`. A registry 401 → whole-session
 * re-login (no partial authed state); a registry failure → degraded (cannot
 * prove ineligibility from a failed registry). Resilience (§ 2.5): only this
 * section degrades on a transient failure; the `(console)` shell + other
 * sections stay intact (mirrors the scm/wms section degrade discipline).
 */
export default async function EcommercePage() {
  // Speculatively fire the domain-health fan-out concurrently with the
  // eligibility pre-flight (TASK-PC-FE-118, same pattern as TASK-PC-FE-117).
  // domain-health is tenant-agnostic public actuator liveness (not per-tenant
  // authorized data — TASK-PC-FE-061), so firing it before the eligibility
  // gate is safe. On the gated branches below (registryDegraded / not-eligible
  // / 401) this call is wasted, but those are degraded/rare while the eligible
  // hot path runs the health card on every load. `getDomainHealthState()`
  // never throws, so leaving `healthPromise` un-awaited on a gated branch
  // raises no unhandled rejection.
  const healthPromise = getDomainHealthState();

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
  // compact note WITHOUT blanking the section (degrade-safe). The call was
  // started up-front (concurrently with the eligibility pre-flight) and is
  // awaited here only on the eligible path. (TASK-PC-FE-118)
  const healthState = await healthPromise;
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

      {/* 운영 영역 quick-launch 그리드 — 7 shipped operator surfaces
          (TASK-PC-FE-081…090 + 154). Labels/hrefs mirror ConsoleSidebarNav's
          ecommerce children; the sidebar remains the primary nav path (§ 2.4.10,
          TASK-PC-FE-155). */}
      <div className="mb-8">
        <h2 className="mb-3 text-lg font-semibold text-foreground">운영</h2>
        <div
          data-testid="ecommerce-ops-links"
          className="flex flex-wrap gap-3"
        >
          {ECOMMERCE_OPS_AREAS.map((area) => (
            <Link
              key={area.href}
              href={area.href}
              data-testid={area.testid}
              className="rounded-md border border-border bg-background px-4 py-3 text-sm font-medium text-foreground transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              {area.label} →
            </Link>
          ))}
        </div>
      </div>
    </section>
  );
}
