import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PerformanceMetricsSchema,
  type PerformanceMetrics,
} from '@/shared/api/performance-metrics';
import { callWmsAdmin } from './wms-client';

// ---------------------------------------------------------------------------
// GET /dashboard/metrics?period= — the 개요 «실적» strip (ADR-MONO-084 P3,
// TASK-MONO-808)
// ---------------------------------------------------------------------------

/**
 * 입고 수량 (`qtyReceived`) · 출고 수량 (`qtyShipped`) · 출고 건수
 * (`shipmentCount`), current vs the same-length previous KST window, with a
 * daily series. Shape = `platform/contracts/performance-metrics-contract.md`
 * (parsed by the shared schema — integer strings, F5).
 *
 * **Warehouse-global**, like `/dashboard/throughput` (admin contract § 1.0 —
 * not tenant-partitioned). The 개요 calls it WITHOUT `warehouseId` (the whole
 * operation): the page has no single selected-warehouse context — the
 * throughput card's warehouse is a client-side selector inside that card only.
 * `warehouseId` stays an optional argument because the producer accepts it.
 *
 * Returns the parsed body only (not the `WmsResult` envelope): the strip has
 * no use for the read-model lag hint, and `performanceMetricsCell` reads a
 * plain body.
 */
export async function getWmsMetrics(
  period: MetricsPeriod,
  warehouseId?: string,
): Promise<PerformanceMetrics> {
  const qs = new URLSearchParams({ period });
  if (warehouseId) qs.set('warehouseId', warehouseId);
  const res = await callWmsAdmin(
    { method: 'GET', path: `/dashboard/metrics?${qs.toString()}` },
    (json) => PerformanceMetricsSchema.parse(json),
  );
  return res.data;
}
