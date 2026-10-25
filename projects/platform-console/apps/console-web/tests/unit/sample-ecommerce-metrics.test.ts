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

  it('counts only realised sales — the September month is the ten CONFIRMED/SHIPPED orders', async () => {
    const m = PerformanceMetricsSchema.parse(
      await (await get('/api/admin/orders/metrics?period=month')).json(),
    );
    // Hand-summed. Realised, paid inside 09-01..09-15 20:00 KST (revenue, units):
    // order-2 09-02 45000 (1) · order-7 09-03 38000 (2) · order-8 09-04 28000 (1) ·
    // order-9 09-06 24000 (2) · order-10 09-09 45000 (1) · order-11 09-10 56000 (2) ·
    // order-12 09-12 31000 (2) · order-13 09-13 28000 (1) · order-14 09-14 20000 (1) ·
    // order-15 09-15 28000 (1)  =>  343000 over 14 units in 10 orders. The PENDING
    // order-1/-16, the CANCELLED order-17 and the BACKORDERED order-6 are seeded in the
    // same weeks and must not appear; the August realised orders (3, 5) are outside.
    expect(m.metrics).toEqual([
      { key: 'revenue', unit: 'money', currency: 'KRW', current: '343000', previous: '0' },
      { key: 'units', unit: 'count', currency: null, current: '14', previous: '0' },
      { key: 'orders', unit: 'count', currency: null, current: '10', previous: '0' },
    ]);
    expect(m.series.find((s) => s.date === '2026-09-02')?.values.revenue).toBe('45000');
    expect(m.series.find((s) => s.date === '2026-09-12')?.values.revenue).toBe('31000');
    expect(m.series.filter((s) => s.values.revenue !== '0').map((s) => s.date)).toEqual([
      '2026-09-02', '2026-09-03', '2026-09-04', '2026-09-06', '2026-09-09',
      '2026-09-10', '2026-09-12', '2026-09-13', '2026-09-14', '2026-09-15',
    ]);
  });

  it('the week window (09-14..15) holds order-14 (20000) and order-15 (28000)', async () => {
    const w = PerformanceMetricsSchema.parse(
      await (await get('/api/admin/orders/metrics?period=week')).json(),
    );
    expect(w.metrics).toEqual([
      { key: 'revenue', unit: 'money', currency: 'KRW', current: '48000', previous: '0' },
      { key: 'units', unit: 'count', currency: null, current: '2', previous: '0' },
      { key: 'orders', unit: 'count', currency: null, current: '2', previous: '0' },
    ]);
    expect(w.series.map((s) => [s.date, s.values.revenue])).toEqual([
      ['2026-09-14', '20000'],
      ['2026-09-15', '28000'],
    ]);
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
      ['prod-sample-0001', 'prod-sample-0002', 'prod-sample-0003', 'prod-sample-0004'],
    );
    // Month rankings (hand-summed from the ten realised September orders):
    // revenue  prod-4 28000+56000+28000+28000=140000 · prod-2 45000+45000=90000 ·
    //          prod-1 38000+19000+20000=77000 · prod-3 24000+12000=36000
    expect(month.topProductsByRevenue.map((r) => [r.id, r.value])).toEqual([
      ['prod-sample-0004', 140000],
      ['prod-sample-0002', 90000],
      ['prod-sample-0001', 77000],
      ['prod-sample-0003', 36000],
    ]);
    // orders   prod-4 in 4 · prod-1 in 3 · prod-2 in 2 · prod-3 in 2 (tie -> id order)
    expect(month.topProductsByOrderCount.map((r) => [r.id, r.value])).toEqual([
      ['prod-sample-0004', 4],
      ['prod-sample-0001', 3],
      ['prod-sample-0002', 2],
      ['prod-sample-0003', 2],
    ]);
    // sellers: revenue s1 167000 · s4 140000 · s2 36000; orders s1 5 · s4 4 · s2 2
    expect(month.topSellersByRevenue.map((r) => [r.id, r.value])).toEqual([
      ['seller-sample-0001', 167000],
      ['seller-sample-0004', 140000],
      ['seller-sample-0002', 36000],
    ]);
    expect(month.topSellersByOrderCount.map((r) => [r.id, r.value])).toEqual([
      ['seller-sample-0001', 5],
      ['seller-sample-0004', 4],
      ['seller-sample-0002', 2],
    ]);
  });
});
