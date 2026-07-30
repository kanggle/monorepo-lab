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
 * Thin wrapper (TASK-PC-FE-263) — owns `isAllDown`, its copy text,
 * and its testid, delegates the banner shell to `shared/ui/DegradeBanner`.
 */

export function isAllDown(cards: ReadonlyArray<Card>): boolean {
  if (cards.length === 0) return false;
  return cards.every((c) => c.status !== 'ok');
}

export interface OverviewDegradeBannerProps {
  /** The full envelope — used to seed the explicit-retry button's
   *  React Query initialData (no automatic refetch). */
  initial: OperatorOverview;
}

export function OverviewDegradeBanner({ initial }: OverviewDegradeBannerProps) {
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
