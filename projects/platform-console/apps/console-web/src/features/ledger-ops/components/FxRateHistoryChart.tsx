import type { FxRateHistoryQuote } from '../api/types';
import {
  FX_LINE_HEIGHT,
  FX_LINE_WIDTH,
  fxHistoryLine,
} from '../lib/fx-history-line';

/**
 * FX 환율 추이 선 (TASK-PC-FE-359) — drawn ABOVE `FxRateHistoryTable`'s rows.
 * Inline SVG, no chart library. The SVG is decorative (`aria-hidden`); what a
 * person reads is the text beside it — 최저 · 최고 · 최신 as the producer's
 * ORIGINAL rate strings (F5: the float from `chartMagnitude` only places the
 * points), and the table below still lists every quote.
 *
 *  - 1 drawable point → a single dot; all equal → a flat line in the middle;
 *  - an unreadable rate is skipped (and the count is said); none readable →
 *    renders nothing, the table stands alone.
 */
export function FxRateHistoryChart({
  quotes,
}: {
  quotes: readonly FxRateHistoryQuote[];
}) {
  const geo = fxHistoryLine(quotes);
  if (!geo) return null;
  const { points, skipped, min, max, latest } = geo;
  const last = points[points.length - 1];

  return (
    <figure className="mb-4" data-testid="ledger-fx-history-chart">
      <svg
        aria-hidden="true"
        viewBox={`0 0 ${FX_LINE_WIDTH} ${FX_LINE_HEIGHT}`}
        className="h-auto w-full max-w-2xl rounded border border-border bg-muted/30 text-primary"
        data-testid="ledger-fx-history-chart-svg"
        data-points={points.length}
      >
        {points.length > 1 ? (
          <polyline
            data-testid="ledger-fx-history-chart-line"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinejoin="round"
            points={points.map((p) => `${p.x},${p.y}`).join(' ')}
          />
        ) : null}
        <circle
          data-testid="ledger-fx-history-chart-latest-dot"
          cx={last.x}
          cy={last.y}
          r={4}
          fill="currentColor"
        />
      </svg>
      <figcaption className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground">
        <span data-testid="ledger-fx-history-chart-latest">
          최신 <span className="font-mono text-foreground">{latest.rate}</span>
        </span>
        {min ? (
          <span data-testid="ledger-fx-history-chart-min">
            최저 <span className="font-mono text-foreground">{min.rate}</span>
          </span>
        ) : null}
        {max ? (
          <span data-testid="ledger-fx-history-chart-max">
            최고 <span className="font-mono text-foreground">{max.rate}</span>
          </span>
        ) : null}
        <span data-testid="ledger-fx-history-chart-scope">
          {`받아 온 최근 ${quotes.length}건 · 왼쪽이 오래된 값, 오른쪽이 최신 값`}
        </span>
        {skipped > 0 ? (
          <span data-testid="ledger-fx-history-chart-skipped">
            {`숫자로 읽을 수 없는 ${skipped}건은 그래프에서 뺐습니다 (아래 표에는 있습니다).`}
          </span>
        ) : null}
      </figcaption>
    </figure>
  );
}
