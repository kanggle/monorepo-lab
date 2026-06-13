import { formatMoney, type TrialBalance } from '../api/types';

/**
 * Trial balance table (TASK-PC-FE-072 — § 2.4.7.1).
 *
 * F5 money rendering (CONTRACT obligation, NOT a UX nicety):
 *   - every per-account debit / credit AND base debit / credit is a
 *     **string** of integer minor units (the producer wire shape from
 *     `ledger-api.md` § 4);
 *   - rendering goes through `formatMoney(...)` ONLY — pure string
 *     manipulation + the per-currency scale lookup; NO float / JS
 *     `Number(...)` / `parseFloat(...)` / `parseInt(...)` is applied
 *     to `amount` anywhere (a test grep-asserts this against the
 *     on-disk source).
 *
 * The double-entry `inBalance` invariant is surfaced HONESTLY — an
 * out-of-balance trial balance is shown as such (a danger badge), never
 * hidden or silently "corrected".
 *
 * TASK-PC-FE-074: the optional `onSelectAccount` prop makes each
 * `ledgerAccountCode` cell a focusable button that drills into the Account
 * tab. When absent, the cell stays plain text (FE-072 callers are
 * UNAFFECTED — no breaking change). All existing testids are preserved.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface TrialBalanceTableProps {
  trialBalance: TrialBalance;
  /** Optional drill-in callback (TASK-PC-FE-074). When provided, each
   *  account code cell becomes a focusable button that calls
   *  `onSelectAccount(code)`. When absent the cell stays plain text —
   *  FE-072 callers are unaffected. */
  onSelectAccount?: (code: string) => void;
}

export function TrialBalanceTable({
  trialBalance,
  onSelectAccount,
}: TrialBalanceTableProps) {
  const tb = trialBalance;
  return (
    <section aria-labelledby="ledger-tb-heading" className="mb-8">
      <div className="mb-3 flex items-center gap-3">
        <h2
          id="ledger-tb-heading"
          className="text-lg font-medium text-foreground"
        >
          시산표 (trial balance)
        </h2>
        <span
          data-testid="ledger-tb-inbalance"
          className={
            tb.inBalance
              ? 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
              : 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive'
          }
        >
          {tb.inBalance ? '대차 일치 (in balance)' : '대차 불일치 (out of balance)'}
        </span>
      </div>
      {tb.accounts.length === 0 ? (
        <p
          className="mb-4 text-sm text-muted-foreground"
          data-testid="ledger-tb-empty"
        >
          시산표 계정이 없습니다.
        </p>
      ) : (
        <table className="mb-4 data-table" data-testid="ledger-tb-table">
          <caption className="sr-only">시산표</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                계정 코드
              </th>
              <th scope="col" className="p-2">
                차변 (debit)
              </th>
              <th scope="col" className="p-2">
                대변 (credit)
              </th>
              <th scope="col" className="p-2">
                기준 차변 (base debit)
              </th>
              <th scope="col" className="p-2">
                기준 대변 (base credit)
              </th>
            </tr>
          </thead>
          <tbody>
            {tb.accounts.map((a, i) => (
              <tr
                key={`${a.ledgerAccountCode}-${i}`}
                data-testid={`ledger-tb-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2" data-testid={`ledger-tb-code-${i}`}>
                  {onSelectAccount ? (
                    <button
                      type="button"
                      onClick={() => onSelectAccount(a.ledgerAccountCode)}
                      data-testid={`ledger-tb-code-link-${i}`}
                      className="underline text-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                      {a.ledgerAccountCode}
                    </button>
                  ) : (
                    a.ledgerAccountCode
                  )}
                </td>
                <td className="p-2" data-testid={`ledger-tb-debit-${i}`}>
                  {formatMoney(a.debitTotal)}
                </td>
                <td className="p-2" data-testid={`ledger-tb-credit-${i}`}>
                  {formatMoney(a.creditTotal)}
                </td>
                <td className="p-2" data-testid={`ledger-tb-base-debit-${i}`}>
                  {formatMoney(a.baseDebitTotal)}
                </td>
                <td
                  className="p-2"
                  data-testid={`ledger-tb-base-credit-${i}`}
                >
                  {formatMoney(a.baseCreditTotal)}
                </td>
              </tr>
            ))}
            <tr className="border-t-2 border-border font-medium">
              <td className="p-2">합계 (grand total)</td>
              <td className="p-2" data-testid="ledger-tb-grand-debit">
                {formatMoney(tb.grandDebitTotal)}
              </td>
              <td className="p-2" data-testid="ledger-tb-grand-credit">
                {formatMoney(tb.grandCreditTotal)}
              </td>
              <td className="p-2" data-testid="ledger-tb-grand-base-debit">
                {formatMoney(tb.grandBaseDebitTotal)}
              </td>
              <td className="p-2" data-testid="ledger-tb-grand-base-credit">
                {formatMoney(tb.grandBaseCreditTotal)}
              </td>
            </tr>
          </tbody>
        </table>
      )}
    </section>
  );
}
