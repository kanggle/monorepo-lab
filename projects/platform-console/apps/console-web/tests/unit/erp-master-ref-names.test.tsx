import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * 마스터 참조 컬럼 회귀 가드 (TASK-PC-FE-276 · **TASK-PC-FE-277 이 넓혔다**).
 *
 * 🔵 277 은 **새 가드를 짓지 않았다.** 술어(`[data-master-ref]` 셀에 UUID 정규식)와
 *    하한과 대조군이 그대로 적용되고, 늘어난 것은 **모집단**뿐이다 — erp-ops 밖의 두 칸
 *    (`ReplenishmentTable` 의 「창고」, `OrgNodeDetail` 의 「상위 노드」). 🔴 파일 이름은
 *    `erp-…` 로 남겨 둔다: 이름을 바꾸면 이 가드를 가리키는 세 곳(276 의 티켓 본문, 277 의
 *    AC-3, 이 파일 자신)이 동시에 안 낡는다는 보장이 없다. 이름이 아니라 이 주석이 범위다.
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

// 🔵 `OrgNodeDetail`(277 이 더한 칸)은 자기 화면의 하위 패널을 위해 조회 훅 7개를 부른다.
//    이 가드가 재는 것은 **`<dl>` 의 참조 칸**이므로 그 훅들은 조용한 기본값으로 막는다 —
//    실제 네트워크를 태우면 이 파일이 재는 축이 「조회가 되나」로 바뀐다.
const idleQuery = { data: undefined, isLoading: false, isError: false, error: null };
const idleMutation = { mutate: vi.fn(), isPending: false, isError: false, error: null };
vi.mock('@/features/org-hierarchy/hooks/use-org-nodes', () => ({
  useOrgNodeTenants: () => idleQuery,
  useOrgNodeAdmins: () => idleQuery,
  useSetCeiling: () => idleMutation,
  useUpdateOrgNode: () => idleMutation,
  useDeleteOrgNode: () => idleMutation,
  useGrantOrgAdmin: () => idleMutation,
  useRevokeOrgAdmin: () => idleMutation,
}));

