import Link from 'next/link';
import {
  getShippingsSectionState,
  ShippingsScreen,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce shipping operations list route (TASK-PC-FE-088 — ADR-031 Phase 4b).
 * An in-console nav destination under the `/ecommerce` section.
 *
 * Server component. Eligibility waterfall mirrors /ecommerce/promotions/page.tsx:
 * registryDegraded → notEligible → forbidden → degraded → happy.
 *
 * Reuses the products eligibility resolver (same ecommerce productKey gate).
 * Base URL: ECOMMERCE_PUBLIC_BASE_URL + /shippings (non-admin path).
 */
export default async function EcommerceShippingsPage() {
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1
      id="ecommerce-shippings-heading"
      className="mb-6 text-2xl font-semibold"
    >
      E-Commerce 배송 운영
    </h1>
  );

  if (registryDegraded) {
    return (
      <section aria-labelledby="ecommerce-shippings-heading">
        {heading}
        <div
          role="status"
          data-testid="shipping-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 배송 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getShippingsSectionState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="ecommerce-shippings-heading">
        {heading}
        <div
          role="status"
          data-testid="shipping-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 배송 운영 화면에 대한 접근 권한이 없습니다.
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

  if (state.forbidden) {
    return (
      <section aria-labelledby="ecommerce-shippings-heading">
        {heading}
        <div
          role="status"
          data-testid="shipping-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded || !state.shippings) {
    return (
      <section aria-labelledby="ecommerce-shippings-heading">
        {heading}
        <div
          role="status"
          data-testid="shipping-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 배송 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <ShippingsScreen shippings={state.shippings} />;
}
