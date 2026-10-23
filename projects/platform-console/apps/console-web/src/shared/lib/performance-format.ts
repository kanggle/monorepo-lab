import { chartMagnitude } from '@/shared/lib/chart-scale';
import { formatDecimalDisplay, formatMoneyDisplay } from '@/shared/lib/money';
import type { MetricsPeriod } from '@/shared/lib/kst-period';
import type {
  PerformanceMetric,
  PerformanceSeriesPoint,
} from '@/shared/api/performance-metrics';

/**
 * Pure renderers for the shared performance strip (ADR-MONO-084, TASK-MONO-806).
 *
 * ── F5 ── values are integer strings. What a person reads (the value, the
 * change, the percentage) is computed from the string — the change with
 * `BigInt`, which is exact — and never through a JS number. Only bar heights
 * go through `chartMagnitude`, the console's single sanctioned coercion. On the
 * F5 guard's list.
 */

export const PERIOD_LABEL: Readonly<Record<MetricsPeriod, string>> = {
  week: '이번 주',
  month: '이번 달',
};

/** A metric's value as shown: `₩12,340,000` for money, `1,234` for a count. */
export function formatMetricValue(
  value: string,
  metric: Pick<PerformanceMetric, 'unit' | 'currency'>,
): string {
  return metric.unit === 'money' && metric.currency
    ? formatMoneyDisplay({ amount: value, currency: metric.currency })
    : formatDecimalDisplay(value);
}

/**
 * The screen name of a metric. A per-currency key (`amount:KRW`) falls back to
 * its base key's label plus the currency, so a domain names `amount` once.
 */
export function metricLabel(
  metric: Pick<PerformanceMetric, 'key' | 'currency'>,
  labels: Readonly<Record<string, string>>,
): string {
  const exact = labels[metric.key];
  if (exact) return exact;
  const base = metric.key.split(':')[0];
  const baseLabel = labels[base];
  if (baseLabel) return metric.currency ? `${baseLabel} (${metric.currency})` : baseLabel;
  return metric.key;
}

export interface MetricDelta {
  direction: 'up' | 'down' | 'flat';
  /** `"12.3"` — one decimal, absolute, half-up; `null` when the previous value is 0
   *  (a percentage of nothing is not a number, so the screen shows «—»). */
  percent: string | null;
}

/**
 * Current vs previous, exactly. Direction is never colour-coded on screen: a rise
 * is good for revenue and bad for failed logins, and the strip does not know which.
 */
export function metricDelta(current: string, previous: string): MetricDelta {
  const cur = BigInt(current);
  const prev = BigInt(previous);
  const diff = cur - prev;
  const direction = diff > 0n ? 'up' : diff < 0n ? 'down' : 'flat';
  if (prev === 0n) return { direction, percent: null };

  const absDiff = diff < 0n ? -diff : diff;
  const absPrev = prev < 0n ? -prev : prev;
  // tenths of a percent, rounded half-up: (|diff| * 1000 + |prev| / 2) / |prev|
  const tenths = (absDiff * 1000n + absPrev / 2n) / absPrev;
  const whole = tenths / 10n;
  const frac = tenths % 10n;
  return { direction, percent: `${whole.toString()}.${frac.toString()}` };
}

const ARROW: Readonly<Record<MetricDelta['direction'], string>> = {
  up: '▲',
  down: '▼',
  flat: '–',
};

/** Visible change line: `▲ 12.3%`, `▼ 4.0%`, `– 0.0%`, or `—` when undefined. */
export function formatDelta(delta: MetricDelta): string {
  return delta.percent === null ? '—' : `${ARROW[delta.direction]} ${delta.percent}%`;
}

/** Screen-reader sentence for the change. */
export function describeDelta(delta: MetricDelta): string {
  if (delta.percent === null) {
    return delta.direction === 'flat'
      ? '직전 같은 기간과 같습니다'
      : '직전 같은 기간 값이 0 이라 비율을 계산하지 않습니다';
  }
  const word = delta.direction === 'up' ? '증가' : delta.direction === 'down' ? '감소' : '변화 없음';
  return `직전 같은 기간 대비 ${delta.percent}% ${word}`;
}

/** `2026-10-23T14:05:00+09:00` → `10월 23일 14:05` (string slicing — no Date, no zone). */
export function formatWireMoment(wire: string): string {
  const m = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(wire);
  if (!m) return wire;
  const [, mo, d, hh, mm] = m;
  return `${mo.replace(/^0/, '')}월 ${d.replace(/^0/, '')}일 ${hh}:${mm}`;
}

/** `2026-10-23` → `10/23`. */
export function shortDate(iso: string): string {
  const m = /^\d{4}-(\d{2})-(\d{2})$/.exec(iso);
  return m ? `${m[1].replace(/^0/, '')}/${m[2].replace(/^0/, '')}` : iso;
}

export interface SeriesBar {
  date: string;
  value: string;
  /** Bar height, % of the window max; a 2% sliver for any positive value; 0 for
   *  zero, negative or undrawable values. Geometry only — never shown as text. */
  percent: number;
}

/** One metric's daily bars, scaled to that metric's own max. */
export function seriesBars(
  series: readonly PerformanceSeriesPoint[],
  key: string,
): SeriesBar[] {
  const rows = series.map((p) => {
    const value = p.values[key] ?? '0';
    const magnitude = chartMagnitude(value);
    return { date: p.date, value, magnitude: magnitude !== null && magnitude > 0 ? magnitude : 0 };
  });
  const max = rows.reduce((m, r) => Math.max(m, r.magnitude), 0);
  return rows.map((r) => ({
    date: r.date,
    value: r.value,
    percent: max === 0 || r.magnitude === 0 ? 0 : Math.max(2, (r.magnitude / max) * 100),
  }));
}
