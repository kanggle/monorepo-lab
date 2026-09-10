/**
 * 마스터 참조 컬럼의 표시 문자열 — **한 벌** (TASK-PC-FE-276).
 *
 * ## 왜 이 파일이 생겼나
 *
 * `/erp/masters` 의 참조 칸들이 **raw UUID 를 찍고 있었다**(부서의 「상위 부서」, 직원의
 * 「부서」·「직급」·「비용센터」, 비용센터의 「부서」 — 8곳). 🔴 데이터가 없어서가 아니다:
 * 바로 옆 `/erp/orgview` 는 같은 테넌트·같은 세션에서 `CODE · 이름` 으로 그린다.
 * **마스터 화면만 그 표현을 안 썼다.**
 *
 * 그리고 그 표현(`` `${code} · ${name}` ``)은 이미 이 프로젝트 안에 **11곳에 인라인으로
 * 복제**돼 있었다(`EmployeeOrgViewCard` · `BusinessPartnerList` · `JobGradeList` ·
 * `CostCenterList` · `MasterWriteDialog` · `OrgScopeDialogBody`). 🔴 거기에 9번째, 10번째
 * 사본을 더하는 것이 이 티켓의 Failure 2 다 — 같은 개념에 두 표현이 생기면 다음엔
 * 한쪽만 고쳐진다. ⇒ 여기 한 벌을 두고 전부 그것을 쓴다.
 *
 * 🔴 **`data-testid` 는 안 바꾼다.** 바뀌는 것은 값이고, 선택자가 바뀌면 그건 다른 변경이다.
 */

/** 참조 대상을 못 찾았을 때의 표시. 🔴 **id 로 되돌아가지 않는다.** */
export const MASTER_REF_UNRESOLVED = '이름 확인 불가';

/** 참조가 애초에 없을 때(최상위 부서 등). 기존 화면이 쓰던 값 그대로다. */
export const MASTER_REF_NONE = '—';

export interface MasterRefTarget {
  code?: string | null;
  name?: string | null;
}

/**
 * `CODE · 이름`. 코드가 없으면 이름만.
 *
 * 🔵 `MasterWriteDialog` 의 드롭다운 라벨이 쓰던 바로 그 규칙이고(`o.code ? … : o.name`),
 *    `EmployeeOrgViewCard` 의 조직 경로도 같은 모양이다. 새로 짓지 않았다.
 */
export function codeName(target: MasterRefTarget): string {
  const name = target.name ?? '';
  return target.code ? `${target.code} · ${name}` : name;
}

/**
 * 참조 컬럼 한 칸의 표시 문자열. **세 상태가 세 문자열이다.**
 *
 * | 상황 | 반환 | 왜 |
 * |---|---|---|
 * | `id` 가 없다 | `—` | 참조가 없는 것은 결함이 아니다(최상위 부서) |
 * | 해석됐다 | `CODE · 이름` | |
 * | 못 찾았다 | `이름 확인 불가` | 🔴 **id 로 조용히 되돌아가지 않는다** |
 *
 * 🔴🔴 마지막 줄이 이 함수의 요점이다. id 폴백은 결함을 «가끔» 되살리고, 그때는 아무도
 * 안 본다 — 화면은 대부분의 행에서 이름을 보여주므로 한두 행의 UUID 는 눈에 안 띈다.
 * 못 찾았다는 것을 **보이게** 해야 그 상태가 보고된다.
 *
 * 🔵 그래도 운영자가 그 id 를 알아야 할 수는 있다 ⇒ 호출부가 `title` 속성으로 실어 준다.
 *    보이는 텍스트가 아니므로 회귀 가드(렌더된 셀의 UUID)는 그것을 안 문다.
 */
export function masterRefLabel(
  id: string | null | undefined,
  resolved: MasterRefTarget | null | undefined,
): string {
  if (id === null || id === undefined || id === '') return MASTER_REF_NONE;
  if (!resolved || (!resolved.code && !resolved.name)) return MASTER_REF_UNRESOLVED;
  return codeName(resolved);
}

/**
 * `{ id, code, name }` 목록을 조회용 Map 으로. 목록 화면이 쓴다.
 *
 * 🔴 **페이지네이션 밖의 참조는 여기 없다.** 부서가 3건인 오늘은 안 보이지만, 늘면
 *    나타나는 부류다. 그때 이 함수는 `undefined` 를 주고 `masterRefLabel` 이
 *    `이름 확인 불가` 를 그린다 — **UUID 로 되돌아가지 않는다.** 그것이 이 조합을 고른
 *    이유다: 못 찾는 경우가 «조용한 퇴행» 이 아니라 **보이는 상태**가 된다.
 */
export interface MasterRefOption extends MasterRefTarget {
  id: string;
}

export function masterRefIndex(
  options: readonly MasterRefOption[] | undefined,
): Map<string, MasterRefTarget> {
  const m = new Map<string, MasterRefTarget>();
  for (const o of options ?? []) m.set(o.id, { code: o.code, name: o.name });
  return m;
}
