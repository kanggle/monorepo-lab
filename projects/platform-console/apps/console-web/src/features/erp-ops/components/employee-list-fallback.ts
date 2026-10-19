import { ApiError } from '@/shared/api/errors';

/**
 * The user-visible copy of the four employee pickers (결재 대상 · 결재선 · 대결자 · 위임 사실 필터),
 * kept in ONE module so it can be reviewed in one place.
 *
 * TASK-PC-FE-349 — the note shown when an employee picker falls back to a raw-id input because the
 * employee list could not be read. A `403` is a permission answer, not an outage: say so. Every
 * other failure (5xx, network, 401 — the session layer re-logs in) keeps the «불러오지 못해»
 * wording. The id input stays in both cases — the server accepts the write.
 *
 * TASK-ERP-BE-045 — the pickers now read `GET /employees/picker`, which opens to 기준정보 조회 ∨
 * 기준정보 변경 ∨ 결재 변경 (console contract § 2.4.8 «Employee pickers read the picker list»). A
 * `403` there therefore means the operator holds NEITHER 기준정보 조회 NOR 결재 변경, and the copy names
 * both. (A 결재 읽기-only operator lands here on the 위임 사실 filters — the picker does not open to
 * `ERP_APPROVAL_READ`.)
 *
 * `who` = whose id the operator now types («대상» · «결재자» · «대결자» · «위임자/대행자»).
 */
export function employeeListFallbackText(error: unknown, who: string): string {
  if (error instanceof ApiError && error.status === 403) {
    return `기준정보 조회 · 결재 변경 권한이 없어 직원 목록을 볼 수 없습니다. ${who} 직원 ID 를 직접 입력하세요.`;
  }
  return `직원 목록을 불러오지 못해 ${who} 직원 ID 를 직접 입력합니다.`;
}

/**
 * TASK-ERP-BE-045 — shown under a picker when the producer narrowed the list to the operator's
 * department subtree (`meta.scope === 'SUBTREE'`). An approver or delegate normally sits outside it,
 * so the note points at the «ID 직접 입력» toggle (Failure Scenario 4: a scoped list must not make an
 * out-of-subtree approver impossible to name).
 */
export const EMPLOYEE_PICKER_SCOPED_NOTICE =
  '내 부서 범위의 직원만 표시합니다. 목록에 없는 직원은 «ID 직접 입력» 을 누르세요.';

/** TASK-ERP-BE-045 — the toggle's two labels: list → raw id input, and back. */
export const EMPLOYEE_PICKER_MANUAL_LABEL = 'ID 직접 입력';
export const EMPLOYEE_PICKER_LIST_LABEL = '목록에서 고르기';
