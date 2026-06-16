'use client';

import type { FxRateHistoryResponse } from '../api/types';

/**
 * FX 환율 history 드릴 테이블 (TASK-PC-FE-104 — § 2.4.7.1 / ledger-api.md § 14.1).
 *
 * Displays the per-pair time series of fetched FX quotes from the
 * `fx_rate_quote_history` append-only audit trail
 * (`GET /api/finance/ledger/fx-rates/{foreignCurrency}/history` / FIN-BE-040) —
 * the exact decimal rate string, the provider-stated `asOf` instant, the
 * `fetchedAt` pull instant, and the `source` provenance. Rows are ordered
 * newest-first (`fetched_at DESC`, ties broken by surrogate id DESC) by the
 * producer — rendered verbatim, no client re-sort.
 *
 * F5 rate invariant (CONTRACT obligation — § 2.4.7.1, same as the FX feed
 * table): `rate` is a precision-exact **decimal string** from the producer
 * (e.g. "13.60000000"); it is rendered verbatim — NEVER coerced via
 * `Number()` / `parseFloat()` / `parseInt()`. The history rows carry NO
 * staleness fields (history is raw provenance, not a live-cache freshness
 * check — that distinction lives on the FX feed table, FE-092).
 *
 * Empty history (`quotes: []`) → a 200 empty-state message (NOT a 404 /
 * error — an unknown / never-polled foreign code returns 200 with an empty
 * series, mirroring the feed cache's empty-200 stance).
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */

export interface FxRateHistoryTableProps {
  data: FxRateHistoryResponse | null;
  onRefresh?: () => void;
}

export function FxRateHistoryTable({ data, onRefresh }: FxRateHistoryTableProps) {
  if (!data) {
    return null;
  }

  const { base, foreign, quotes } = data;

  return (
    <section aria-labelledby="ledger-fx-history-heading" className="mt-8">
      <div className="mb-4 flex items-center gap-3">
        <h3
          id="ledger-fx-history-heading"
          data-testid="ledger-fx-history-heading"
          className="text-base font-medium text-foreground"
        >
          {base}/{foreign} 환율 이력
        </h3>
        <button
          type="button"
          data-testid="ledger-fx-history-refresh"
          onClick={onRefresh}
          className="rounded-md border border-border px-3 py-1 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          새로고침
        </button>
      </div>

      {quotes.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-fx-history-empty"
        >
          이 통화쌍의 환율 이력이 없습니다.
        </p>
      ) : (
        <table
          className="w-full data-table text-sm"
          data-testid="ledger-fx-history-table"
          aria-label={`${base}/${foreign} 환율 이력`}
        >
          <caption className="sr-only">{base}/{foreign} 환율 이력</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                환율
              </th>
              <th scope="col" className="p-2">
                as-of
              </th>
              <th scope="col" className="p-2">
                fetched
              </th>
              <th scope="col" className="p-2">
                출처
              </th>
            </tr>
          </thead>
          <tbody>
            {quotes.map((q, i) => (
              <tr
                key={`${q.fetchedAt}-${i}`}
                data-testid={`ledger-fx-history-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2 font-mono text-foreground">
                  {/* F5: rate is rendered as-is (string). NEVER Number()/parseFloat(). */}
                  {q.rate}
                </td>
                <td className="p-2 text-muted-foreground">{q.asOf}</td>
                <td className="p-2 text-muted-foreground">{q.fetchedAt}</td>
                <td className="p-2 text-muted-foreground">{q.source}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
