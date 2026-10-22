import { chartMagnitude } from '@/shared/lib/chart-scale';
import type { TrialBalanceAccount } from '../api/types';

/**
 * Trial-balance debit / credit bar pairs (TASK-PC-FE-361) — pure helper for
 * `TrialBalanceTable`. Widths only: magnitudes come from `chartMagnitude` (the
 * F5 chart-geometry exception) and feed nothing a person reads; the totals and
 * the `inBalance` verdict stay the producer's.
 *
 * Grouping rule (AC-0 ①): accounts may carry different transaction currencies,
 * and minor units of different currencies are not comparable. The bars use the
 * ORIGINAL debit / credit totals, and an account's group is its currency
 * (`debitTotal.currency`). Within one group every bar is scaled against that
 * group's largest debit-or-credit magnitude. An account whose debit and credit
 * currencies disagree (should not happen) or are blank is drawn with NO bars —
 * never against a foreign scale. Negative / unparsable amounts count as 0.
 */
export interface TrialBalanceBarPair {
  debit: number;
  credit: number;
  /** Group scale: largest debit / credit magnitude among same-currency accounts. */
  max: number;
  currency: string;
}

function positive(amount: string): number {
  const m = chartMagnitude(amount);
  return m !== null && m > 0 ? m : 0;
}

/** One entry per account (same order); `null` = no bars for that account. */
export function trialBalanceBars(
  accounts: readonly TrialBalanceAccount[],
): (TrialBalanceBarPair | null)[] {
  const groupMax = new Map<string, number>();
  const parsed = accounts.map((a) => {
    const currency = a.debitTotal.currency;
    if (!currency || currency !== a.creditTotal.currency) return null;
    const debit = positive(a.debitTotal.amount);
    const credit = positive(a.creditTotal.amount);
    groupMax.set(currency, Math.max(groupMax.get(currency) ?? 0, debit, credit));
    return { currency, debit, credit };
  });
  return parsed.map((p) => {
    if (!p) return null;
    const max = groupMax.get(p.currency) ?? 0;
    return max > 0 ? { ...p, max } : null;
  });
}

/** True when the drawn bars span more than one currency group. */
export function trialBalanceBarCurrencies(
  pairs: readonly (TrialBalanceBarPair | null)[],
): string[] {
  return Array.from(new Set(pairs.flatMap((p) => (p ? [p.currency] : []))));
}
