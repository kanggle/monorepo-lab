'use client';

import { formatMoney } from '../api/types';
import type { AccountBalance, AccountEntriesResponse } from '../api/types';

/**
 * Account detail view (TASK-PC-FE-074 — § 2.4.7.1).
 *
 * Displays the running balance for a ledger account (§ 3
 * `GET /accounts/{code}/balance`) plus a paginated list of ledger entries
 * posted to that account (§ 2 `GET /accounts/{code}/entries`).
 *
 * F5 money rendering (CONTRACT obligation, NOT a UX nicety):
 *   - every money field is rendered via `formatMoney(...)` ONLY (pure
 *     string manipulation + per-currency scale lookup);
 *   - NO `Number()` / `parseFloat()` / `parseInt()` is applied to `amount`
 *     anywhere (a test grep-asserts this against the on-disk source).
 *
 * TOLERANCE: `type` / `normalSide` / `balanceSide` / `direction` are free
 * strings — unknown / future values render as-is with a generic prefix,
 * never throw.
 *
 * `onSelectEntry` wires each `entryId` cell back to the Journal Entry tab
 * so the operator can drill from an account entry into its full journal
 * entry detail.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface AccountDetailProps {
  balance: AccountBalance | null;
  entries: AccountEntriesResponse | null;
  /** Called when the operator clicks an entry row's entryId — re-uses the
   *  Journal Entry tab drill. */
  onSelectEntry?: (entryId: string) => void;
}

export function AccountDetail({
  balance,
  entries,
  onSelectEntry,
}: AccountDetailProps) {
  return (
    <section
      aria-labelledby="ledger-account-detail-heading"
      data-testid="ledger-account-detail"
      className="mb-8"
    >
      <h2
        id="ledger-account-detail-heading"
        className="mb-4 text-lg font-medium text-foreground"
      >
        계정 원장 상세 (account ledger)
      </h2>

      {/* Balance card */}
      {balance ? (
        <div
          data-testid="ledger-account-balance"
          className="mb-6 rounded-md border border-border bg-muted p-4"
          aria-label="계정 잔액 (account balance)"
        >
          <h3 className="mb-3 text-sm font-medium text-foreground">
            잔액 (balance) — {balance.ledgerAccountCode}
          </h3>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-muted-foreground">계정 유형 (type)</dt>
              <dd className="font-medium text-foreground">{balance.type}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">정상 변 (normalSide)</dt>
              <dd className="font-medium text-foreground">{balance.normalSide}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">잔액 변 (balanceSide)</dt>
              <dd className="font-medium text-foreground">{balance.balanceSide}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">차변 합계 (debit total)</dt>
              <dd className="font-medium text-foreground">
                {formatMoney(balance.debitTotal)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">대변 합계 (credit total)</dt>
              <dd className="font-medium text-foreground">
                {formatMoney(balance.creditTotal)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">순잔액 (balance)</dt>
              <dd className="font-medium text-foreground">
                {formatMoney(balance.balance)}
              </dd>
            </div>
          </dl>
        </div>
      ) : (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="ledger-account-balance-none"
        >
          계정 잔액을 불러올 수 없습니다.
        </p>
      )}

      {/* Entries table */}
      {entries ? (
        entries.data.length === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-account-entries-empty"
          >
            이 계정에 기표된 분개가 없습니다.
          </p>
        ) : (
          <table
            className="w-full data-table text-sm"
            data-testid="ledger-account-entries-table"
            aria-label="계정 분개 내역"
          >
            <caption className="sr-only">계정 분개 내역</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  기표 일시 (postedAt)
                </th>
                <th scope="col" className="p-2">
                  변 (direction)
                </th>
                <th scope="col" className="p-2">
                  금액 (money)
                </th>
                <th scope="col" className="p-2">
                  분개 ID (entryId)
                </th>
              </tr>
            </thead>
            <tbody>
              {entries.data.map((line, i) => (
                <tr
                  key={`${line.entryId}-${i}`}
                  data-testid={`ledger-account-entry-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 text-muted-foreground">
                    {line.postedAt ?? '—'}
                  </td>
                  <td className="p-2">{line.direction}</td>
                  <td className="p-2">{formatMoney(line.money)}</td>
                  <td className="p-2">
                    {onSelectEntry ? (
                      <button
                        type="button"
                        onClick={() => onSelectEntry(line.entryId)}
                        className="underline text-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        data-testid={`ledger-account-entry-id-${i}`}
                      >
                        {line.entryId}
                      </button>
                    ) : (
                      <span data-testid={`ledger-account-entry-id-${i}`}>
                        {line.entryId}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )
      ) : (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-account-entries-none"
        >
          계정 분개 내역을 불러올 수 없습니다.
        </p>
      )}
    </section>
  );
}
