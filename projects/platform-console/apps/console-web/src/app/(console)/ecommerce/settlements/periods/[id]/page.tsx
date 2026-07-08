import Link from 'next/link';
import {
  getPeriodPayoutsSectionState,
  PeriodPayoutsScreen,
} from '@/features/ecommerce-ops';
import { resolveEcommerceEligibility } from '../../../products/_eligibility';

export const dynamic = 'force-dynamic';

/**
 * ecommerce settlement period detail route (TASK-PC-FE-221 Phase A) — the
 * per-period payouts list. Server component; eligibility waterfall mirrors the
 * settlements landing page. There is no single GET period-by-id read — the
 * period is identified by its id and its payouts are the content.
 */
export default async function EcommerceSettlementPeriodPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  const { eligible, registryDegraded } = await resolveEcommerceEligibility();

  const heading = (
    <h1 id="settlements-period-heading" className="mb-6 text-2xl font-semibold">
      정산 기간 지급 내역 · {id}
    </h1>
  );

  const backLink = (
    <Link
      href="/ecommerce/settlements"
      className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      정산 목록으로 이동
    </Link>
  );

  if (registryDegraded) {
    return (
      <section aria-labelledby="settlements-period-heading">
        {heading}
        <div
          role="status"
          data-testid="settlements-payouts-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          정산 지급 내역을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getPeriodPayoutsSectionState(eligible, id);

  if (state.notEligible) {
    return (
      <section aria-labelledby="settlements-period-heading">
        {heading}
        <div
          role="status"
          data-testid="settlements-payouts-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            ecommerce 정산 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 ecommerce 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
          </p>
          {backLink}
        </div>
      </section>
    );
  }

  if (state.forbidden) {
    return (
      <section aria-labelledby="settlements-period-heading">
        {heading}
        <div
          role="status"
          data-testid="settlements-payouts-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 화면을 조회할 권한이 없습니다. (운영자 역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.notFound) {
    return (
      <section aria-labelledby="settlements-period-heading">
        {heading}
        <div
          role="status"
          data-testid="settlements-payouts-notfound"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p>해당 정산 기간을 찾을 수 없습니다.</p>
          {backLink}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.payouts) {
    return (
      <section aria-labelledby="settlements-period-heading">
        {heading}
        <div
          role="status"
          data-testid="settlements-payouts-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          정산 지급 내역을 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <PeriodPayoutsScreen periodId={id} initialPayouts={state.payouts} />
  );
}
