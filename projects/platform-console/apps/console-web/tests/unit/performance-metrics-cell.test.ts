import { describe, expect, it } from 'vitest';
import { ApiError } from '@/shared/api/errors';
import {
  PerformanceMetricsSchema,
  performanceMetricsCell,
} from '@/shared/api/performance-metrics';

/** TASK-MONO-806 AC-4 — the shared parse and per-cell outcome. */

const BODY = {
  period: 'month',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-01T00:00:00+09:00', to: '2026-10-23T14:05:00+09:00' },
  previous: { from: '2026-09-01T00:00:00+09:00', to: '2026-09-23T14:05:00+09:00' },
  metrics: [
    { key: 'amount', unit: 'money', currency: 'KRW', current: '12340000', previous: '11020000' },
    { key: 'items', unit: 'count', currency: null, current: '812', previous: '790' },
  ],
  series: [{ date: '2026-10-01', values: { amount: '410000', items: '27' } }],
};

describe('PerformanceMetricsSchema', () => {
  it('accepts the contract shape', () => {
    expect(PerformanceMetricsSchema.safeParse(BODY).success).toBe(true);
  });

  it('rejects a JSON number where an integer string belongs (F5)', () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[0], current: 12340000 }] };
    expect(PerformanceMetricsSchema.safeParse(bad).success).toBe(false);
    const badSeries = { ...BODY, series: [{ date: '2026-10-01', values: { amount: 1 } }] };
    expect(PerformanceMetricsSchema.safeParse(badSeries).success).toBe(false);
  });

  it('a count with no currency field at all reads as null (a producer that drops nulls — TASK-MONO-807)', () => {
    const { currency: _drop, ...countNoField } = BODY.metrics[1];
    void _drop;
    const parsed = PerformanceMetricsSchema.safeParse({ ...BODY, metrics: [BODY.metrics[0], countNoField] });
    expect(parsed.success).toBe(true);
    expect(parsed.data?.metrics[1].currency).toBeNull();
    // …but money still needs its currency
    const { currency: _m, ...moneyNoField } = BODY.metrics[0];
    void _m;
    expect(PerformanceMetricsSchema.safeParse({ ...BODY, metrics: [moneyNoField] }).success).toBe(false);
  });

  it('rejects duplicate keys and a unit / currency mismatch', () => {
    const dup = { ...BODY, metrics: [BODY.metrics[1], BODY.metrics[1]] };
    expect(PerformanceMetricsSchema.safeParse(dup).success).toBe(false);
    const moneyNoCurrency = { ...BODY, metrics: [{ ...BODY.metrics[0], currency: null }] };
    expect(PerformanceMetricsSchema.safeParse(moneyNoCurrency).success).toBe(false);
    const countWithCurrency = { ...BODY, metrics: [{ ...BODY.metrics[1], currency: 'KRW' }] };
    expect(PerformanceMetricsSchema.safeParse(countWithCurrency).success).toBe(false);
  });
});

describe('performanceMetricsCell', () => {
  it('ok', async () => {
    const cell = await performanceMetricsCell(Promise.resolve(BODY));
    expect(cell.status).toBe('ok');
    expect(cell.value?.metrics).toHaveLength(2);
  });

  it('a body that does not parse is degraded, not ok-with-holes', async () => {
    expect((await performanceMetricsCell(Promise.resolve({ period: 'today' }))).status).toBe('degraded');
  });

  it('403 → forbidden; 5xx / network → degraded', async () => {
    expect((await performanceMetricsCell(Promise.reject(new ApiError(403, 'FORBIDDEN', 'x')))).status)
      .toBe('forbidden');
    expect((await performanceMetricsCell(Promise.reject(new ApiError(503, 'UNAVAILABLE', 'x')))).status)
      .toBe('degraded');
    expect((await performanceMetricsCell(Promise.reject(new TypeError('fetch failed')))).status)
      .toBe('degraded');
  });

  it('401 is re-thrown for the whole-session re-login', async () => {
    await expect(
      performanceMetricsCell(Promise.reject(new ApiError(401, 'UNAUTHORIZED', 'x'))),
    ).rejects.toMatchObject({ status: 401 });
  });
});
