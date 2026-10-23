import Link from 'next/link';
import { Card } from '@/shared/ui/Card';
import { cn } from '@/shared/lib/cn';
import { METRICS_PERIODS, type MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PERIOD_LABEL,
  describeDelta,
  formatDelta,
  formatMetricValue,
  formatWireMoment,
  metricDelta,
  metricLabel,
  seriesBars,
  shortDate,
} from '@/shared/lib/performance-format';
import type { PerformanceCell, PerformanceMetric } from '@/shared/api/performance-metrics';

/**
 * The «실적» strip at the top of a domain overview (ADR-MONO-084 D1,
 * TASK-MONO-806): a 이번 주 / 이번 달 switch, one KPI card per metric (value ·
 * change vs the same-length previous window), and one daily trend chart.
 *
 * Server component — no client state. The period switch is two links the page
 * builds (`periodHrefs`), so a choice survives reload and the page's other
 * query parameters stay the page's business. Pure CSS bars, like the console's
 * other charts (no chart library); every number is also text.
 *
 * Failure is cell-local: a failed or forbidden strip says so in words — never
 * a 0, which would read as «nothing sold» — and the overview below it renders
 * as before.
 */
export function PerformanceStrip({
  cell,
  selected,
  periodHrefs,
  labels,
  chartKey,
  testid = 'performance-strip',
}: {
  cell: PerformanceCell;
  /** The period the page asked for — drives the switch even when the read failed. */
  selected: MetricsPeriod;
  periodHrefs: Readonly<Record<MetricsPeriod, string>>;
  /** metric key → screen name (a per-currency key falls back to its base key). */
  labels: Readonly<Record<string, string>>;
  /** Which metric the trend chart draws; defaults to the first one. */
  chartKey?: string;
  testid?: string;
}) {
  const headingId = `${testid}-heading`;

  return (
    <section aria-labelledby={headingId} data-testid={testid} className="mb-6">
      <Card>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <h2 id={headingId} className="text-lg font-semibold">
            실적
          </h2>
          <nav aria-label="실적 기간" className="flex gap-1 rounded-md bg-muted p-1 text-sm">
            {METRICS_PERIODS.map((p) => (
              <Link
                key={p}
                href={periodHrefs[p]}
                aria-current={p === selected ? 'page' : undefined}
                data-testid={`${testid}-period-${p}`}
                className={cn(
                  'rounded px-3 py-1 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
                  p === selected
                    ? 'bg-background font-medium text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {PERIOD_LABEL[p]}
              </Link>
            ))}
          </nav>
        </div>
        <StripBody cell={cell} labels={labels} chartKey={chartKey} testid={testid} />
      </Card>
    </section>
  );
}

function StripBody({
  cell,
  labels,
  chartKey,
  testid,
}: {
  cell: PerformanceCell;
  labels: Readonly<Record<string, string>>;
  chartKey?: string;
  testid: string;
}) {
  if (cell.status !== 'ok') {
    return (
      <p role="status" data-testid={`${testid}-${cell.status}`} className="text-sm text-muted-foreground">
        {cell.status === 'forbidden'
          ? '실적을 볼 권한이 없습니다. 아래 현재 상태는 그대로 볼 수 있습니다.'
          : '실적을 불러올 수 없습니다. 아래 현재 상태는 그대로 볼 수 있습니다. 잠시 후 다시 시도하세요.'}
      </p>
    );
  }

  const data = cell.value;
  if (data.metrics.length === 0) {
    return (
      <p role="status" data-testid={`${testid}-empty`} className="text-sm text-muted-foreground">
        표시할 실적이 없습니다.
      </p>
    );
  }

  const chartMetric = data.metrics.find((m) => m.key === chartKey) ?? data.metrics[0];

  return (
    <>
      <p data-testid={`${testid}-window`} className="mb-4 text-xs text-muted-foreground">
        {PERIOD_LABEL[data.period]} · {formatWireMoment(data.current.from)} – {formatWireMoment(data.current.to)} ·
        한국 시간 기준 · 직전 같은 길이 구간({formatWireMoment(data.previous.from)} –{' '}
        {formatWireMoment(data.previous.to)})과 비교
      </p>
      <ul className="mb-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {data.metrics.map((m) => (
          <KpiCard key={m.key} metric={m} labels={labels} testid={testid} />
        ))}
      </ul>
      <TrendChart
        metric={chartMetric}
        series={data.series}
        label={metricLabel(chartMetric, labels)}
        testid={`${testid}-trend`}
      />
    </>
  );
}

function KpiCard({
  metric,
  labels,
  testid,
}: {
  metric: PerformanceMetric;
  labels: Readonly<Record<string, string>>;
  testid: string;
}) {
  const delta = metricDelta(metric.current, metric.previous);
  return (
    <li
      data-testid={`${testid}-kpi-${metric.key}`}
      className="rounded-md border border-border px-4 py-3"
    >
      <p className="text-xs text-muted-foreground">{metricLabel(metric, labels)}</p>
      <p data-testid={`${testid}-kpi-${metric.key}-value`} className="mt-1 text-xl font-semibold tabular-nums">
        {formatMetricValue(metric.current, metric)}
      </p>
      <p className="mt-1 text-xs tabular-nums text-muted-foreground">
        <span aria-hidden="true" data-testid={`${testid}-kpi-${metric.key}-delta`}>
          {formatDelta(delta)}
        </span>
        <span className="sr-only">{describeDelta(delta)}</span>
        <span className="ml-2">직전 {formatMetricValue(metric.previous, metric)}</span>
      </p>
    </li>
  );
}

function TrendChart({
  metric,
  series,
  label,
  testid,
}: {
  metric: PerformanceMetric;
  series: Parameters<typeof seriesBars>[0];
  label: string;
  testid: string;
}) {
  const bars = seriesBars(series, metric.key);
  return (
    <div data-testid={testid}>
      <p className="mb-2 text-sm font-medium">{label} · 일별</p>
      <ol aria-label={`${label} 일별 추이`} className="flex items-stretch gap-1 overflow-x-auto">
        {bars.map((b) => (
          <li
            key={b.date}
            data-testid={`${testid}-day-${b.date}`}
            className="flex min-w-[1.75rem] flex-1 flex-col items-center"
          >
            <span className="sr-only">{`${shortDate(b.date)}: ${formatMetricValue(b.value, metric)}`}</span>
            <div aria-hidden="true" className="flex h-24 w-full items-end justify-center border-b border-border">
              <div
                data-testid={`${testid}-bar-${b.date}`}
                className="w-3 rounded-t bg-primary/70"
                style={{ height: `${b.percent}%` }}
              />
            </div>
            <span aria-hidden="true" className="mt-1 text-[10px] text-muted-foreground">
              {shortDate(b.date)}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
