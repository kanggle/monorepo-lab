import type { Card } from '../api/operator-overview-types';
import { RetryButton } from './RetryButton';
import type { OperatorOverview } from '../api/operator-overview-types';

/**
 * Banner shown when EVERY one of the 6 cards is non-`ok`
 * (TASK-PC-FE-011). Server component. The all-down state is the
 * BFF's D5.A discipline — composition still emits HTTP 200 with all
 * 6 cards in `degraded`/`forbidden` states; the console must not
 * blank the shell. This banner makes the operator aware that the
 * whole envelope is currently degraded and surfaces the explicit
 * retry affordance at the top.
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
  if (!isAllDown(initial.cards)) return null;
  return (
    <div
      role="status"
      data-testid="operator-overview-all-degraded"
      className="mb-6 flex items-start justify-between gap-4 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
    >
      <div>
        <p className="font-medium text-foreground">
          모든 도메인의 개요 정보를 일시적으로 불러올 수 없습니다.
        </p>
        <p className="mt-1">
          콘솔 자체는 정상 동작합니다. 잠시 후 아래에서 다시 시도하거나
          각 도메인 화면으로 직접 이동하세요.
        </p>
      </div>
      <RetryButton initial={initial} testidSuffix="banner" />
    </div>
  );
}
