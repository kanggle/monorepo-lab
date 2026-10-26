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
import type {
  PerformanceCell,
  PerformanceMetric,
  PerformanceMetrics,
} from '@/shared/api/performance-metrics';

/**
 * Optional per-card drill-in (TASK-MONO-810): the list a card's number came
 * from. Called with the metric and the whole response (so a link can carry the
 * response's own `current` window). Return `null`/`undefined` for a card with
 * no list behind it. Absent ⇒ no card is a link (every strip before 810).
 */
export type PerformanceCardHref = (
  metric: PerformanceMetric,
  data: PerformanceMetrics,
) => string | null | undefined;

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
  cardHref,
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
  /** Optional per-card link to the list behind the number (see {@link PerformanceCardHref}). */
  cardHref?: PerformanceCardHref;
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
        <StripBody
          cell={cell}
          labels={labels}
          chartKey={chartKey}
          cardHref={cardHref}
          testid={testid}
        />
      </Card>
    </section>
  );
}

function StripBody({
  cell,
  labels,
  chartKey,
  cardHref,
  testid,
}: {
  cell: PerformanceCell;
  labels: Readonly<Record<string, string>>;
  chartKey?: string;
  cardHref?: PerformanceCardHref;
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
          <KpiCard
            key={m.key}
            metric={m}
            labels={labels}
            href={cardHref?.(m, data) ?? null}
            testid={testid}
          />
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
  href,
  testid,
}: {
  metric: PerformanceMetric;
  labels: Readonly<Record<string, string>>;
  href: string | null;
  testid: string;
}) {
  const body = <KpiBody metric={metric} labels={labels} testid={testid} />;
  return (
    <li
      data-testid={`${testid}-kpi-${metric.key}`}
      className={cn('rounded-md border border-border', href ? '' : 'px-4 py-3')}
    >
      {href ? (
        // The whole card is the link; its accessible name is the card's own
        // text (label · value · change) plus where it goes.
        <Link
          href={href}
          data-testid={`${testid}-kpi-${metric.key}-link`}
          className="block rounded-md px-4 py-3 hover:bg-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {body}
          <span className="sr-only"> — 목록에서 보기</span>
        </Link>
      ) : (
        body
      )}
    </li>
  );
}

function KpiBody({
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
    <>
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
    </>
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
        {bars.map((b, i) => (
          <li
            key={b.date}
            data-testid={`${testid}-day-${b.date}`}
            className="group flex min-w-[1.75rem] flex-1 flex-col items-center"
          >
            <span className="sr-only">{`${shortDate(b.date)}: ${formatMetricValue(b.value, metric)}`}</span>
            <div aria-hidden="true" className="relative flex h-24 w-full items-end justify-center border-b border-border">
              {/* Hover readout (TASK-PC-FE-375): the whole day column is the
                  target, so a 0 or 2%-sliver day can be read too. It sits
                  inside the bar area because the list's overflow-x-auto would
                  clip anything above it, and opens toward the chart's middle
                  so the first and last columns are not cut off at the edge.
                  Hidden from assistive tech — the sr-only line says the same. */}
              <span
                aria-hidden="true"
                data-testid={`${testid}-tip-${b.date}`}
                className={cn(
                  'pointer-events-none invisible absolute top-0 z-10 whitespace-nowrap rounded border border-border bg-background px-2 py-1 text-xs tabular-nums text-foreground shadow-sm group-hover:visible',
                  i < bars.length / 2 ? 'left-0' : 'right-0',
                )}
              >
                {`${shortDate(b.date)} ${label} ${formatMetricValue(b.value, metric)}`}
              </span>
              <div
                data-testid={`${testid}-bar-${b.date}`}
                className="w-3 rounded-t bg-primary/70 group-hover:bg-primary"
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
