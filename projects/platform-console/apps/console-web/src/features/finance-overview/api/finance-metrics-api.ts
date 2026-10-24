import { callFinance } from '@/shared/api/finance-accounts-read';
import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PerformanceMetricsSchema,
  type PerformanceMetrics,
} from '@/shared/api/performance-metrics';

// ---------------------------------------------------------------------------
// GET /api/finance/accounts/metrics?period= — the Finance 개요 «실적» strip
// (ADR-MONO-084 P7, TASK-MONO-812)
// ---------------------------------------------------------------------------

/**
 * 충전 · 이체 · 캡처 — per type a count (`topupCount` / `transferCount` /
 * `captureCount`) and an amount per currency (`topupAmount:<CUR>` …, minor
 * units, never added across currencies), current vs the same-length previous
 * KST window, with a daily series. Shape =
 * `platform/contracts/performance-metrics-contract.md`, parsed by the shared
 * schema (integer strings, F5).
 *
 * What each key counts is the producer's contract (`finance-platform/specs/
 * contracts/http/account-api.md` § `GET /api/finance/accounts/metrics`):
 * settled transactions only, attributed to `settledAt`; a hold counts only as
 * its capture (the captured part), a released hold and a failed transaction
 * move nothing. The response is a **tenant-wide total** — no account id
 * crosses to the console (the honest constraint of this overview: finance v1
 * has no account list, and this read does not become one).
 *
 * Same hardened call site as the overview's account reads (`callFinance` —
 * the domain-facing IAM OIDC token, no `X-Tenant-Id`: finance resolves the
 * tenant from the token producer-side). Read-only: no `Idempotency-Key`, no
 * body. The console sends nothing but `period`. `403` → `ApiError(403)` → the
 * strip's «권한 없음»; `503` / timeout / network → `FinanceUnavailableError` →
 * «불러올 수 없음»; `401` → `ApiError(401)` → the overview's whole-session
 * re-login. A body that does not parse throws, and the strip degrades (never
 * zeros).
 *
 * Lives in `features/finance-overview` (not `shared/api`): this overview is
 * its only caller.
 */
export const FINANCE_METRICS_PATH = '/api/finance/accounts/metrics';

export async function getFinanceMetrics(
  period: MetricsPeriod,
): Promise<PerformanceMetrics> {
  const qs = new URLSearchParams({ period });
  return callFinance(
    {
      path: `${FINANCE_METRICS_PATH}?${qs.toString()}`,
      // No id in this path — the log shape is the path itself.
      logPath: FINANCE_METRICS_PATH,
    },
    (json) => PerformanceMetricsSchema.parse(json),
  );
}
