import Link from 'next/link';
import {
  DetailHeader,
  TemplateForm,
  ReadOnlyRouteNotice,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../../products/_eligibility';
import { resolveEcommerceCanWrite } from '../../../_write-access';

export const dynamic = 'force-dynamic';

/**
 * ecommerce notification template CREATE route (TASK-PC-FE-089 — ADR-031 Phase 5b).
 * Server component eligibility pre-flight (no upstream read needed — the form
 * posts to the same-origin proxy on submit); only an eligible operator sees
 * the form. Mirrors the promotions new/page.tsx degrade/not-eligible branches.
 */
export default async function NewTemplatePage() {
  const eligibility = await resolveEcommerceEligibility();
  const { eligible, registryDegraded } = eligibility;

  const heading = (
    <h1 className="mb-6 text-2xl font-semibold">알림 템플릿 등록</h1>
  );

  if (registryDegraded) {
    return (
      <section>
        {heading}
        <div
          role="status"
          data-testid="notification-degraded"
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
          data-testid="notification-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 알림 템플릿 운영 화면에 대한 접근 권한이 없습니다.
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
  if (!(await resolveEcommerceCanWrite('/ecommerce/notifications/templates', eligibility))) {
    return (
      <ReadOnlyRouteNotice
        heading="알림 템플릿 등록"
        testId="notification-form-read-only"
        action="알림 템플릿 등록"
        roleLabel="알림 변경"
        backHref={'/ecommerce/notifications/templates'}
        backLabel="목록으로"
      />
    );
  }

  return (
    <section>
      <DetailHeader
        headingId="notification-template-new-heading"
        title="알림 템플릿 등록"
        backHref="/ecommerce/notifications/templates"
        backTestId="notification-new-back"
      />
      <TemplateForm />
    </section>
  );
}
