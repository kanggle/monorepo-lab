import Link from 'next/link';
import { ProductForm } from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce product REGISTER route (TASK-PC-FE-081 — § 2.4.10 #3). Server
 * component eligibility pre-flight (no upstream read needed — the form posts
 * to the same-origin proxy on submit); only an eligible operator sees the
 * form. Mirrors the list-page degrade/not-eligible branches.
 */
export default async function NewProductPage() {
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">상품 등록</h1>
  );

  if (registryDegraded) {
    return (
      <section>
        {heading}
        <div
          role="status"
          data-testid="product-degraded"
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
          data-testid="product-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 상품 운영 화면에 대한 접근 권한이 없습니다.
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
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">상품 등록</h1>
        <Link href="/ecommerce/products" className="text-sm underline">
          목록으로
        </Link>
      </div>
      <ProductForm />
    </section>
  );
}
