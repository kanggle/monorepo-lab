import Link from 'next/link';
import {
  getProductDetailSectionState,
  ProductForm,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce product EDIT route (TASK-PC-FE-081 — § 2.4.10 #4). Server
 * component: eligibility pre-flight → seeded detail read (the form needs the
 * current values) → degrade waterfall → `ProductForm` in UPDATE mode. Variant
 * CRUD is on the detail page, not here.
 */
export default async function ProductEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = <h1 className="mb-6 text-2xl font-semibold">상품 수정</h1>;

  const note = (testid: string, msg: string) => (
    <section>
      {heading}
      <div
        role="status"
        data-testid={testid}
        className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
      >
        {msg}
      </div>
      <Link href="/ecommerce/products" className="mt-4 inline-block text-sm underline">
        목록으로
      </Link>
    </section>
  );

  if (registryDegraded) {
    return note(
      'product-degraded',
      'ecommerce 상품 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  const state = await getProductDetailSectionState(eligible, id);

  if (state.notEligible) {
    return note(
      'product-not-eligible',
      'ecommerce 상품 운영 화면에 대한 접근 권한이 없습니다. 운영자 관리자에게 문의하세요.',
    );
  }
  if (state.forbidden) {
    return note(
      'product-forbidden',
      '이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)',
    );
  }
  if (state.notFound) {
    return note(
      'product-not-found',
      '대상 상품을 찾을 수 없습니다. 목록에서 다시 선택하세요.',
    );
  }
  if (state.degraded || !state.detail) {
    return note(
      'product-degraded',
      'ecommerce 상품 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  return (
    <section>
      <div className="mb-6 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">상품 수정</h1>
        <Link href={`/ecommerce/products/${id}`} className="text-sm underline">
          상세로
        </Link>
      </div>
      <ProductForm existing={state.detail} />
    </section>
  );
}
