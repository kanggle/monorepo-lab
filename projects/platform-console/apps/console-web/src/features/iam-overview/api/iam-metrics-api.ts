import { AuditUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';
import type { MetricsPeriod } from '@/shared/lib/kst-period';
import {
  PerformanceMetricsSchema,
  type PerformanceMetrics,
} from '@/shared/api/performance-metrics';

// ---------------------------------------------------------------------------
// GET /api/admin/metrics?period=&tenantId= — the IAM 개요 «실적» strip
// (ADR-MONO-084 P6, TASK-MONO-811)
// ---------------------------------------------------------------------------

/**
 * 로그인 성공 (`loginSuccess`) · 로그인 실패 (`loginFailure`) · 계정 잠금
 * (`accountLocked`) · 신규 계정 (`newAccount`), all `unit=count`, current vs the
 * same-length previous KST window, with a daily series. Shape =
 * `platform/contracts/performance-metrics-contract.md`; what each key counts is
 * the producer's contract (`iam-platform/specs/contracts/http/admin-api.md`
 * § `GET /api/admin/metrics`).
 *
 * The body is the bare shape, parsed by the shared schema (integer strings,
 * F5). A body that does not parse throws inside the core, which reports it as
 * {@link AuditUnavailableError} — so the strip degrades, never shows zeros.
 *
 * Same hardened core as every IAM admin read (`callAdminGateway`): the
 * EXCHANGED operator token (never the IAM OIDC token), the active tenant as
 * `X-Tenant-Id`, and — exactly like `queryAudit` (`shared/api/iam-audit-read.ts`)
 * — the active tenant ALSO as the `tenantId` QUERY param, because admin-service
 * scopes this read by that param, not by the header. The caller passes the
 * tenant it already resolved, so the strip and the cards below it are scoped
 * by the one value.
 *
 * Read-only: no `X-Operator-Reason`, no `Idempotency-Key`. `403`
 * (`PERMISSION_DENIED` / `TENANT_SCOPE_DENIED`) → `ApiError(403)` → the strip's
 * «권한 없음»; `503` / timeout / network → {@link AuditUnavailableError} → the
 * strip's «불러올 수 없음» (the metrics read is the audit read's grade — same
 * timeout, same degrade class; the log prefix is its own). `401` →
 * `ApiError(401)` → the overview's whole-session re-login.
 *
 * Lives in `features/iam-overview` (not `shared/api`): this overview is its
 * only caller.
 */

export const IAM_METRICS_PATH = '/api/admin/metrics';

const IAM_METRICS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'iam_metrics',
  requestFailedLabel: 'iam metrics request failed',
  resolveTimeoutMs: (env) => env.AUDIT_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new AuditUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof AuditUnavailableError,
  messages: {
    degraded: 'IAM metrics service unavailable',
    timeout: 'IAM metrics call timed out',
    network: 'IAM metrics call failed',
  },
  forbiddenMode: 'dedicated',
  forceMutationHeaders: false,
};

export async function getIamMetrics(
  period: MetricsPeriod,
  tenantId: string,
): Promise<PerformanceMetrics> {
  const qs = new URLSearchParams({ period, tenantId });
  return callAdminGateway(
    { method: 'GET', path: `${IAM_METRICS_PATH}?${qs.toString()}` },
    (json) => PerformanceMetricsSchema.parse(json),
    IAM_METRICS_PROFILE,
  );
}
