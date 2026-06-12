import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';

/**
 * Maps a reconciliation discrepancy *resolve* error to an inline-actionable
 * Korean message (TASK-PC-FE-073 — § 2.4.7.1 resolve-specific resilience;
 * graceful, NO crash). Mirrors the erp `approval-error.ts` helper.
 *
 * The resolve is a normal operator action whose failure modes are all
 * recoverable inline (never an error boundary):
 *   - `409 RECONCILIATION_ALREADY_RESOLVED` — a concurrent operator (or a
 *     stale UI) already resolved it → "이미 해소됨, 새로고침";
 *   - `422 RECONCILIATION_PERIOD_LOCKED` — the discrepancy's statement date
 *     is in a CLOSED accounting period (frozen with the books) → "기간 마감,
 *     다음 open 기간에";
 *   - `404 RECONCILIATION_DISCREPANCY_NOT_FOUND` — the discrepancy id is
 *     unknown / not in tenant;
 *   - `LedgerUnavailableError` (503 / timeout / network) → "일시적으로
 *     사용할 수 없습니다" (the resolve affordance re-enables on retry).
 *
 * `401` (whole-session re-login) and `403` (not finance-scoped) are NOT
 * resolved here — they are surfaced by the shared shell/auth path; this
 * helper falls through to a generic message for them and any other code.
 */
export function discrepancyResolveErrorMessage(err: unknown): string {
  if (err instanceof LedgerUnavailableError) {
    return 'ledger 를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하세요.';
  }
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'RECONCILIATION_ALREADY_RESOLVED':
        return '이미 해소된 대사 차이입니다. 새로고침 후 확인하세요.';
      case 'RECONCILIATION_PERIOD_LOCKED':
        return '해당 기간이 마감되어 대사 차이를 해소할 수 없습니다. 다음 open 기간에 처리하세요.';
      case 'RECONCILIATION_DISCREPANCY_NOT_FOUND':
        return '대상 대사 차이를 찾을 수 없습니다. 목록을 새로고침하세요.';
      case 'VALIDATION_ERROR':
        return '입력값이 올바르지 않습니다 (해소 유형 선택 + 사유가 필요합니다).';
      case 'TENANT_FORBIDDEN':
      case 'PERMISSION_DENIED':
      case 'FORBIDDEN':
        return '이 작업을 수행할 권한이 없습니다 (finance 스코프 확인 필요).';
      default:
        return err.message || '대사 차이 해소를 처리하지 못했습니다.';
    }
  }
  return '대사 차이 해소를 처리하지 못했습니다.';
}
