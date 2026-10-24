import { barPercent, type ThroughputDay } from '../lib/throughput';

/**
 * Daily inbound vs outbound **count** column chart for the `/wms` 개요
 * throughput card (TASK-PC-FE-358). Pure CSS/flex — the project has no chart
 * library; same principles as `ecommerce-ops/components/RankingBarChart.tsx`:
 *   - bars are decorative (`aria-hidden`); every number is ALSO text — one
 *     screen-reader sentence per day, plus the visible figures under the bars;
 *   - each bar is a % of the WINDOW max (inbound and outbound share one scale,
 *     so a taller bar is a larger count), a 2% sliver for any non-zero value,
 *     0 when the max is 0 (divide-by-zero guard — the card renders the empty
 *     notice before reaching here, but the chart is safe on its own).
 *
 * Self-contained under `features/wms-ops/` on purpose (TASK-PC-FE-357 is
 * adding a shared bar primitive concurrently; this chart does not depend on
 * it). Dates are the producer's **KST** day buckets (TASK-MONO-808) — rendered
 * straight from the ISO string, never through a timezone conversion (a
 * conversion would shift every label by up to a day).
 */

/** `2026-10-22` → `10/22` (no Date, no timezone). */
export function shortDateLabel(iso: string): string {
  const [, m, d] = iso.split('-');
  return m && d ? `${Number(m)}/${Number(d)}` : iso;
}

/** `2026-10-22` → `10월 22일`. */
export function longDateLabel(iso: string): string {
  const [, m, d] = iso.split('-');
  return m && d ? `${Number(m)}월 ${Number(d)}일` : iso;
}

const INBOUND_BAR = 'bg-primary/70';
const OUTBOUND_BAR = 'bg-amber-500/80';

export function WmsThroughputChart({
  days,
  testid = 'wms-throughput-chart',
}: {
  days: readonly ThroughputDay[];
  testid?: string;
}) {
  const max = days.reduce(
    (m, d) => Math.max(m, d.putawayCount, d.shipmentCount),
    0,
  );

  return (
    <div data-testid={testid}>
      <ul className="mb-3 flex flex-wrap gap-4 text-xs text-muted-foreground">
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className={`inline-block h-2.5 w-2.5 rounded-sm ${INBOUND_BAR}`} />
          입고(적치 완료) 건수
        </li>
        <li className="flex items-center gap-1.5">
          <span aria-hidden="true" className={`inline-block h-2.5 w-2.5 rounded-sm ${OUTBOUND_BAR}`} />
          출고(출하 확정) 건수
        </li>
      </ul>
      <ol
        aria-label="일별 입고 · 출고 건수"
        className="flex items-stretch gap-1 overflow-x-auto"
      >
        {days.map((d) => (
          <li
            key={d.date}
            data-testid={`${testid}-day-${d.date}`}
            className="flex min-w-[2rem] flex-1 flex-col items-center"
          >
            <span className="sr-only">
              {`${longDateLabel(d.date)}: 입고 ${d.putawayCount.toLocaleString()}건, 출고 ${d.shipmentCount.toLocaleString()}건`}
            </span>
            <div
              aria-hidden="true"
              className="flex h-28 w-full items-end justify-center gap-0.5 border-b border-border"
            >
              <div
                data-testid={`${testid}-bar-in-${d.date}`}
                className={`w-2.5 rounded-t ${INBOUND_BAR}`}
                style={{ height: `${barPercent(d.putawayCount, max)}%` }}
              />
              <div
                data-testid={`${testid}-bar-out-${d.date}`}
                className={`w-2.5 rounded-t ${OUTBOUND_BAR}`}
                style={{ height: `${barPercent(d.shipmentCount, max)}%` }}
              />
            </div>
            <div
              aria-hidden="true"
              className="mt-1 flex gap-1 text-[10px] tabular-nums text-foreground"
            >
              <span>{d.putawayCount.toLocaleString()}</span>
              <span className="text-muted-foreground">{d.shipmentCount.toLocaleString()}</span>
            </div>
            <span aria-hidden="true" className="text-[10px] text-muted-foreground">
              {shortDateLabel(d.date)}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
