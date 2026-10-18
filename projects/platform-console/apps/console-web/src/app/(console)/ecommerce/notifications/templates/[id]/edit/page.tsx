import { notFound } from 'next/navigation';
import Link from 'next/link';
import {
  DetailHeader,
  getNotificationDetailSectionState,
  TemplateForm,
  ReadOnlyRouteNotice,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../../../products/_eligibility';
import { resolveEcommerceCanWrite } from '../../../../_write-access';

export const dynamic = 'force-dynamic';

/**
 * ecommerce notification template EDIT route (TASK-PC-FE-089 — ADR-031 Phase 5b).
 * Server component: eligibility pre-flight → seeded detail read (BE-373 GET endpoint)
 * → degrade waterfall → `TemplateForm` in UPDATE mode (PUT subject+body only).
 *
 * type/channel are immutable — the form renders them read-only.
 * 404 TEMPLATE_NOT_FOUND → Next.js `notFound()`.
 */
export default async function TemplateEditPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const eligibility = await resolveEcommerceEligibility();
  const { eligible, registryDegraded } = eligibility;

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">알림 템플릿 수정</h1>
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
        href="/ecommerce/notifications/templates"
        className="mt-4 inline-block text-sm underline"
      >
        목록으로
      </Link>
    </section>
  );

  if (registryDegraded) {
    return note(
      'notification-degraded',
      'ecommerce 알림 템플릿 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  const state = await getNotificationDetailSectionState(eligible, id);

  if (state.notEligible) {
    return note(
      'notification-not-eligible',
      'ecommerce 알림 템플릿 운영 화면에 대한 접근 권한이 없습니다. 운영자 관리자에게 문의하세요.',
    );
  }
  if (state.forbidden) {
    return note(
      'notification-forbidden',
      '이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)',
    );
  }
  if (state.notFound) {
    notFound();
  }
  if (state.degraded || !state.detail) {
    return note(
      'notification-degraded',
      'ecommerce 알림 템플릿 정보를 일시적으로 불러올 수 없습니다. 잠시 후 다시 시도하세요.',
    );
  }

  // TASK-PC-FE-346 — a non-writer gets the notice INSTEAD of the form (contract § 2.4.3.4); the
  // server would only answer the submit with a 403.
  if (!(await resolveEcommerceCanWrite('/ecommerce/notifications/templates', eligibility))) {
    return (
      <ReadOnlyRouteNotice
        heading="알림 템플릿 수정"
        testId="notification-form-read-only"
        action="알림 템플릿 수정"
        roleLabel="알림 변경"
        backHref={'/ecommerce/notifications/templates'}
        backLabel="목록으로"
      />
    );
  }

  return (
    <section>
      <DetailHeader
        headingId="notification-template-edit-heading"
        title="알림 템플릿 수정"
        backHref="/ecommerce/notifications/templates"
        backTestId="notification-edit-back"
      />
      <TemplateForm existing={state.detail} />
    </section>
  );
}
