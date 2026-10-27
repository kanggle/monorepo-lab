import { describe, expect, it } from 'vitest';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_AS_OF } from '@/shared/sample/codes';
import { SAMPLE_METRICS_WINDOWS } from '@/shared/sample/fixtures/metrics-windows';
import { SAMPLE_METRICS_WINDOWS as ECOMMERCE_WINDOWS } from '@/shared/sample/fixtures/ecommerce';
import { kstMetricsWindows, METRICS_PERIODS } from '@/shared/lib/kst-period';
import { PerformanceMetricsSchema } from '@/shared/api/performance-metrics';
import {
  InspectionSchema,
  ShipmentPageSchema,
} from '@/features/wms-ops/api/types';

/**
 * TASK-MONO-808 — the anonymous visitor's wms «실적» strip (ADR-MONO-074 sample
 * mode). The strip is derived from the SAME seed rows the sample lists show, so
 * the expectations below are re-derived THROUGH THE ROUTER from those lists
 * (shipments, ASN inspection), never from the fixture's internals.
 */

async function admin(path: string): Promise<Response> {
  return sampleResponse({ core: 'wms', surface: 'wms', method: 'GET', path });
}

const inside = (iso: string, w: { from: string; to: string }) => {
  const t = Date.parse(iso);
  return t >= Date.parse(w.from) && t < Date.parse(w.to);
};

async function metrics(period: string) {
  return PerformanceMetricsSchema.parse(
    await (await admin(`/api/v1/admin/dashboard/metrics?period=${period}`)).json(),
  );
}

