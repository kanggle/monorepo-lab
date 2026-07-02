import Link from 'next/link';
import { DetailHeader, PromotionForm } from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce promotion CREATE route (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Server component eligibility pre-flight (no upstream read needed — the form
 * posts to the same-origin proxy on submit); only an eligible operator sees
 * the form. Mirrors the products new/page.tsx degrade/not-eligible branches.
 */
export default async function NewPromotionPage() {
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">프로모션 등록</h1>
  );

  if (registryDegraded) {
    return (
      <section>
        {heading}
        <div
          role="status"
          data-testid="promotion-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          ecommerce 운영 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시
          시도하세요.
        </div>
      </section>
    );
  }

  if (!eligible) {
    return (
      <section>
        {heading}
        <div
          role="status"
          data-testid="promotion-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 프로모션 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <Link
            href="/console"
            className="mt-2 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section>
      <DetailHeader
        headingId="promotion-new-heading"
        title="프로모션 등록"
        backHref="/ecommerce/promotions"
        backTestId="promotion-new-back"
      />
      <PromotionForm />
    </section>
  );
}
