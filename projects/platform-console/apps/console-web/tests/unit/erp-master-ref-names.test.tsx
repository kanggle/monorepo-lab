import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * 마스터 참조 컬럼 회귀 가드 (TASK-PC-FE-276).
 *
 * ## 무엇을 재는가 — **렌더된 셀 안에 UUID 모양이 있는가**
 *
 * 결함은 「참조 컬럼에 raw UUID 가 찍힌다」였고 8곳에 있었다. 🔴 술어를 파일마다 복제하면
 * 다음에 아홉 번째가 생겼을 때 그것만 안 지켜진다. 그래서 판정은 **한 술어**다:
 * 「렌더된 트리의 텍스트에 UUID 정규식이 걸리는가」.
 *
 * ## 🔴 비-공허성 — 하한의 대상은 «렌더된 참조 셀의 수» 다
 *
 * 픽스처가 비면 이 테스트는 **아무 셀도 안 보고 초록**이다. 그래서 매 칸이 먼저
 * 「참조 셀을 몇 개 그렸나」를 세고 하한을 건다. 🔵 하한을 «UUID 0건» 에 걸면 안 되는
 * 이유가 이것이다 — 0건은 «없다» 와 «안 봤다» 를 구별하지 못한다.
 *
 * ## 대조군
 *
 *  - **참조가 없는 행**(`parentId: null`) → `—` 이고 가드가 **안 문다**.
 *  - **참조를 못 찾은 행**(조회원 밖) → `이름 확인 불가` 이고, 🔴 **UUID 로 되돌아가지
 *    않는다**(그것이 이 티켓의 Edge Case 다: id 폴백은 결함을 «가끔» 되살린다).
 *
 * ## ⚪ 이 가드가 못 재는 것
 *
 * 이것은 **컴포넌트 렌더**를 잰다. 실제 화면에서 백엔드가 무엇을 주는지, 그리고 그 값이
 * 정말 UUID 인지는 안 잰다 — 그 축은 라이브이고, 648 의 자동 판정이 이 결함을 놓친 이유도
 * 같다(표지는 「글자가 있나」를 묻고 「무엇이 그려졌나」는 못 묻는다).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp/masters',
  useSearchParams: () => new URLSearchParams(),
}));

import { DepartmentList } from '@/features/erp-ops/components/DepartmentList';
import { EmployeeList } from '@/features/erp-ops/components/EmployeeList';
import { CostCenterList } from '@/features/erp-ops/components/CostCenterList';
import {
  masterRefLabel,
  MASTER_REF_UNRESOLVED,
  MASTER_REF_NONE,
} from '@/shared/lib/master-ref-label';

function wrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

// ---------------------------------------------------------------------------
// 픽스처 — 🔴 id 는 **실제 화면에서 본 모양**(UUID)이다. 읽기 쉬운 가짜 id 를 쓰면
//         이 가드는 결함을 재현할 수 없는 입력 위에서 초록이 된다.
// ---------------------------------------------------------------------------
const HQ = '01a085a1-bc98-741e-ba56-21b896de17f4';
const OPS = '01a085a1-bc98-741e-ba56-21b896de17f5';
const GHOST = '01a085a1-bc98-741e-ba56-21000000abcd';
const JG = '01a085a1-bc98-741e-ba56-21b896de17f7';
const CC = '01a085a1-bc98-741e-ba56-21b896de17f8';

const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i;
const PERIOD = { effectiveFrom: '2026-01-01', effectiveTo: null };

const DEPARTMENTS = {
  data: [
    { id: HQ, code: 'DEPT-HQ', name: '본사', parentId: null, status: 'ACTIVE', effectivePeriod: PERIOD },
    { id: OPS, code: 'DEPT-OPS', name: '운영본부', parentId: HQ, status: 'ACTIVE', effectivePeriod: PERIOD },
    // 🔴 대조군 — 조회원 밖의 부모(페이지네이션 함정). `이름 확인 불가` 여야 하고 UUID 는 안 된다.
    { id: OPS + 'a', code: 'DEPT-X', name: '떠도는팀', parentId: GHOST, status: 'ACTIVE', effectivePeriod: PERIOD },
  ],
  meta: { page: 0, size: 20, totalElements: 3 },
};

