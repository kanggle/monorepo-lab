import type { Card, DomainHealth } from '../api/types';
import { RetryButton } from './RetryButton';

/**
 * Banner shown when EVERY one of the 5 cards is `degraded`
 * (TASK-PC-FE-013). Server component. The all-down state is the
 * BFF's D5.A discipline — composition still emits HTTP 200 with all
 * 5 cards in `degraded` states; the console must not blank the
 * shell. This banner makes the operator aware that the whole
 * envelope is currently degraded and surfaces the explicit retry
 * affordance at the top.
 *
 * NOTE: per § 2.4.9.2 invariant, `forbidden` is never emitted on
 * this route — "all non-ok" reduces to "all degraded" here (the
 * card status union is `'ok' | 'degraded'`).
 */

export function isAllDegraded(cards: ReadonlyArray<Card>): boolean {
  if (cards.length === 0) return false;
  return cards.every((c) => c.status === 'degraded');
}

export interface DegradeBannerProps {
  /** The full envelope — used to seed the explicit-retry button's
   *  React Query initialData (no automatic refetch). */
  initial: DomainHealth;
}

export function DegradeBanner({ initial }: DegradeBannerProps) {
  if (!isAllDegraded(initial.cards)) return null;
  return (
    <div
      role="status"
      data-testid="domain-health-all-degraded"
      className="mb-6 flex items-start justify-between gap-4 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
    >
      <div>
        <p className="font-medium text-foreground">
          모든 도메인의 상태 정보를 일시적으로 불러올 수 없습니다.
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
