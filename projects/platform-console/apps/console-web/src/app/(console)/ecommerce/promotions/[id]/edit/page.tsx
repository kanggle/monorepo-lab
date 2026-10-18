import Link from 'next/link';
import {
  DetailHeader,
  getPromotionDetailSectionState,
  PromotionForm,
  ReadOnlyRouteNotice,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../../products/_eligibility';
import { resolveEcommerceCanWrite } from '../../../_write-access';

export const dynamic = 'force-dynamic';

/**
 * ecommerce promotion EDIT route (TASK-PC-FE-086 — ADR-031 Phase 3b).
 * Server component: eligibility pre-flight → seeded detail read → degrade
 * waterfall → `PromotionForm` in UPDATE mode (PUT full replace).
 */
export default async function PromotionEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const eligibility = await resolveEcommerceEligibility();
  const { eligible, registryDegraded } = eligibility;

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">프로모션 수정</h1>
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
        href="/ecommerce/promotions"
        className="mt-4 inline-block text-sm underline"
      >
        목록으로
      </Link>
    </section>
  );

  if (registryDegraded) {
    return note(
      'promotion-degraded',
      'ecommerce 프로모션 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  const state = await getPromotionDetailSectionState(eligible, id);

  if (state.notEligible) {
    return note(
      'promotion-not-eligible',
      'ecommerce 프로모션 운영 화면에 대한 접근 권한이 없습니다. 운영자 관리자에게 문의하세요.',
    );
  }
  if (state.forbidden) {
    return note(
      'promotion-forbidden',
      '이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)',
    );
  }
  if (state.notFound) {
    return note(
      'promotion-not-found',
      '대상 프로모션을 찾을 수 없습니다. 목록에서 다시 선택하세요.',
    );
  }
  if (state.degraded || !state.detail) {
    return note(
      'promotion-degraded',
      'ecommerce 프로모션 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  // TASK-PC-FE-346 — a non-writer gets the notice INSTEAD of the form (contract § 2.4.3.4); the
  // server would only answer the submit with a 403.
  if (!(await resolveEcommerceCanWrite('/ecommerce/promotions', eligibility))) {
    return (
      <ReadOnlyRouteNotice
        heading="프로모션 수정"
        testId="promotion-form-read-only"
        action="프로모션 수정"
        roleLabel="프로모션 변경"
        backHref={`/ecommerce/promotions/${id}`}
        backLabel="상세로"
      />
    );
  }

  return (
    <section>
      <DetailHeader
        headingId="promotion-edit-heading"
        title="프로모션 수정"
        backHref={`/ecommerce/promotions/${id}`}
        backLabel="상세"
        backTestId="promotion-edit-back"
      />
      <PromotionForm existing={state.detail} />
    </section>
  );
}
