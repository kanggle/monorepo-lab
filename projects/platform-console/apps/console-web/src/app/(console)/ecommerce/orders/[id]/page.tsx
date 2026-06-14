import Link from 'next/link';
import {
  getOrderDetailSectionState,
  OrderDetail,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce order DETAIL route (TASK-PC-FE-083 — § 2.4.10 #16). Server
 * component: eligibility pre-flight → seeded detail read → degrade waterfall
 * (registryDegraded → notEligible → forbidden → notFound → degraded → happy).
 * The detail page hosts the status-transition action area.
 */
export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">주문 상세</h1>
  );

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
      <Link
        href="/ecommerce/orders"
        className="mt-4 inline-block text-sm underline"
      >
        목록으로
      </Link>
    </section>
  );

  if (registryDegraded) {
    return note(
      'order-degraded',
      'ecommerce 주문 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  const state = await getOrderDetailSectionState(eligible, id);

  if (state.notEligible) {
    return note(
      'order-not-eligible',
      'ecommerce 주문 운영 화면에 대한 접근 권한이 없습니다. 운영자 관리자에게 문의하세요.',
    );
  }
  if (state.forbidden) {
    return note(
      'order-forbidden',
      '이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)',
    );
  }
  if (state.notFound) {
    return note(
      'order-not-found',
      '대상 주문을 찾을 수 없습니다. 목록에서 다시 선택하세요.',
    );
  }
  if (state.degraded || !state.detail) {
    return note(
      'order-degraded',
      'ecommerce 주문 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  return <OrderDetail order={state.detail} />;
}
