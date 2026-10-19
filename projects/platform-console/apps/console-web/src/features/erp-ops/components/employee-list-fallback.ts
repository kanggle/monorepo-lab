import { ApiError } from '@/shared/api/errors';

/**
 * TASK-PC-FE-349 — the note shown when an employee picker falls back to a raw-id input because the
 * masterdata employee LIST could not be read.
 *
 * The list is masterdata `READ` scope (masterdata roles only), so an operator holding only the ERP
 * approval roles — the shipped «결재 운영» set — gets a `403` there while every approval write is
 * still open to them. A `403` is a permission answer, not an outage: say so (owner-approved copy,
 * 2026-10-19 UTC). Every other failure (5xx, network, 401 — the session layer re-logs in) keeps the
 * «불러오지 못해» wording. The id input stays in both cases — the server accepts the write.
 *
 * `who` = whose id the operator now types («대상» · «결재자» · «대결자» · «위임자/대행자»).
 */
export function employeeListFallbackText(error: unknown, who: string): string {
  if (error instanceof ApiError && error.status === 403) {
    return `기준정보 조회 권한이 없어 직원 목록을 볼 수 없습니다. ${who} 직원 ID 를 직접 입력하세요.`;
  }
  return `직원 목록을 불러오지 못해 ${who} 직원 ID 를 직접 입력합니다.`;
}
