import type { Card } from '../api/operator-overview-types';
import { RetryButton } from './RetryButton';
import type { OperatorOverview } from '../api/operator-overview-types';
import { DegradeBanner as SharedDegradeBanner } from '@/shared/ui/DegradeBanner';

/**
 * Banner shown when EVERY one of the 6 cards is non-`ok`
 * (TASK-PC-FE-011). Server component. The all-down state is the
 * BFF's D5.A discipline — composition still emits HTTP 200 with all
 * 6 cards in `degraded`/`forbidden` states; the console must not
 * blank the shell. This banner makes the operator aware that the
 * whole envelope is currently degraded and surfaces the explicit
 * retry affordance at the top.
 *
 * 🔴 All-`forbidden` is a different banner (TASK-PC-FE-307): every leg
 * refused this operator/tenant, so nothing is down and retrying returns
 * the same envelope. It must not read as an outage, and it carries no
 * retry. A single `degraded` card among the rest keeps the outage banner —
 * there retry can change the answer.
 *
 * Thin wrapper (TASK-PC-FE-263) — owns `isAllDown`/`isAllForbidden`, their
 * copy text and testids, delegates the banner shell to `shared/ui/DegradeBanner`.
 */

export function isAllDown(cards: ReadonlyArray<Card>): boolean {
  if (cards.length === 0) return false;
  return cards.every((c) => c.status !== 'ok');
}

export function isAllForbidden(cards: ReadonlyArray<Card>): boolean {
  if (cards.length === 0) return false;
  return cards.every((c) => c.status === 'forbidden');
}

export interface OverviewDegradeBannerProps {
  /** The full envelope — used to seed the explicit-retry button's
   *  React Query initialData (no automatic refetch). */
  initial: OperatorOverview;
}

export function OverviewDegradeBanner({ initial }: OverviewDegradeBannerProps) {
  if (isAllForbidden(initial.cards)) {
    return (
      <SharedDegradeBanner
        show
        testid="operator-overview-all-forbidden"
        heading="이 계정과 테넌트로 볼 수 있는 도메인 개요가 없습니다."
        description="모든 도메인이 이 운영자의 개요 조회를 허용하지 않았습니다. 각 카드의 사유를 확인하고, 테넌트를 바꾸거나 운영자 권한을 확인하세요."
        retry={null}
      />
    );
  }
  return (
    <SharedDegradeBanner
      show={isAllDown(initial.cards)}
      testid="operator-overview-all-degraded"
      heading="모든 도메인의 개요 정보를 일시적으로 불러올 수 없습니다."
      description="콘솔 자체는 정상 동작합니다. 잠시 후 아래에서 다시 시도하거나 각 도메인 화면으로 직접 이동하세요."
      retry={<RetryButton initial={initial} testidSuffix="banner" />}
    />
  );
}
