import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_AS_OF } from '@/shared/sample/codes';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/ecommerce';
import { kstMetricsWindows, METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema } from '@/shared/api/performance-metrics';
import { OrderInsightsSchema } from '@/features/ecommerce-ops/api/order-types';

/**
 * TASK-MONO-807 — the anonymous visitor's «실적» strip and period-scoped rankings
 * (ADR-MONO-074 sample mode). The sample windows are written out (the sample tree
 * may not import `shared/lib`), so they are pinned here against the real thing.
 */

async function get(path: string): Promise<Response> {
  return sampleResponse({ core: 'ecommerce', surface: 'ecommerce_order', method: 'GET', path });
}

describe('sample ecommerce /orders/metrics (TASK-MONO-807)', () => {
  it('the written-out windows equal kstMetricsWindows at SAMPLE_AS_OF', () => {
    for (const p of METRICS_PERIODS) {
      const want = kstMetricsWindows(p, new Date(SAMPLE_AS_OF));
      expect(SAMPLE_METRICS_WINDOWS[p].current, p).toEqual(want.current);
      expect(SAMPLE_METRICS_WINDOWS[p].previous, p).toEqual(want.previous);
      expect([...SAMPLE_METRICS_WINDOWS[p].dates], p).toEqual(want.dates);
    }
  });

  it('answers the shared contract shape for both periods', async () => {
    for (const p of METRICS_PERIODS) {
      const res = await get(`/api/admin/orders/metrics?period=${p}`);
      expect(res.status, p).toBe(200);
      const parsed = PerformanceMetricsSchema.safeParse(await res.json());
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual(['revenue', 'units', 'orders']);
    }
  });

  it('counts only realised sales — the September month is order-2 alone', async () => {
    const m = PerformanceMetricsSchema.parse(
      await (await get('/api/admin/orders/metrics?period=month')).json(),
    );
    // order-2 (CONFIRMED, paid 2026-09-02 13:00 KST, 1 × 45000). The PENDING order-1 and
    // the BACKORDERED order-6 are seeded in the same weeks and must not appear.
    expect(m.metrics).toEqual([
      { key: 'revenue', unit: 'money', currency: 'KRW', current: '45000', previous: '0' },
      { key: 'units', unit: 'count', currency: null, current: '1', previous: '0' },
      { key: 'orders', unit: 'count', currency: null, current: '1', previous: '0' },
    ]);
    expect(m.series.find((s) => s.date === '2026-09-02')?.values.revenue).toBe('45000');
    expect(m.series.filter((s) => s.values.revenue !== '0')).toHaveLength(1);
  });

  it('is not mistaken for an order id (the /{id} detail branch)', async () => {
    const res = await get('/api/admin/orders/metrics');
    expect(res.status).not.toBe(404);
  });

  it('insights with a period keep only the orders paid inside it', async () => {
    const all = OrderInsightsSchema.parse(await (await get('/api/admin/orders/insights')).json());
    const month = OrderInsightsSchema.parse(
      await (await get('/api/admin/orders/insights?period=month')).json(),
    );
    expect(all.topProductsByRevenue.map((r) => r.id).sort()).toEqual(
      ['prod-sample-0001', 'prod-sample-0002', 'prod-sample-0003'],
    );
    expect(month.topProductsByRevenue.map((r) => r.id)).toEqual(['prod-sample-0002']);
  });
});
