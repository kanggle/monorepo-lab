import {
  formatMoney,
  KNOWN_SOURCE_TYPES,
  KNOWN_DIRECTIONS,
  type JournalEntry,
  type JournalLine,
} from '../api/types';
import { labelForUnknown } from '@/shared/lib/tolerant-label';

/**
 * Journal entry detail (TASK-PC-FE-072 — § 2.4.7.1 / `ledger-api.md` § 1).
 *
 * Renders the entry header (sourceType surfaced honestly — TRANSACTION /
 * MANUAL / REVALUATION / SETTLEMENT shown as-is; unknown / future
 * sourceType → generic label; the `balanced` double-entry invariant shown
 * honestly) and the lines table — the F5 multi-currency triple per line:
 *   - `money` (transaction currency),
 *   - `exchangeRate` (an exact-decimal **string**, rendered VERBATIM — no
 *     arithmetic, never floated),
 *   - `baseAmount` (the balance-authoritative KRW value).
 *
 * F5 money rendering (CONTRACT obligation): `money` + `baseAmount` go
 * through `formatMoney(...)` ONLY; `exchangeRate` is rendered as the raw
 * string. NO `Number(...)` / `parseFloat(...)` / `parseInt(...)` is applied
 * to any `amount` / `exchangeRate` anywhere (a test grep-asserts this).
 *
 * A 9th-increment revaluation line (`money.amount === '0'` foreign with a
 * non-zero KRW `baseAmount`) is highlighted so the operator can see the
 * unrealised-FX provenance honestly.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface JournalEntryDetailProps {
  entry: JournalEntry;
}

/** A revaluation provenance line has a zero transaction-currency amount
 *  (the foreign leg is 0) with a non-zero base KRW amount. Pure string
 *  comparison — NO Number coercion of the amount. */
function isRevaluationLine(line: JournalLine): boolean {
  return line.money.amount === '0' && line.baseAmount.amount !== '0';
}

export function JournalEntryDetail({ entry }: JournalEntryDetailProps) {
  return (
    <section
      aria-labelledby="ledger-entry-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="ledger-entry-detail"
    >
      <div className="mb-3 flex items-center gap-3">
        <h2
          id="ledger-entry-detail-heading"
          className="text-base font-medium text-foreground"
        >
          분개 상세 — {entry.entryId}
        </h2>
        <span
          data-testid="ledger-entry-source"
          className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground"
        >
          {labelForUnknown(entry.source.sourceType, KNOWN_SOURCE_TYPES)}
        </span>
        <span
          data-testid="ledger-entry-balanced"
          className={
            entry.balanced
              ? 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
              : 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive'
          }
        >
          {entry.balanced ? '대차 일치 (balanced)' : '대차 불일치 (unbalanced)'}
        </span>
      </div>

      <dl className="mb-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">기표 시각 (UTC)</dt>
          <dd className="text-foreground">{entry.postedAt ?? '—'}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">반전 대상 (reversal of)</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-entry-reversal-of"
          >
            {entry.reversalOfEntryId ?? '—'}
          </dd>
        </div>
      </dl>

      <table className="data-table" data-testid="ledger-entry-lines-table">
        <caption className="sr-only">분개 라인</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              계정 코드
            </th>
            <th scope="col" className="p-2">
              방향
            </th>
            <th scope="col" className="p-2">
              금액 (거래 통화)
            </th>
            <th scope="col" className="p-2">
              환율 (exchange rate)
            </th>
            <th scope="col" className="p-2">
              기준 금액 (base KRW)
            </th>
          </tr>
        </thead>
        <tbody>
          {entry.lines.map((line, i) => {
            const reval = isRevaluationLine(line);
            return (
              <tr
                key={`${line.ledgerAccountCode}-${i}`}
                data-testid={`ledger-entry-line-${i}`}
                aria-label={reval ? '환산 (revaluation) 라인' : undefined}
                className={
                  reval
                    ? 'border-b border-border bg-amber-50 dark:bg-amber-950/30'
                    : 'border-b border-border'
                }
              >
                <td className="p-2" data-testid={`ledger-line-code-${i}`}>
                  {line.ledgerAccountCode}
                  {reval ? (
                    <span
                      data-testid={`ledger-line-reval-${i}`}
                      className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/60 dark:text-amber-100"
                    >
                      환산 (revaluation)
                    </span>
                  ) : null}
                </td>
                <td
                  className="p-2"
                  data-testid={`ledger-line-direction-${i}`}
                >
                  {labelForUnknown(line.direction, KNOWN_DIRECTIONS)}
                </td>
                <td className="p-2" data-testid={`ledger-line-money-${i}`}>
                  {formatMoney(line.money)}
                </td>
                <td className="p-2" data-testid={`ledger-line-rate-${i}`}>
                  {line.exchangeRate}
                </td>
                <td className="p-2" data-testid={`ledger-line-base-${i}`}>
                  {formatMoney(line.baseAmount)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}
