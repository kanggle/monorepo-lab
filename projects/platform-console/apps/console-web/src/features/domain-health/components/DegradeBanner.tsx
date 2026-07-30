import type { Card, DomainHealth } from '../api/types';
import { RetryButton } from './RetryButton';
import { DegradeBanner as SharedDegradeBanner } from '@/shared/ui/DegradeBanner';

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
 *
 * Thin wrapper (TASK-PC-FE-263) — owns `isAllDegraded`, its copy text,
 * and its testid, delegates the banner shell to `shared/ui/DegradeBanner`.
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
  return (
    <SharedDegradeBanner
      show={isAllDegraded(initial.cards)}
      testid="domain-health-all-degraded"
      heading="모든 도메인의 상태 정보를 일시적으로 불러올 수 없습니다."
      description="콘솔 자체는 정상 동작합니다. 잠시 후 아래에서 다시 시도하거나 각 도메인 화면으로 직접 이동하세요."
      retry={<RetryButton initial={initial} testidSuffix="banner" />}
    />
  );
}
