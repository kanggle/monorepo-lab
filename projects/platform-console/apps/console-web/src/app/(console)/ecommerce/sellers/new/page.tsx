import Link from 'next/link';
import {
  DetailHeader,
  SellerRegisterForm,
  ReadOnlyRouteNotice,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../products/_eligibility';
import { resolveEcommerceCanWrite } from '../../_write-access';

export const dynamic = 'force-dynamic';

/**
 * ecommerce seller REGISTER route (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area).
 * Server component eligibility pre-flight (no upstream read needed — the form
 * posts to the same-origin proxy on submit); only an eligible operator sees
 * the form. Mirrors the promotions new/page.tsx degrade/not-eligible branches.
 */
export default async function NewSellerPage() {
  const eligibility = await resolveEcommerceEligibility();
  const { eligible, registryDegraded } = eligibility;

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">셀러 등록</h1>
  );

  if (registryDegraded) {
    return (
      <section>
        {heading}
        <div
          role="status"
          data-testid="seller-degraded"
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
          data-testid="seller-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 셀러 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <Link
            href="/dashboards/overview"
            className="mt-2 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            개요로 이동
          </Link>
        </div>
      </section>
    );
  }

  // TASK-PC-FE-346 — a non-writer gets the notice INSTEAD of the form (contract § 2.4.3.4); the
  // server would only answer the submit with a 403.
  if (!(await resolveEcommerceCanWrite('/ecommerce/sellers', eligibility))) {
    return (
      <ReadOnlyRouteNotice
        heading="셀러 등록"
        testId="seller-form-read-only"
        action="셀러 등록"
        roleLabel="상품 변경 — 셀러는 상품 영역 권한을 따릅니다"
        backHref={'/ecommerce/sellers'}
        backLabel="목록으로"
      />
    );
  }

  return (
    <section>
      <DetailHeader
        headingId="seller-new-heading"
        title="셀러 등록"
        backHref="/ecommerce/sellers"
        backTestId="seller-new-back"
      />
      <SellerRegisterForm />
    </section>
  );
}
