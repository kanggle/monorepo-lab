import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PerformanceMetricsSchema,
  type PerformanceMetrics,
} from '@/shared/api/performance-metrics';
import { callScm } from './scm-client';

// ---------------------------------------------------------------------------
// GET /api/v1/procurement/po/metrics?period= — the 개요 «실적» strip
// (ADR-MONO-084 P5, TASK-MONO-810)
// ---------------------------------------------------------------------------

/** TASK-PC-FE-372 — exported so the home headline leg (`shared/composition/operator-overview-headline.ts`) is pinned to it by a unit test. */
export const PO_METRICS_PATH = '/api/v1/procurement/po/metrics';

/**
 * 발주 건수 (`poCount`) · 발주 금액 (`poAmount:<CUR>` — one key per currency,
 * minor units, never added across currencies) · 입고 완료 (`receivedCount`),
 * current vs the same-length previous KST window, with a daily series. Shape =
 * `platform/contracts/performance-metrics-contract.md`, parsed by the shared
 * schema (integer strings, F5).
 *
 * What each key counts is the producer's contract (`procurement-api.md`
 * § `GET /po/metrics`): `poCount` / `poAmount` = `submittedAt` in the window and
 * the PO is not CANCELED now; `receivedCount` = POs whose transition to
 * `RECEIVED` happened in the window. The console sends nothing but `period`.
 *
 * Deliberately NOT re-exported from the `scm-api.ts` barrel: that barrel's
 * export set is pinned to the six list/detail reads (`scm-api.test.ts`), and
 * this read has one caller (`overview-state.ts`).
 *
 * The shared contract's body is the bare shape. The procurement service wraps
 * its other reads in `{ data, meta }`; an enveloped body is unwrapped here so
 * either form parses — anything else is a parse failure (the strip degrades).
 */
export async function getPoMetrics(
  period: MetricsPeriod,
): Promise<PerformanceMetrics> {
  const qs = new URLSearchParams({ period });
  const { raw } = await callScm(
    { path: `${PO_METRICS_PATH}?${qs.toString()}` },
    (json) => PerformanceMetricsSchema.parse(unwrap(json)),
  );
  return raw;
}

function unwrap(json: unknown): unknown {
  if (json && typeof json === 'object' && !('period' in json) && 'data' in json) {
    return (json as { data: unknown }).data;
  }
  return json;
}
