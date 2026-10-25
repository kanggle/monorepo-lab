import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PerformanceMetricsSchema,
  type PerformanceMetrics,
} from '@/shared/api/performance-metrics';
import { callApproval } from './approval-call';

// ---------------------------------------------------------------------------
// GET /api/erp/approval/metrics?period= — the 개요 «실적» strip (ADR-MONO-084 P4,
// TASK-MONO-809)
// ---------------------------------------------------------------------------

/** TASK-PC-FE-372 — exported so the home headline leg (`shared/composition/operator-overview-headline.ts`) is pinned to it by a unit test. */
export const APPROVAL_METRICS_PATH = '/api/erp/approval/metrics';

/**
 * 상신 (`submitted`) · 승인 (`approved` — final approval only) · 반려
 * (`rejected`), current vs the same-length previous KST window, with a daily
 * series. Shape = `platform/contracts/performance-metrics-contract.md` (parsed
 * by the shared schema — integer strings, F5).
 *
 * Scope follows the approval list rule, producer-side: operator/platform → the
 * whole tenant; otherwise only requests the caller takes part in. The console
 * sends nothing but `period`.
 */
export async function getApprovalMetrics(
  period: MetricsPeriod,
): Promise<PerformanceMetrics> {
  const qs = new URLSearchParams({ period });
  return callApproval(
    {
      path: `${APPROVAL_METRICS_PATH}?${qs.toString()}`,
      logPath: APPROVAL_METRICS_PATH,
    },
    (json) => PerformanceMetricsSchema.parse(json),
  );
}
