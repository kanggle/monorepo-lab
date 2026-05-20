import {
  formatMoney,
  balanceMoney,
  type Balance,
} from '../api/types';

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
  return (
    <table
      className="mb-6 w-full border-collapse text-sm"
      data-testid="finance-balances-table"
    >
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
        </tr>
      </thead>
      <tbody>
        {balances.map((b, i) => {
          // F5 — pure string passing into `formatMoney`. NO `Number()`
          // coercion of `amount` anywhere on this line or under
          // `features/finance-ops/`.
          const m = balanceMoney(b);
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
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