const EMPLOYEES = {
  data: [
    {
      id: 'e1', employeeNumber: 'EMP-0002', name: '이운영', status: 'ACTIVE',
      departmentId: OPS, jobGradeId: JG, costCenterId: CC, effectivePeriod: PERIOD,
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1 },
};

const COST_CENTERS = {
  data: [
    { id: CC, code: 'CC-OPS', name: '운영 코스트센터', departmentId: OPS, status: 'ACTIVE', effectivePeriod: PERIOD },
  ],
  meta: { page: 0, size: 20, totalElements: 1 },
};

const OPTIONS = {
  departments: [
    { id: HQ, code: 'DEPT-HQ', name: '본사' },
    { id: OPS, code: 'DEPT-OPS', name: '운영본부' },
  ],
  jobGrades: [{ id: JG, code: 'JG-G2', name: '대리' }],
  costCenters: [{ id: CC, code: 'CC-OPS', name: '운영 코스트센터' }],
};

/**
 * 렌더된 트리에서 **참조 셀**을 뽑는다.
 *
 * 🔴 «표의 모든 셀» 이 아니다. 참조 컬럼만 봐야 대조군(코드·이름 컬럼)이 오염되지 않는다.
 *    참조 셀은 `data-master-ref` 를 단 셀이다 — 그 속성이 곧 **모집단 선언**이고, 참조 칸이
 *    새로 생기면 마커를 달아야 가드가 그것을 본다. 🔴 `data-testid` 는 안 바꿨다(기존 e2e).
 */
function refCells(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>('[data-master-ref]'));
}

function assertNoUuidInRefCells(container: HTMLElement, floor: number, label: string) {
  const cells = refCells(container);
  // 🔴 비-공허성: 하한의 대상은 «렌더된 참조 셀의 수» 다.
  expect(cells.length, `${label}: 참조 셀이 ${cells.length}개 — 하한 ${floor}`).toBeGreaterThanOrEqual(floor);
  const offenders = cells
    .map((el) => (el.textContent ?? '').trim())
    .filter((t) => UUID_RE.test(t));
  expect(offenders, `${label}: 참조 셀에 UUID 원문이 그려졌습니다`).toEqual([]);
}

describe('마스터 참조 컬럼이 UUID 대신 이름을 그린다 (TASK-PC-FE-276)', () => {
  it('부서 목록 — 「상위 부서」 칸', () => {
    const { container } = render(
      <DepartmentList initial={DEPARTMENTS as never} parentOptions={OPTIONS.departments} />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 3, '부서 목록');
    expect(screen.getByText('DEPT-HQ · 본사')).toBeTruthy();
  });

  it('직원 목록 — 「부서」 칸', () => {
    const { container } = render(
      <EmployeeList initial={EMPLOYEES as never} optionSources={OPTIONS as never} />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 1, '직원 목록');
    expect(screen.getByText('DEPT-OPS · 운영본부')).toBeTruthy();
  });

  it('비용센터 목록 — 「부서」 칸', () => {
    const { container } = render(
      <CostCenterList initial={COST_CENTERS as never} optionSources={{ departments: OPTIONS.departments } as never} />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 1, '비용센터 목록');
    expect(screen.getByText('DEPT-OPS · 운영본부')).toBeTruthy();
  });

  it('🔴 대조군 — 참조가 없는 행은 `—` 이고 가드가 안 문다', () => {
    const { container } = render(
      <DepartmentList initial={DEPARTMENTS as never} parentOptions={OPTIONS.departments} />,
      { wrapper: wrapper() },
    );
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain(MASTER_REF_NONE);
  });

  it('🔴🔴 대조군 — 조회원 밖의 참조는 `이름 확인 불가` 다 (**id 로 안 돌아간다**)', () => {
    const { container } = render(
      <DepartmentList initial={DEPARTMENTS as never} parentOptions={OPTIONS.departments} />,
      { wrapper: wrapper() },
    );
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    // 그 셀은 UUID 를 **보이는 텍스트로** 안 싣는다. (원본 id 는 title 에만 있다)
    assertNoUuidInRefCells(container, 3, '부서 목록(미해석 행 포함)');
  });
});

describe('masterRefLabel — 세 상태가 세 문자열이다', () => {
  it('id 가 없다 → —', () => {
    expect(masterRefLabel(null, null)).toBe(MASTER_REF_NONE);
    expect(masterRefLabel(undefined, null)).toBe(MASTER_REF_NONE);
  });
  it('해석됐다 → CODE · 이름', () => {
    expect(masterRefLabel(HQ, { code: 'DEPT-HQ', name: '본사' })).toBe('DEPT-HQ · 본사');
  });
  it('🔴 못 찾았다 → 이름 확인 불가 (id 가 아니다)', () => {
    const out = masterRefLabel(GHOST, undefined);
    expect(out).toBe(MASTER_REF_UNRESOLVED);
    expect(UUID_RE.test(out)).toBe(false);
  });
  it('코드가 없으면 이름만', () => {
    expect(masterRefLabel(HQ, { code: null, name: '본사' })).toBe('본사');
  });
});
