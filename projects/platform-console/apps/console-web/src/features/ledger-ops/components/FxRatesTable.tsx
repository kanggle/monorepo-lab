'use client';

import type { FxRatesResponse } from '../api/types';

/**
 * FX 환율 피드 대시보드 테이블 (TASK-PC-FE-092 — § 2.4.7.1).
 *
 * Displays the cached FX rate entries from the ledger feed
 * (`GET /api/finance/ledger/fx-rates` / FIN-BE-033) — currency pair,
 * exact rate string, source, timestamps, age, and staleness.
 *
 * F5 rate invariant (CONTRACT obligation — § 2.4.7.1):
 *   - `rate` is a precision-exact **decimal string** from the producer
 *     (e.g. "1300.12345678"); it is rendered verbatim — NEVER coerced
 *     via `Number()` / `parseFloat()` / `parseInt()`;
 *   - `ageSeconds` IS a plain integer count (duration, not money — the
 *     F5 invariant is amount/rate-only); arithmetic on it is allowed.
 *
 * Empty cache (`rates: []`) → a 200 empty-state message (NOT a 404 /
 * error — the producer returns 200 for an empty feed cache).
 *
 * `feedEnabled=false` → top-level badge warning that the FX feed is
 * disabled and the ledger's FX_RATE_UNAVAILABLE fallback is inactive.
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */

export interface FxRatesTableProps {
  data: FxRatesResponse | null;
  onRefresh?: () => void;
  /**
   * TASK-MONO-300 — when true, the Refresh button is disabled and shows a
   * loading indicator while the POST mutation is in-flight. Prevents
   * double-submit and avoids spamming the external FX provider. Defaults to
   * false (the read-only FE-092 baseline behaviour; tests pin no-regression).
   */
  refreshing?: boolean;
  /**
   * TASK-PC-FE-104 — when provided, each row's currency-pair cell becomes a
   * button that drills into that pair's FX rate history (passing the foreign
   * currency upward). Omitted → the pair renders as plain text (the FE-092
   * feed-only behaviour; a test pins the no-regression).
   */
  onSelectPair?: (foreignCurrency: string) => void;
}

/**
 * Converts `ageSeconds` to a human-friendly Korean string.
 *   < 0 or < 60 s  → "방금"
 *   60 s–3599 s    → "N분 전"
 *   3600 s–86399 s → "N시간 전"
 *   ≥ 86400 s      → "N일 전"
 * Negative values (clock skew) are treated as "방금" (clamped).
 */
function humanizeAge(ageSeconds: number): string {
  if (ageSeconds < 60) return '방금';
  if (ageSeconds < 3600) return `${Math.floor(ageSeconds / 60)}분 전`;
  if (ageSeconds < 86400) return `${Math.floor(ageSeconds / 3600)}시간 전`;
  return `${Math.floor(ageSeconds / 86400)}일 전`;
}

export function FxRatesTable({ data, onRefresh, refreshing = false, onSelectPair }: FxRatesTableProps) {
  if (!data) {
    return null;
  }

  const { feedEnabled, rates } = data;

  return (
    <section
      aria-labelledby="ledger-fx-rates-heading"
      className="mb-8"
    >
      <h2
        id="ledger-fx-rates-heading"
        className="mb-4 text-lg font-medium text-foreground"
      >
        FX 환율 피드
      </h2>

      {/* feedEnabled badge */}
      <div className="mb-4 flex items-center gap-3">
        {feedEnabled ? (
          <span
            data-testid="ledger-fx-rates-feed-badge"
            className="inline-flex items-center rounded-full bg-green-100 px-3 py-1 text-sm font-medium text-green-800"
          >
            피드 활성
          </span>
        ) : (
          <span
            data-testid="ledger-fx-rates-feed-badge"
            className="inline-flex items-center rounded-full bg-yellow-100 px-3 py-1 text-sm font-medium text-yellow-800"
          >
            피드 비활성 — 환율 폴백이 꺼져 있습니다
          </span>
        )}

        <button
          type="button"
          data-testid="ledger-fx-rates-refresh"
          onClick={onRefresh}
          disabled={refreshing}
          aria-busy={refreshing}
          className="rounded-md border border-border px-3 py-1 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          {refreshing ? '새로고침 중…' : '새로고침'}
        </button>
      </div>

      {/* Empty state */}
      {rates.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-fx-rates-empty"
        >
          적재된 환율 quote 가 없습니다.
        </p>
      ) : (
        <table
          className="w-full data-table text-sm"
          data-testid="ledger-fx-rates-table"
          aria-label="FX 환율 피드 목록"
        >
          <caption className="sr-only">FX 환율 피드 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                통화쌍
              </th>
              <th scope="col" className="p-2">
                환율
              </th>
              <th scope="col" className="p-2">
                출처
              </th>
              <th scope="col" className="p-2">
                as-of
              </th>
              <th scope="col" className="p-2">
                fetched
              </th>
              <th scope="col" className="p-2">
                나이
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
            </tr>
          </thead>
          <tbody>
            {rates.map((rate, i) => (
              <tr
                key={`${rate.baseCurrency}-${rate.foreignCurrency}-${i}`}
                data-testid={`ledger-fx-rates-row-${i}`}
                className={
                  rate.stale
                    ? 'border-b border-border bg-yellow-50'
                    : 'border-b border-border'
                }
              >
                <td className="p-2 font-medium text-foreground">
                  {onSelectPair ? (
                    <button
                      type="button"
                      data-testid={`ledger-fx-rates-pair-${i}`}
                      onClick={() => onSelectPair(rate.foreignCurrency)}
                      className="text-primary underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                    >
                      {rate.baseCurrency}/{rate.foreignCurrency}
                    </button>
                  ) : (
                    <>
                      {rate.baseCurrency}/{rate.foreignCurrency}
                    </>
                  )}
                </td>
                <td className="p-2 font-mono text-foreground">
                  {/* F5: rate is rendered as-is (string). NEVER Number()/parseFloat(). */}
                  {rate.rate}
                </td>
                <td className="p-2 text-muted-foreground">{rate.source}</td>
                <td className="p-2 text-muted-foreground">{rate.asOf}</td>
                <td className="p-2 text-muted-foreground">{rate.fetchedAt}</td>
                <td className="p-2 text-muted-foreground">
                  {humanizeAge(rate.ageSeconds)}
                </td>
                <td className="p-2">
                  {rate.stale ? (
                    <span className="inline-flex items-center rounded-full bg-yellow-100 px-2 py-0.5 text-xs font-semibold text-yellow-800">
                      STALE
                    </span>
                  ) : (
                    <span className="inline-flex items-center rounded-full bg-green-100 px-2 py-0.5 text-xs font-semibold text-green-800">
                      최신
                    </span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
