import { describe, expect, it } from 'vitest';
import { ApiError } from '@/shared/api/errors';
import { employeeListFallbackText } from '@/features/erp-ops/components/employee-list-fallback';

/**
 * TASK-PC-FE-349 (403 wording revised by TASK-ERP-BE-045) — an operator holding only the ERP approval roles (the shipped «결재 운영» set =
 * ERP_APPROVAL_READ + ERP_APPROVAL_WRITE) gets `403` on the masterdata employee LIST (READ scope =
 * masterdata roles only). The four employee pickers used to call that «불러오지 못해» — an outage
 * wording for a permission answer. A 403 now says so; every other failure keeps the old wording.
 */
describe('employeeListFallbackText', () => {
  // TASK-ERP-BE-045 — the pickers now read `GET /employees/picker`, open to 기준정보 조회 ∨ 기준정보
  // 변경 ∨ 결재 변경. A 403 there means the operator holds NEITHER, so the copy names both (console
  // contract § 2.4.8 «Employee pickers read the picker list»).
  it('403 → names both missing permissions (기준정보 조회 · 결재 변경)', () => {
    const e = new ApiError(403, 'PERMISSION_DENIED', 'forbidden');
    expect(employeeListFallbackText(e, '결재자')).toBe(
      '기준정보 조회 · 결재 변경 권한이 없어 직원 목록을 볼 수 없습니다. 결재자 직원 ID 를 직접 입력하세요.',
    );
    expect(employeeListFallbackText(e, '위임자/대행자')).toBe(
      '기준정보 조회 · 결재 변경 권한이 없어 직원 목록을 볼 수 없습니다. 위임자/대행자 직원 ID 를 직접 입력하세요.',
    );
  });

  it('control: an outage (503) keeps the old «불러오지 못해» wording', () => {
    const e = new ApiError(503, 'ERP_UNAVAILABLE', 'down');
    expect(employeeListFallbackText(e, '대결자')).toBe(
      '직원 목록을 불러오지 못해 대결자 직원 ID 를 직접 입력합니다.',
    );
  });

  it('control: a non-ApiError (network) and an absent error keep the old wording', () => {
    expect(employeeListFallbackText(new TypeError('fetch failed'), '대상')).toBe(
      '직원 목록을 불러오지 못해 대상 직원 ID 를 직접 입력합니다.',
    );
    expect(employeeListFallbackText(null, '대상')).toBe(
      '직원 목록을 불러오지 못해 대상 직원 ID 를 직접 입력합니다.',
    );
  });

  it('a 401 is NOT a permission message (the session layer re-logs in; keep the generic wording)', () => {
    const e = new ApiError(401, 'TOKEN_INVALID', 'expired');
    expect(employeeListFallbackText(e, '대상')).toContain('불러오지 못해');
  });
});