import { DepartmentList } from '@/features/erp-ops/components/DepartmentList';
import { EmployeeList } from '@/features/erp-ops/components/EmployeeList';
import { CostCenterList } from '@/features/erp-ops/components/CostCenterList';
// TASK-PC-FE-277 이 더한 두 칸 — erp-ops 밖이다.
import { ReplenishmentTable } from '@/features/scm-replenishment/components/ReplenishmentTable';
import { OrgNodeDetail } from '@/features/org-hierarchy/components/OrgNodeDetail';
import { WmsInventoryDetailPanel } from '@/features/wms-ops/components/WmsInventoryDetailPanel';
import { WmsAsnDataTable } from '@/features/wms-ops/components/WmsAsnDataTable';
import { OutboundDrillLines } from '@/features/wms-outbound-ops/components/OutboundDrillLines';
import { WmsInventoryDataTable } from '@/features/wms-ops/components/WmsInventoryDataTable';
import { OrgAdminPanel } from '@/features/org-hierarchy/components/OrgAdminPanel';
import { OrgScopeDialogBody } from '@/features/operators/components/OrgScopeDialogBody';
import {
  codeName,
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

// ---------------------------------------------------------------------------
// TASK-PC-FE-277 — erp-ops 밖의 참조 칸. 🔵 술어도 하한도 위와 **같은 함수**다.
// ---------------------------------------------------------------------------

const WH = '01910000-0000-7000-8000-000000000001';
const WH_GHOST = '01910000-0000-7000-8000-0000000009ff';
const ROOT_NODE = '01a085a1-bc98-741e-ba56-21b896de1001';
const CHILD_NODE = '01a085a1-bc98-741e-ba56-21b896de1002';

const SUGGESTIONS = [
  // 해석됨 — 생산자가 `warehouseCode` 를 실어 준다(ADR-MONO-050 D9).
  { id: 's1', skuCode: 'SKU-BOX-001', warehouseId: WH, warehouseCode: 'WH01', suggestedQty: 10, status: 'SUGGESTED' },
  // 🔴 대조군 — BATCH 출처는 코드가 없다. `이름 확인 불가` 여야 하고 **UUID 는 안 된다**.
  { id: 's2', skuCode: 'SKU-BOX-002', warehouseId: WH_GHOST, warehouseCode: null, suggestedQty: 5, status: 'SUGGESTED' },
  // 🔴 대조군 — 참조가 아예 없는 행 → `—`.
  { id: 's3', skuCode: 'SKU-BOX-003', warehouseId: null, warehouseCode: null, suggestedQty: 1, status: 'SUGGESTED' },
];

const ORG_NODES = [
  { orgNodeId: ROOT_NODE, parentId: null, name: '본사', depth: 0, ceiling: { mode: 'UNBOUNDED' }, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
  { orgNodeId: CHILD_NODE, parentId: ROOT_NODE, name: '운영본부', depth: 1, ceiling: { mode: 'UNBOUNDED' }, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' },
];

function renderReplenishment() {
  return render(
    <ReplenishmentTable
      rows={SUGGESTIONS as never}
      queryPage={0}
      dataPage={0}
      totalPages={1}
      totalElements={SUGGESTIONS.length}
      onPrev={vi.fn()}
      onNext={vi.fn()}
      onAction={vi.fn()}
      actionPending={false}
    />,
    { wrapper: wrapper() },
  );
}

function renderOrgNode(which: 'root' | 'child') {
  const node = which === 'root' ? ORG_NODES[0] : ORG_NODES[1];
  return render(
    <OrgNodeDetail node={node as never} nodes={ORG_NODES as never} grantableRoles={null} />,
    { wrapper: wrapper() },
  );
}

describe('erp-ops 밖의 참조 칸도 같은 술어를 지킨다 (TASK-PC-FE-277)', () => {
  it('보충 추천 — 「창고」 칸', () => {
    const { container } = renderReplenishment();
    assertNoUuidInRefCells(container, 3, '보충 추천');
    expect(screen.getByText('WH01')).toBeTruthy();
  });

  it('🔴 대조군 — 코드가 없는 행은 `이름 확인 불가` 이고 **id 로 안 돌아간다**', () => {
    const { container } = renderReplenishment();
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    expect(cells).toContain(MASTER_REF_NONE);
    // 🔴 그 행의 id 는 **여전히 UUID 다** — 즉 이 대조군은 공허하지 않다.
    expect(UUID_RE.test(WH_GHOST)).toBe(true);
  });

  it('🔵 원본 id 는 사라지지 않는다 — `title` 로 옮겼을 뿐이다', () => {
    const { container } = renderReplenishment();
    const titles = refCells(container).map((el) => el.getAttribute('title'));
    expect(titles).toContain(WH);
    // 보이는 텍스트에는 없다.
    assertNoUuidInRefCells(container, 3, '보충 추천(title 확인)');
  });

  it('조직 노드 — 「상위 노드」 칸', () => {
    const { container } = renderOrgNode('child');
    assertNoUuidInRefCells(container, 1, '조직 노드');
    // 🔴 `screen.getByText('본사')` 로는 못 잰다 — 같은 화면의 재부모 `<select>` 가
    //    같은 이름을 옵션으로 갖고 있어서 «두 개» 를 찾는다. 참조 셀로 좁힌다.
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('본사');
  });

  it('🔴 대조군 — 루트 노드는 `(루트)` 다 (`—` 가 아니라 그 화면의 말이다)', () => {
    const { container } = renderOrgNode('root');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('(루트)');
    assertNoUuidInRefCells(container, 1, '조직 노드(루트)');
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-277 AC-2 — `OrgScopeDialogBody` 의 id 폴백.
//   🔴 기존 `OrgScopeDialog.test.tsx` 의 부서 픽스처는 id 가 `dept-sales` 라 **읽을 수
//      있다** — 그 스위트는 이 결함을 재현할 수 없는 입력 위에서 초록이었다. 여기서는
//      실제 화면에서 본 모양(UUID)을 쓴다.
// ---------------------------------------------------------------------------

const SALES = '01a085a1-bc98-741e-ba56-21b896de2001';
const SCOPE_GHOST = '01a085a1-bc98-741e-ba56-21b896de2fff';

function orgScopeForm(over: Record<string, unknown>) {
  return {
    assignmentsLoading: false,
    hasAssignment: true,
    currentSummary: null,
    currentScope: [SALES, SCOPE_GHOST],
    departments: [{ id: SALES, code: 'SALES', name: '영업본부' }],
    activeDepartments: [],
    deptsFailed: false,
    deptsLoading: false,
    mode: 'subset',
    setMode: vi.fn(),
    selected: [],
    toggleDept: vi.fn(),
    manual: '',
    setManual: vi.fn(),
    subsetIds: [],
    subsetEmpty: false,
    blockConfirmed: false,
    setBlockConfirmed: vi.fn(),
    reason: '',
    setReason: vi.fn(),
    reasonOk: false,
    canSubmit: false,
    isPending: false,
    submit: vi.fn(),
    submitError: null,
    ...over,
  };
}

function renderOrgScope(over: Record<string, unknown> = {}) {
  return render(
    <OrgScopeDialogBody
      f={orgScopeForm(over) as never}
      operatorLabel="이운영"
      titleId="t"
      descId="d"
      reasonId="r"
      manualId="m"
      onClose={vi.fn()}
    />,
    { wrapper: wrapper() },
  );
}

describe('조직 스코프 칩 — id 폴백을 걷어냈다 (TASK-PC-FE-277 AC-2)', () => {
  it('해석된 칩은 `CODE · 이름`, 못 찾은 칩은 `이름 확인 불가` — 둘 다 UUID 가 아니다', () => {
    const { container } = renderOrgScope();
    assertNoUuidInRefCells(container, 2, '조직 스코프');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('SALES · 영업본부');
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
  });

  it('🔵 원본 id 는 `title` 에 남는다', () => {
    const { container } = renderOrgScope();
    const titles = refCells(container).map((el) => el.getAttribute('title'));
    expect(titles).toContain(SALES);
    expect(titles).toContain(SCOPE_GHOST);
  });

  it('🔴🔴 대조군 — 부서 조회가 **통째로** 실패하면 id 를 그대로 그리고, 그 칸은 모집단 밖이다', () => {
    const { container } = renderOrgScope({ deptsFailed: true, departments: [] });
    // ① 그 상태에서 id 는 보이는 텍스트다 — 화면이 가진 유일한 진실이기 때문이다.
    expect(container.textContent).toContain(SALES);
    // ② 🔴 그런데 **마커가 없다** ⇒ 가드가 이 상태를 결함으로 오판하지 않는다.
    //    「못 찾았다」와 「조회원 자체가 없다」는 다른 사실이고, 마커는 그 구별을 싣는다.
    expect(refCells(container).length).toBe(0);
  });
});

describe('🔴🔴 bite — 이 술어가 실제로 문다 (주입 · 실행 · 물기를 각각 단언한다)', () => {
  // 🔴 소스를 런타임에 되돌릴 수는 없다. 그래서 **되돌린 마크업**을 직접 그려서
  //    술어에 먹인다 — 재는 것은 컴포넌트가 아니라 `assertNoUuidInRefCells` 자신이다.
  function Reverted() {
    return (
      <table>
        <tbody>
          <tr>
            <td data-master-ref="suggestion.warehouseId">{WH}</td>
          </tr>
        </tbody>
      </table>
    );
  }

  it('① 주입 — 되돌린 셀에 넣은 값이 정말 UUID 모양이다', () => {
    expect(UUID_RE.test(WH)).toBe(true);
  });

  it('② 실행 — 그 셀이 정말 모집단 안에 들어온다', () => {
    const { container } = render(<Reverted />, { wrapper: wrapper() });
    const cells = refCells(container);
    expect(cells.length).toBe(1);
    expect((cells[0].textContent ?? '').trim()).toBe(WH);
  });

  it('③ 물기 — 술어가 실패한다', () => {
    const { container } = render(<Reverted />, { wrapper: wrapper() });
    expect(() => assertNoUuidInRefCells(container, 1, 'bite')).toThrow();
  });

  it('🔴 하한도 문다 — 참조 셀이 하나도 없으면 초록이 아니다', () => {
    const { container } = render(<div />, { wrapper: wrapper() });
    expect(refCells(container).length).toBe(0);
    expect(() => assertNoUuidInRefCells(container, 1, 'bite-공허')).toThrow();
  });
});

// ---------------------------------------------------------------------------
// TASK-MONO-659 — wms 의 세 참조 칸. 🔵 술어도 하한도 위와 **같은 함수**다.
//
// 🔴 이 셋은 `TASK-PC-FE-277` 이 «콘솔에서 못 고친다» 로 분류했던 자리다 — 읽을 이름이
//    화면에 도착조차 하지 않았다. 659 가 생산자(admin-service · outbound-service)에
//    `warehouseCode` / `skuCode` 를 실어서 비로소 여기서 고칠 수 있게 됐다.
//    ⇒ 그래서 **하한이 3칸 늘어난다**(AC-3: "새 칸이 늘었는데 하한이 그대로면
//    비-공허성이 헐거워진다").
// ---------------------------------------------------------------------------

const WMS_WH = '01910000-0000-7000-8000-000000000001';
const WMS_WH_GHOST = '01910000-0000-7000-8000-0000000009fe';
const WMS_SKU = '01910000-0000-7000-8000-000000000403';

describe('wms 의 참조 칸도 같은 술어를 지킨다 (TASK-MONO-659)', () => {
  it('재고 상세 — 「창고」 칸', () => {
    const { container } = render(
      <WmsInventoryDetailPanel
        selected={{ locationId: 'L', skuId: 'S', lotId: null } as never}
        loading={false}
        forbidden={false}
        notFound={false}
        degraded={false}
        data={{
          locationId: 'L',
          skuId: WMS_SKU,
          lotId: null,
          warehouseId: WMS_WH,
          warehouseCode: 'WH01',
          availableQty: 5,
        } as never}
      />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 1, 'wms 재고 상세');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('WH01');
    // 🔵 원본 id 는 사라지지 않았다 — `title` 로 옮겼을 뿐이다.
    expect(refCells(container).map((el) => el.getAttribute('title'))).toContain(WMS_WH);
  });

  it('🔴 대조군 — 코드가 없으면 `이름 확인 불가` 이고 **id 로 안 돌아간다**', () => {
    const { container } = render(
      <WmsInventoryDetailPanel
        selected={{ locationId: 'L', skuId: 'S', lotId: null } as never}
        loading={false}
        forbidden={false}
        notFound={false}
        degraded={false}
        data={{
          locationId: 'L',
          skuId: WMS_SKU,
          lotId: null,
          warehouseId: WMS_WH_GHOST,
          warehouseCode: null,
          availableQty: 5,
        } as never}
      />,
      { wrapper: wrapper() },
    );
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    // 🔴 그 행의 id 는 여전히 UUID 다 — 이 대조군은 공허하지 않다.
    expect(UUID_RE.test(WMS_WH_GHOST)).toBe(true);
    assertNoUuidInRefCells(container, 1, 'wms 재고 상세(미해석)');
  });

  it('ASN 목록 — 「창고」 칸 (해석 + 미해석 + 없음)', () => {
    const { container } = render(
      <WmsAsnDataTable
        data={{
          content: [
            { asnId: 'a1', asnNo: 'ASN-1', warehouseId: WMS_WH, warehouseCode: 'WH01', status: 'CREATED' },
            { asnId: 'a2', asnNo: 'ASN-2', warehouseId: WMS_WH_GHOST, warehouseCode: null, status: 'CREATED' },
            { asnId: 'a3', asnNo: 'ASN-3', warehouseId: null, warehouseCode: null, status: 'CREATED' },
          ],
          page: { totalPages: 1, totalElements: 3, number: 0, size: 20 },
        } as never}
        query={{ page: 0 } as never}
        onPrevPage={vi.fn()}
        onNextPage={vi.fn()}
        onInspect={vi.fn()}
      />,
      { wrapper: wrapper() },
    );
    // 세 행이 세 상태를 덮는다 ⇒ 하한 3.
    assertNoUuidInRefCells(container, 3, 'wms ASN 목록');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('WH01');
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    expect(cells).toContain(MASTER_REF_NONE);
  });

  it('출고 주문 라인 — 「SKU」 칸', () => {
    const { container } = render(
      <OutboundDrillLines
        lines={[
          { orderLineId: 'ol1', lineNo: 1, skuId: WMS_SKU, skuCode: 'SKU-BOX-001', qtyOrdered: 3 },
          // 🔴 대조군 — 백필 전에 만들어진 주문은 `skuCode` 가 null 이다.
          { orderLineId: 'ol2', lineNo: 2, skuId: WMS_WH_GHOST, skuCode: null, qtyOrdered: 1 },
        ] as never}
      />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 2, 'wms 출고 라인');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('SKU-BOX-001');
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
  });
});

// ---------------------------------------------------------------------------
// TASK-PC-FE-281 — `{code ?? id}` 로 **id 로 되돌아가던** 마스터 참조 7칸.
//
// 🔴 659 가 「창고」만 고쳐서 같은 패널이 같은 상황에 두 말을 하고 있었다:
//    창고=`이름 확인 불가` / 위치·SKU·로트=raw UUID.
// 🔴🔴 그리고 **그 행 자신의 업무 번호 3칸은 제외했다** — 마지막 describe 가
//    그 셋이 모집단 **밖**임을 단언한다(안 하면 나중에 마커가 붙어도 아무도 모른다).
// ---------------------------------------------------------------------------

const P_LOC = '01910000-0000-7000-8000-000000001001';
const P_SKU = '01910000-0000-7000-8000-000000000403';
const P_LOT = '01910000-0000-7000-8000-0000000007aa';
const P_SUP = '01910000-0000-7000-8000-0000000002fe';

function inventoryRows() {
  return [
    // 해석됨
    { locationId: P_LOC, skuId: P_SKU, lotId: P_LOT, warehouseId: WMS_WH, warehouseCode: 'WH01',
      locationCode: 'A-01-01', skuCode: 'SKU-BOX-001', lotNo: 'LOT-77', availableQty: 5 },
    // 🔴 미해석 — 코드가 null 이다. 여기서 UUID 가 보이면 이 티켓의 결함이 살아 있다.
    { locationId: P_LOC, skuId: P_SKU, lotId: P_LOT, warehouseId: WMS_WH, warehouseCode: null,
      locationCode: null, skuCode: null, lotNo: null, availableQty: 1 },
    // 참조 없음 — lotId 가 null ⇒ `—`
    { locationId: P_LOC, skuId: P_SKU, lotId: null, warehouseId: WMS_WH, warehouseCode: 'WH01',
      locationCode: 'A-01-02', skuCode: 'SKU-BOX-002', lotNo: null, availableQty: 2 },
  ];
}

function renderInventoryTable() {
  return render(
    <WmsInventoryDataTable
      data={{ content: inventoryRows(), page: { totalPages: 1, totalElements: 3, number: 0, size: 20 } } as never}
      query={{ page: 0 } as never}
      onPrevPage={vi.fn()}
      onNextPage={vi.fn()}
      onSelect={vi.fn()}
    />,
    { wrapper: wrapper() },
  );
}

describe('id 폴백을 걷어낸다 — wms 마스터 참조 7칸 (TASK-PC-FE-281)', () => {
  it('재고 목록 — 위치·SKU·로트가 UUID 로 안 돌아간다', () => {
    const { container } = renderInventoryTable();
    // 3행 × 3칸(+창고 3) ⇒ 하한을 넉넉히 잡지 않고 실제 수로 잡는다.
    assertNoUuidInRefCells(container, 9, 'wms 재고 목록');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('A-01-01');
    expect(cells).toContain('SKU-BOX-001');
    expect(cells).toContain('LOT-77');
    // 🔴 미해석 행 — `이름 확인 불가` 이고 UUID 가 아니다.
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    // 참조가 아예 없는 칸 — `—`
    expect(cells).toContain(MASTER_REF_NONE);
  });

  it('🔵 원본 id 는 사라지지 않았다 — `title` 로 옮겼을 뿐이다', () => {
    const { container } = renderInventoryTable();
    const titles = refCells(container).map((el) => el.getAttribute('title'));
    expect(titles).toContain(P_LOC);
    expect(titles).toContain(P_SKU);
    // 보이는 텍스트에는 없다.
    assertNoUuidInRefCells(container, 9, 'wms 재고 목록(title 확인)');
  });

  it('재고 상세 — 위치·SKU·로트도 같은 술어를 지킨다', () => {
    const { container } = render(
      <WmsInventoryDetailPanel
        selected={{ locationId: P_LOC, skuId: P_SKU, lotId: P_LOT } as never}
        loading={false}
        forbidden={false}
        notFound={false}
        degraded={false}
        data={{ locationId: P_LOC, skuId: P_SKU, lotId: P_LOT, warehouseId: WMS_WH,
                warehouseCode: null, locationCode: null, skuCode: null, lotNo: null,
                availableQty: 5 } as never}
      />,
      { wrapper: wrapper() },
    );
    // 창고 + 위치 + SKU + 로트 = 4칸, 전부 미해석이다.
    assertNoUuidInRefCells(container, 4, 'wms 재고 상세(전부 미해석)');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells.filter((c) => c === MASTER_REF_UNRESOLVED).length).toBe(4);
  });

  it('ASN 목록 — 공급사 칸이 이름을 그리고, 없으면 UUID 가 아니다', () => {
    const { container } = render(
      <WmsAsnDataTable
        data={{
          content: [
            { asnId: 'a1', asnNo: 'ASN-1', warehouseId: WMS_WH, warehouseCode: 'WH01',
              supplierPartnerId: P_SUP, supplierName: '한빛물산', status: 'CREATED' },
            { asnId: 'a2', asnNo: 'ASN-2', warehouseId: WMS_WH, warehouseCode: 'WH01',
              supplierPartnerId: P_SUP, supplierName: null, status: 'CREATED' },
          ],
          page: { totalPages: 1, totalElements: 2, number: 0, size: 20 },
        } as never}
        query={{ page: 0 } as never}
        onPrevPage={vi.fn()}
        onNextPage={vi.fn()}
        onInspect={vi.fn()}
      />,
      { wrapper: wrapper() },
    );
    assertNoUuidInRefCells(container, 4, 'wms ASN 공급사');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('한빛물산');
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
  });
});

// ---------------------------------------------------------------------------
// 🔴🔴 TASK-PC-FE-281 § 제외 — **그 행 자신의 업무 번호는 모집단 밖이다**
//
// `asnNo ?? asnId` · `orderNo ?? orderId` 는 다른 엔티티를 가리키는 참조가 아니다.
// 해석할 «이름» 이 없으므로 `이름 확인 불가` 로 바꾸면 **그 행을 지목할 방법이
// 화면에서 사라진다.** ⇒ UUID 폴백이 옳고, `data-master-ref` 를 달지 않는다.
// 🔵 이 칸이 없으면 누군가 «일관성» 을 이유로 마커를 달았을 때 아무도 모른다.
// ---------------------------------------------------------------------------
describe('🔴 제외한 칸은 모집단 밖이다 (TASK-PC-FE-281 § 제외)', () => {
  it('ASN 번호 칸은 `data-master-ref` 를 달지 않는다 — UUID 폴백이 의도다', () => {
    const { container } = render(
      <WmsAsnDataTable
        data={{
          content: [
            // 🔴 asnNo 가 null 이라 UUID 가 **보이는 텍스트로** 나온다. 그것이 의도다.
            { asnId: P_SUP, asnNo: null, warehouseId: WMS_WH, warehouseCode: 'WH01',
              supplierPartnerId: P_SUP, supplierName: '한빛물산', status: 'CREATED' },
          ],
          page: { totalPages: 1, totalElements: 1, number: 0, size: 20 },
        } as never}
        query={{ page: 0 } as never}
        onPrevPage={vi.fn()}
        onNextPage={vi.fn()}
        onInspect={vi.fn()}
      />,
      { wrapper: wrapper() },
    );
    // (1) 그 UUID 가 화면에 실제로 있다 — 이 대조군은 공허하지 않다.
    expect(container.textContent).toContain(P_SUP);
    // (2) 그런데 참조 셀(모집단) 안에서는 UUID 가 0건이다 ⇒ 그 칸은 모집단 밖이다.
    assertNoUuidInRefCells(container, 2, '제외 칸(ASN 번호)');
  });
});

// ---------------------------------------------------------------------------
// TASK-MONO-670 / ADR-MONO-073 ⓐ — org-admin 행의 운영자 칸.
//
// 🔴 `TASK-PC-FE-277` 이 「콘솔에서 못 고치는 부류」로 분류했던 자리다 — 생산자가
//    `displayName` 을 싣기 전에는 화면이 할 수 있는 것이 없었다. 이제 싣는다.
// ---------------------------------------------------------------------------

const OA_OP = '01a085a1-bc98-741e-ba56-21b896de3001';
const OA_GHOST = '01a085a1-bc98-741e-ba56-21b896de3fff';

function renderOrgAdmins(admins: unknown[]) {
  return render(
    <OrgAdminPanel
      node={{ orgNodeId: 'biz', parentId: null, name: '사업본부', depth: 0,
              ceiling: { mode: 'UNBOUNDED' }, createdAt: '2026-01-01T00:00:00Z',
              updatedAt: '2026-01-01T00:00:00Z' } as never}
      admins={admins as never}
      adminsLoading={false}
      adminsError={null}
      grantableRoles={['ORG_ADMIN']}
      onGrant={vi.fn()}
      onRevoke={vi.fn()}
      grantPending={false}
      grantError={null}
      revokePending={false}
      revokeError={null}
    />,
    { wrapper: wrapper() },
  );
}

describe('org-admin 행의 운영자 칸 (TASK-MONO-670)', () => {
  it('표시명이 오면 그것을 그리고, UUID 로 안 돌아간다', () => {
    const { container } = renderOrgAdmins([
      { operatorId: OA_OP, displayName: '김운영', roleName: 'ORG_ADMIN',
        grantedAt: '2026-07-10T09:00:00Z' },
      // 🔴 대조군 — 생산자가 못 찾으면 null 이다.
      { operatorId: OA_GHOST, displayName: null, roleName: 'ORG_ADMIN',
        grantedAt: '2026-07-10T09:00:00Z' },
    ]);

    assertNoUuidInRefCells(container, 2, 'org-admin 운영자 칸');
    const cells = refCells(container).map((el) => (el.textContent ?? '').trim());
    expect(cells).toContain('김운영');
    expect(cells).toContain(MASTER_REF_UNRESOLVED);
    // 🔵 그 행의 id 는 여전히 UUID 다 ⇒ 대조군이 공허하지 않다.
    expect(UUID_RE.test(OA_GHOST)).toBe(true);
    // 🔵 원본 id 는 사라지지 않았다 — `title` 로 옮겼을 뿐이다.
    expect(refCells(container).map((el) => el.getAttribute('title'))).toContain(OA_OP);
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
  it('🔴 이름이 없으면 코드만 — 꼬리 ` · ` 가 남지 않는다 (TASK-PC-FE-277)', () => {
    // scm 의 `reorder_suggestion` 은 코드만 싣는다(ADR-MONO-050 D9). 276 의 호출부는
    // 전부 양쪽을 갖고 있어서 이 입력이 처음 생긴 것이 277 이다.
    expect(codeName({ code: 'WH01' })).toBe('WH01');
    expect(codeName({ code: 'WH01', name: null })).toBe('WH01');
    expect(masterRefLabel(HQ, { code: 'WH01' })).toBe('WH01');
    // 🔵 양쪽이 다 있는 기존 출력은 안 바뀐다 — 이것이 이 변경의 대조군이다.
    expect(codeName({ code: 'DEPT-HQ', name: '본사' })).toBe('DEPT-HQ · 본사');
  });
});