describe('sample wms /dashboard/metrics (TASK-MONO-808)', () => {
  it('the shared windows equal kstMetricsWindows at SAMPLE_AS_OF — and are the ecommerce ones', () => {
    for (const p of METRICS_PERIODS) {
      const want = kstMetricsWindows(p, new Date(SAMPLE_AS_OF));
      expect(SAMPLE_METRICS_WINDOWS[p].current, p).toEqual(want.current);
      expect(SAMPLE_METRICS_WINDOWS[p].previous, p).toEqual(want.previous);
      expect([...SAMPLE_METRICS_WINDOWS[p].dates], p).toEqual(want.dates);
    }
    // One copy, re-exported (not a second hand-typed table).
    expect(ECOMMERCE_WINDOWS).toBe(SAMPLE_METRICS_WINDOWS);
  });

  it('answers the shared contract shape for both periods — three count metrics', async () => {
    for (const p of METRICS_PERIODS) {
      const res = await admin(`/api/v1/admin/dashboard/metrics?period=${p}`);
      expect(res.status, p).toBe(200);
      const parsed = PerformanceMetricsSchema.safeParse(await res.json());
      expect(parsed.success, p).toBe(true);
      expect(parsed.data?.period).toBe(p);
      expect(parsed.data?.timezone).toBe('Asia/Seoul');
      expect(parsed.data?.metrics.map((m) => m.key)).toEqual([
        'qtyReceived',
        'qtyShipped',
        'shipmentCount',
      ]);
      expect(parsed.data?.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
    }
  });

  it('출고 수량 / 출고 건수 equal the sample shipment list inside each window', async () => {
    const shipments = ShipmentPageSchema.parse(
      await (await admin('/api/v1/admin/dashboard/shipments?page=0&size=100')).json(),
    ).content;
    expect(shipments.length).toBeGreaterThan(0); // non-vacuous
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      const w = SAMPLE_METRICS_WINDOWS[p];
      for (const [side, win] of [['current', w.current], ['previous', w.previous]] as const) {
        const rows = shipments.filter((s) => s.shippedAt && inside(s.shippedAt, win));
        const qty = rows.reduce((a, s) => a + (s.totalQty ?? 0), 0);
        expect(m.metrics.find((x) => x.key === 'qtyShipped')?.[side], `${p} ${side}`).toBe(String(qty));
        expect(m.metrics.find((x) => x.key === 'shipmentCount')?.[side], `${p} ${side}`).toBe(
          String(rows.length),
        );
      }
    }
  });

  it('입고 수량 = the CLOSED ASNs’ inspection passed quantities, put away by closedAt', async () => {
    const inspection = InspectionSchema.parse(
      await (await admin('/api/v1/admin/dashboard/asns/asn-sample-0002/inspection')).json(),
    );
    const month = await metrics('month');
    const week = await metrics('week');
    // asn-0002 put away 2026-09-12 (KST) alone on that day — its own passed quantity.
    expect(month.series.find((s) => s.date === '2026-09-12')?.values.qtyReceived).toBe(
      String(inspection.totalQtyPassed),
    );
    // Hand-summed month: 500 (09-12) + 120 (09-03) + 80 (09-06; 83 expected, 3 damaged)
    // + 200 (09-10) + 60 (09-14) + 45 (09-15) = 1005.
    expect(month.metrics.find((x) => x.key === 'qtyReceived')?.current).toBe('1005');
    // Week window (09-14..15): 60 + 45 = 105.
    expect(week.metrics.find((x) => x.key === 'qtyReceived')?.current).toBe('105');
    const received = (m: typeof month) =>
      m.series.filter((s) => s.values.qtyReceived !== '0').map((s) => s.date);
    expect(received(month)).toEqual([
      '2026-09-03', '2026-09-06', '2026-09-10', '2026-09-12', '2026-09-14', '2026-09-15',
    ]);
    expect(received(week)).toEqual(['2026-09-14', '2026-09-15']);
    // The damaged-quantity ASN: passed (80), not expected (83), is what counts.
    const damaged = InspectionSchema.parse(
      await (await admin('/api/v1/admin/dashboard/asns/asn-sample-0004/inspection')).json(),
    );
    expect(damaged.totalQtyPassed).toBe(80);
    expect(damaged.totalQtyExpected).toBe(83);
  });

  it('TASK-PC-FE-374/380 — 출고 bars: 8 KST days in the month (hand-listed), 1 in the week', async () => {
    const month = await metrics('month');
    const week = await metrics('week');
    const shippedDays = (m: typeof month) =>
      m.series.filter((s) => s.values.shipmentCount !== '0').map((s) => s.date);
    expect(shippedDays(month)).toEqual([
      '2026-09-02', '2026-09-04', '2026-09-08', '2026-09-09', '2026-09-10',
      '2026-09-12', '2026-09-13', '2026-09-14',
    ]);
    expect(shippedDays(week)).toEqual(['2026-09-14']);
    // Month: 25 + 40 + 20 (ord-0011, 09-08) + 15 + 35 + 30 + 10 + 20 (ord-0003) = 195 over 8 shipments;
    // week: 20 over 1.
    expect(month.metrics.find((x) => x.key === 'qtyShipped')?.current).toBe('195');
    expect(month.metrics.find((x) => x.key === 'shipmentCount')?.current).toBe('8');
    expect(week.metrics.find((x) => x.key === 'qtyShipped')?.current).toBe('20');
    expect(week.metrics.find((x) => x.key === 'shipmentCount')?.current).toBe('1');
  });

  it('the series is every date of the current window, zero-filled, and sums to the current totals', async () => {
    for (const p of METRICS_PERIODS) {
      const m = await metrics(p);
      expect(m.series.map((s) => s.date), p).toEqual([...SAMPLE_METRICS_WINDOWS[p].dates]);
      for (const key of ['qtyReceived', 'qtyShipped', 'shipmentCount']) {
        const sum = m.series.reduce((a, s) => a + BigInt(s.values[key]), BigInt(0));
        expect(String(sum), `${p} ${key}`).toBe(m.metrics.find((x) => x.key === key)?.current);
      }
      // Zero-filled: quiet days are present as "0", not dropped.
      expect(m.series.some((s) => s.values.qtyShipped === '0'), p).toBe(true);
    }
    // In the week window the only shipment is on 09-14 KST (09:00Z = 18:00 KST).
    const week = await metrics('week');
    expect(week.series.find((s) => s.date === '2026-09-14')?.values.shipmentCount).toBe('1');
    expect(week.series.find((s) => s.date === '2026-09-15')?.values.shipmentCount).toBe('0');
  });

  it('TASK-PC-FE-380 — the PREVIOUS week window (09-07..08 KST) holds one shipment: 20 units', async () => {
    const m = await metrics('week');
    // Hand-counted: ord-sample-0011 shipped 09-08 12:00 KST, qty 20. No ASN is put away
    // on 09-07..08 (closed 09-06 and 09-10), so 입고 stays "0".
    expect(m.metrics.map((x) => [x.key, x.previous])).toEqual([
      ['qtyReceived', '0'],
      ['qtyShipped', '20'],
      ['shipmentCount', '1'],
    ]);
  });

  it('the previous MONTH window (08-01..15) holds no sample movement → "0", not missing', async () => {
    const m = await metrics('month');
    expect(m.metrics.map((x) => x.previous)).toEqual(['0', '0', '0']);
  });

  it('an unknown warehouseId narrows to nothing; the sample warehouse equals warehouse-global', async () => {
    const all = await metrics('month');
    const one = PerformanceMetricsSchema.parse(
      await (await admin('/api/v1/admin/dashboard/metrics?period=month&warehouseId=wh-sample-0001')).json(),
    );
    const none = PerformanceMetricsSchema.parse(
      await (await admin('/api/v1/admin/dashboard/metrics?period=month&warehouseId=nope')).json(),
    );
    expect(one.metrics).toEqual(all.metrics);
    expect(none.metrics.map((x) => x.current)).toEqual(['0', '0', '0']);
  });

  it('no / unknown period → not answered (the console always sends one)', async () => {
    expect((await admin('/api/v1/admin/dashboard/metrics')).status).toBe(503);
    expect((await admin('/api/v1/admin/dashboard/metrics?period=year')).status).toBe(503);
  });
});
