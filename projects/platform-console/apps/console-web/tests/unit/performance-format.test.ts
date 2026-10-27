import { describe, expect, it } from 'vitest';
import {
  comparisonBars,
  describeDelta,
  formatDelta,
  formatMetricValue,
  formatWireMoment,
  metricDelta,
  metricLabel,
  seriesBars,
  shortDate,
} from '@/shared/lib/performance-format';
import type { PerformanceSeriesPoint } from '@/shared/api/performance-metrics';

/** TASK-MONO-806 AC-4 — the strip's pure renderers. */
describe('metricDelta', () => {
  it('a rise and a fall, one decimal', () => {
    expect(metricDelta('12340000', '11020000')).toEqual({ direction: 'up', percent: '12.0' });
    expect(metricDelta('96', '100')).toEqual({ direction: 'down', percent: '4.0' });
  });

  it('rounds half-up from the exact ratio', () => {
    expect(metricDelta('4', '3').percent).toBe('33.3'); // 33.33…
    expect(metricDelta('5', '3').percent).toBe('66.7'); // 66.66…
    expect(metricDelta('2001', '2000').percent).toBe('0.1'); // 0.05 → 0.1
    expect(metricDelta('20001', '20000').percent).toBe('0.0'); // 0.005 → 0.0
  });

  it('no change', () => {
    expect(metricDelta('812', '812')).toEqual({ direction: 'flat', percent: '0.0' });
  });

  it('previous 0 has no percentage — never a division by zero', () => {
    expect(metricDelta('5', '0')).toEqual({ direction: 'up', percent: null });
    expect(metricDelta('0', '0')).toEqual({ direction: 'flat', percent: null });
    expect(formatDelta(metricDelta('5', '0'))).toBe('—');
    expect(describeDelta(metricDelta('0', '0'))).toBe('직전 같은 기간과 같습니다');
  });

  it('negative previous (a refunds metric) uses its magnitude', () => {
    expect(metricDelta('-50', '-100')).toEqual({ direction: 'up', percent: '50.0' });
  });

  it('is exact beyond the float range (F5)', () => {
    // 2^53 + 1 vs 2^53: a float would see no change at all.
    expect(metricDelta('9007199254740993', '9007199254740992').direction).toBe('up');
  });

  it('visible and spoken forms', () => {
    expect(formatDelta({ direction: 'up', percent: '12.3' })).toBe('▲ 12.3%');
    expect(formatDelta({ direction: 'down', percent: '4.0' })).toBe('▼ 4.0%');
    expect(formatDelta({ direction: 'flat', percent: '0.0' })).toBe('– 0.0%');
    expect(describeDelta({ direction: 'down', percent: '4.0' })).toBe('직전 같은 기간 대비 4.0% 감소');
  });
});

describe('formatMetricValue / metricLabel', () => {
  it('money from the string, counts grouped', () => {
    expect(formatMetricValue('12340000', { unit: 'money', currency: 'KRW' })).toBe('₩12,340,000');
    expect(formatMetricValue('123450', { unit: 'money', currency: 'USD' })).toBe('1,234.50 USD');
    expect(formatMetricValue('1234', { unit: 'count', currency: null })).toBe('1,234');
    expect(formatMetricValue('0', { unit: 'count', currency: null })).toBe('0');
  });

  it('a per-currency key falls back to its base label', () => {
    const labels = { amount: '거래 금액', items: '수량' };
    expect(metricLabel({ key: 'items', currency: null }, labels)).toBe('수량');
    expect(metricLabel({ key: 'amount:USD', currency: 'USD' }, labels)).toBe('거래 금액 (USD)');
    expect(metricLabel({ key: 'unknown', currency: null }, labels)).toBe('unknown');
  });
});

describe('seriesBars', () => {
  const series: PerformanceSeriesPoint[] = [
    { date: '2026-10-01', values: { a: '0' } },
    { date: '2026-10-02', values: { a: '1' } },
    { date: '2026-10-03', values: { a: '1000' } },
    { date: '2026-10-04', values: { a: '-5' } },
    { date: '2026-10-05', values: {} },
  ];

  it('scales to the max, keeps a sliver for small positives, 0 for zero / negative / missing', () => {
    expect(seriesBars(series, 'a').map((b) => b.percent)).toEqual([0, 2, 100, 0, 0]);
    expect(seriesBars(series, 'a')[4].value).toBe('0');
  });

  it('an all-zero series draws nothing (no divide-by-zero)', () => {
    expect(seriesBars([{ date: '2026-10-01', values: { a: '0' } }], 'a')[0].percent).toBe(0);
  });
});

/** TASK-PC-FE-379 — the operator overview headline's «이번 주 · 지난 주» bars. */
describe('comparisonBars', () => {
  it('scales both windows to the larger of the two', () => {
    expect(comparisonBars('7', '4')).toEqual({ currentPercent: 100, previousPercent: (4 / 7) * 100 });
    expect(comparisonBars('640', '700')).toEqual({ currentPercent: (640 / 700) * 100, previousPercent: 100 });
    expect(comparisonBars('8', '8')).toEqual({ currentPercent: 100, previousPercent: 100 });
  });

  it('a zero is an empty bar, both zero is two empty bars — never NaN', () => {
    expect(comparisonBars('12', '0')).toEqual({ currentPercent: 100, previousPercent: 0 });
    expect(comparisonBars('0', '0')).toEqual({ currentPercent: 0, previousPercent: 0 });
  });

  it('a tiny positive value keeps a visible 2% sliver', () => {
    expect(comparisonBars('1', '100000')).toEqual({ currentPercent: 2, previousPercent: 100 });
  });

  it('negative or undrawable values draw nothing; big integer strings stay finite', () => {
    expect(comparisonBars('-5', '10')).toEqual({ currentPercent: 0, previousPercent: 100 });
    expect(comparisonBars('abc', '1e400')).toEqual({ currentPercent: 0, previousPercent: 0 });
    const big = comparisonBars('900000000000000000000', '450000000000000000000');
    expect(big.currentPercent).toBe(100);
    expect(big.previousPercent).toBeCloseTo(50, 10);
  });
});

describe('wire labels', () => {
  it('formats without a Date or a zone', () => {
    expect(formatWireMoment('2026-10-01T00:00:00+09:00')).toBe('10월 1일 00:00');
    expect(formatWireMoment('2026-10-23T14:05:59+09:00')).toBe('10월 23일 14:05');
    expect(shortDate('2026-03-09')).toBe('3/9');
  });
});
