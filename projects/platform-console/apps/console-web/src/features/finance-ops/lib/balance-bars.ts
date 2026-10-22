import { chartMagnitude } from '@/shared/lib/chart-scale';
import { stackedBase, type BarSegment } from '@/shared/ui/InlineBar';
import type { Balance } from '../api/types';

/**
 * «사용 가능 + 보류» bar per currency row (TASK-PC-FE-361) — pure helper for
 * `BalancesTable`. Widths only: the magnitudes come from `chartMagnitude` (the
 * F5 chart-geometry exception) and are used for nothing a person reads; every
 * amount stays the producer string rendered by `formatMoney`.
 *
 * Each currency row is its own bar (one row = one currency, so nothing of a
 * different currency is ever compared). The whole is `max(ledger, available +
 * held)` — `StackedBar` scales to the sum when held + available overshoot the
 * ledger, so an impossible-but-possible shape is shown, not hidden. A negative
 * or unparsable amount counts as 0 (the text column still shows it).
 */
export interface BalanceBar {
  segments: BarSegment[];
  /** Nominal whole = the ledger magnitude (0 when unusable). */
  expected: number;
}

function positive(amount: string): number {
  const m = chartMagnitude(amount);
  return m !== null && m > 0 ? m : 0;
}

/** `null` when the row has nothing to draw (ledger 0 and available + held 0). */
export function balanceBar(b: Balance): BalanceBar | null {
  const segments: BarSegment[] = [
    { key: 'available', value: positive(b.available), tone: 'progress' },
    { key: 'held', value: positive(b.held), tone: 'warning' },
  ];
  const expected = positive(b.ledger);
  if (stackedBase(segments, expected) <= 0) return null;
  return { segments, expected };
}
