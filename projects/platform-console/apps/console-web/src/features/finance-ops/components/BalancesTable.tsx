import {
  formatMoney,
  balanceMoney,
  type Balance,
} from '../api/types';
import { BarSwatch, StackedBar } from '@/shared/ui/InlineBar';
import { balanceBar } from '../lib/balance-bars';

/**
 * Balances table (TASK-PC-FE-009 — § 2.4.7).
 *
 * F5 money rendering (CONTRACT obligation, NOT a UX nicety):
 *   - every Balance row's `ledger` / `available` / `held` is a
 *     **string** of integer minor units (the producer wire shape from
 *     `account-api.md` § GET balances);
 *   - rendering goes through `formatMoney(...)` ONLY — pure string
 *     manipulation + the per-currency scale lookup; NO float / JS
 *     `Number(...)` / `parseFloat(...)` / `parseInt(...)` is applied
 *     to `amount` anywhere (a test grep-asserts this against the
 *     on-disk source).
 *
 * TASK-PC-FE-361: each currency row also gets a decorative «available + held»
 * stacked bar (scale = the row's ledger). Widths come from `lib/balance-bars`
 * (`chartMagnitude`, the F5 chart-geometry exception) — never text.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface BalancesTableProps {
  balances: Balance[];
}

export function BalancesTable({ balances }: BalancesTableProps) {
  if (!balances || balances.length === 0) {
    return (
      <p
        className="mb-6 text-sm text-muted-foreground"
        data-testid="finance-balances-empty"
      >
        잔액이 없습니다.
      </p>
    );
  }
  const bars = balances.map((b) => balanceBar(b));
  const anyBar = bars.some((b) => b !== null);
  return (
    <div className="mb-6">
      <table className="data-table" data-testid="finance-balances-table">
        <caption className="sr-only">잔액</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              통화
            </th>
            <th scope="col" className="p-2">
              장부 (ledger)
            </th>
            <th scope="col" className="p-2">
              가용 (available)
            </th>
            <th scope="col" className="p-2">
              holding
            </th>
            <th scope="col" className="p-2" data-testid="finance-balance-bar-head">
              <BarSwatch tone="progress" />
              가용 <BarSwatch tone="warning" />
              보류
            </th>
          </tr>
        </thead>
        <tbody>
          {balances.map((b, i) => {
            // F5 — pure string passing into `formatMoney`. NO `Number()`
            // coercion of `amount` anywhere on this line or under
            // `features/finance-ops/`.
            const m = balanceMoney(b);
            const bar = bars[i];
            return (
              <tr
                key={`${b.currency}-${i}`}
                data-testid={`finance-balance-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{b.currency}</td>
                <td
                  className="p-2"
                  data-testid={`finance-balance-ledger-${i}`}
                >
                  {formatMoney(m.ledger)}
                </td>
                <td
                  className="p-2"
                  data-testid={`finance-balance-available-${i}`}
                >
                  {formatMoney(m.available)}
                </td>
                <td
                  className="p-2"
                  data-testid={`finance-balance-held-${i}`}
                >
                  {formatMoney(m.held)}
                </td>
                <td className="w-40 p-2">
                  {bar ? (
                    <StackedBar
                      segments={bar.segments}
                      expected={bar.expected}
                      testid={`finance-balance-bar-${i}`}
                    />
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {anyBar ? (
        <p
          className="mt-2 text-xs text-muted-foreground"
          data-testid="finance-balance-bar-scope"
        >
          막대는 통화별 장부 잔액 대비 가용 · 보류 비율입니다.
        </p>
      ) : null}
    </div>
  );
}
