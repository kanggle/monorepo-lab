/**
 * TASK-PC-FE-349 — the note shown when an employee picker falls back to a raw-id input because the
 * masterdata employee LIST could not be read.
 *
 * `who` = whose id the operator now types («대상» · «결재자» · «대결자» · «위임자/대행자»).
 */
export function employeeListFallbackText(_error: unknown, who: string): string {
  return `직원 목록을 불러오지 못해 ${who} 직원 ID 를 직접 입력합니다.`;
}
