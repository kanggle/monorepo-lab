'use client';

import { formatMoney, discrepancyMoney } from '../api/types';
import type { Statement, Discrepancy } from '../api/types';

/**
 * Statement detail view (TASK-PC-FE-075 — § 2.4.7.1).
 *
 * Displays the reconciliation statement detail for a single statement
 * (§ 3 `GET /api/finance/ledger/reconciliation/statements/{id}`):
 *   - a header card (ledgerAccountCode, source, statementDate, matchedCount,
 *     discrepancyCount);
 *   - a **matches** table (statementLineExternalRef, money via formatMoney,
 *     journalEntryId as a button → onSelectEntry to drill into the Journal
 *     Entry tab);
 *   - a **discrepancies** table (discrepancyId as a button →
 *     onSelectDiscrepancy to drill into the existing DiscrepancyDetail,
 *     type, status, expected/actual amounts via discrepancyMoney +
 *     formatMoney).
 *
 * F5 money rendering (CONTRACT obligation, NOT a UX nicety):
 *   - every money field is rendered via `formatMoney(...)` ONLY (pure
 *     string manipulation + per-currency scale lookup);
 *   - NO `Number()` / `parseFloat()` / `parseInt()` is applied to `amount`
 *     anywhere (a test grep-asserts this against the on-disk source).
 *
 * TOLERANCE: `source`, `type`, `status` are free strings — unknown / future
 * values render as-is, never throw.
 *
 * Empty matches → "매칭 없음"; empty discrepancies → "차이 없음"
 * (not errors — a fully reconciled statement has 0 discrepancies).
 *
 * STRICTLY READ-ONLY — no mutation affordance (the FE-073 resolve lives in
 * DiscrepancyDetail, not here).
 */
export interface StatementDetailProps {
  statement: Statement | null;
  /** Called when the operator clicks a match-row journalEntryId button —
   *  re-uses the existing Journal Entry tab drill. */
  onSelectEntry: (entryId: string) => void;
  /** Called when the operator clicks a discrepancy-row discrepancyId button
   *  — drives the existing DiscrepancyDetail in the same 대사 tab. */
  onSelectDiscrepancy: (discrepancyId: string) => void;
}

function discrepancyAmounts(d: Discrepancy) {
  const m = discrepancyMoney(d);
  return { expected: formatMoney(m.expected), actual: formatMoney(m.actual) };
}

export function StatementDetail({
  statement,
  onSelectEntry,
  onSelectDiscrepancy,
}: StatementDetailProps) {
  if (!statement) {
    return (
      <section
        aria-labelledby="ledger-statement-detail-heading"
        data-testid="ledger-statement-detail"
        className="mb-8"
      >
        <h2
          id="ledger-statement-detail-heading"
          className="mb-4 text-lg font-medium text-foreground"
        >
          대사 statement 상세
        </h2>
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-statement-detail-loading"
        >
          불러오는 중…
        </p>
      </section>
    );
  }

  return (
    <section
      aria-labelledby="ledger-statement-detail-heading"
      data-testid="ledger-statement-detail"
      className="mb-8"
    >
      <h2
        id="ledger-statement-detail-heading"
        className="mb-4 text-lg font-medium text-foreground"
      >
        대사 statement 상세 — {statement.statementId}
      </h2>

      {/* Header card */}
      <div
        data-testid="ledger-statement-header"
        className="mb-6 rounded-md border border-border bg-muted p-4"
        aria-label="statement 요약 (header)"
      >
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-3">
          <div>
            <dt className="text-muted-foreground">계정 코드 (ledgerAccountCode)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-statement-account-code"
            >
              {statement.ledgerAccountCode}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">소스 (source)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-statement-source"
            >
              {statement.source}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">statement 일자</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-statement-date"
            >
              {statement.statementDate ?? '—'}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">매칭 건수 (matchedCount)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-statement-matched-count"
            >
              {statement.matchedCount}
            </dd>
          </div>
          <div>
            <dt className="text-muted-foreground">차이 건수 (discrepancyCount)</dt>
            <dd
              className="font-medium text-foreground"
              data-testid="ledger-statement-discrepancy-count"
            >
              {statement.discrepancyCount}
            </dd>
          </div>
        </dl>
      </div>

      {/* Matches table */}
      <section aria-labelledby="ledger-statement-matches-heading" className="mb-6">
        <h3
          id="ledger-statement-matches-heading"
          className="mb-3 text-base font-medium text-foreground"
        >
          매칭 내역 (matches)
        </h3>
        {statement.matches.length === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-statement-matches-empty"
          >
            매칭 없음
          </p>
        ) : (
          <table
            className="w-full data-table text-sm"
            data-testid="ledger-statement-matches-table"
            aria-label="statement 매칭 내역"
          >
            <caption className="sr-only">statement 매칭 내역</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  외부 참조 (statementLineExternalRef)
                </th>
                <th scope="col" className="p-2">
                  금액 (money)
                </th>
                <th scope="col" className="p-2">
                  분개 ID (journalEntryId)
                </th>
              </tr>
            </thead>
            <tbody>
              {statement.matches.map((match, i) => (
                <tr
                  key={`${match.journalEntryId}-${i}`}
                  data-testid={`ledger-statement-match-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2 text-muted-foreground">
                    {match.statementLineExternalRef}
                  </td>
                  <td className="p-2">{formatMoney(match.money)}</td>
                  <td className="p-2">
                    <button
                      type="button"
                      onClick={() => onSelectEntry(match.journalEntryId)}
                      className="underline text-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      data-testid={`ledger-statement-match-entry-${i}`}
                    >
                      {match.journalEntryId}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* Discrepancies table */}
      <section aria-labelledby="ledger-statement-discrepancies-heading">
        <h3
          id="ledger-statement-discrepancies-heading"
          className="mb-3 text-base font-medium text-foreground"
        >
          차이 내역 (discrepancies)
        </h3>
        {statement.discrepancies.length === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-statement-discrepancies-empty"
          >
            차이 없음
          </p>
        ) : (
          <table
            className="w-full data-table text-sm"
            data-testid="ledger-statement-discrepancies-table"
            aria-label="statement 차이 내역"
          >
            <caption className="sr-only">statement 차이 내역</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  차이 ID
                </th>
                <th scope="col" className="p-2">
                  유형 (type)
                </th>
                <th scope="col" className="p-2">
                  상태 (status)
                </th>
                <th scope="col" className="p-2">
                  기대값 (expected)
                </th>
                <th scope="col" className="p-2">
                  실제값 (actual)
                </th>
              </tr>
            </thead>
            <tbody>
              {statement.discrepancies.map((d, i) => {
                const amounts = discrepancyAmounts(d);
                return (
                  <tr
                    key={d.discrepancyId}
                    data-testid={`ledger-statement-discrepancy-row-${i}`}
                    className="border-b border-border"
                  >
                    <td className="p-2">
                      <button
                        type="button"
                        onClick={() => onSelectDiscrepancy(d.discrepancyId)}
                        className="underline text-foreground hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        data-testid={`ledger-statement-disc-link-${i}`}
                      >
                        {d.discrepancyId}
                      </button>
                    </td>
                    <td className="p-2">{d.type}</td>
                    <td className="p-2">{d.status}</td>
                    <td className="p-2">{amounts.expected}</td>
                    <td className="p-2">{amounts.actual}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </section>
  );
}
