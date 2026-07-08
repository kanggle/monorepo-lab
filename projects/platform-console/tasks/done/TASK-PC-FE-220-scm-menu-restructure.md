# TASK-PC-FE-220 — SCM 콘솔 메뉴 재구성 (개요 슬림화 + 조달·재고 분리 + 보충 계획 명칭 정리)

**Status:** done
**Area:** platform-console / console-web · **Routes:** `/scm/procurement`(신규) · `/scm/inventory`(신규) · `/scm`(슬림화) · **Nav:** SCM drill 자식 재구성 (개요 · 가이드 · 조달 · 재고 · 보충 계획 · 보충 계획 설정)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (다-파일 화면/상태 분할 + 테스트 재편)

---

## Goal

SCM 콘솔 메뉴를 도메인 경계에 맞게 재구성한다. 현재 **개요(`/scm`)** 한 화면이 성격이 다른 두 기능(조달 발주 목록 + 재고 가시성 스냅샷/SKU/staleness)을 한꺼번에 얹고 있어, 개요를 "전반 요약"만 남기고 조달·재고를 각각 별도 메뉴로 분리한다. 또한 단일 백엔드 기능(demand-planning)에서 온 두 메뉴 `보충`/`설정`의 명칭을 기능 관계가 드러나도록 `보충 계획`/`보충 계획 설정`으로 정리한다.

목표 메뉴(순서): **개요 · 가이드 · 조달 · 재고 · 보충 계획 · 보충 계획 설정**.

배경: 개요는 이미 `getScmOverviewState` 팬아웃(발주/재고 카운트·PO 상태 분포·최근 발주)을 렌더하는 `ScmOverview` 밴드를 갖고 있다(TASK-PC-FE-167). 지금은 그 밴드 **아래에** `ScmOpsScreen`이 발주 테이블 + 스냅샷/SKU/staleness 테이블을 함께 렌더한다(TASK-PC-FE-008/144). 이 테이블들을 개요에서 떼어 조달·재고 두 라우트로 옮긴다.

## Scope

**Nav** `shared/ui/ConsoleSidebarNav.tsx` — SCM `children`을 다음으로 재구성 (testid 규약 유지):
- `{ '/scm', 개요, nav-scm-ops }` (유지)
- `{ '/scm/guide', 가이드, nav-scm-guide }` (유지)
- `{ '/scm/procurement', 조달, nav-scm-procurement }` (신규)
- `{ '/scm/inventory', 재고, nav-scm-inventory }` (신규)
- `{ '/scm/replenishment', 보충 계획, nav-scm-replenishment }` (라벨 변경, href/testid 유지)
- `{ '/scm/config', 보충 계획 설정, nav-scm-config }` (라벨 변경, href/testid 유지)

**상태 분할** `features/scm-ops/api/scm-state.ts` — 기존 `getScmSectionState`(po+snapshot+staleness 한 번에)를 목적별 둘로 분할:
- `getScmProcurementState(eligible)` → `{ poList, notEligible, forbidden, rateLimited, degraded }`
- `getScmInventoryState(eligible)` → `{ snapshot, staleness, notEligible, forbidden, rateLimited, degraded }`
- 동일 resilience(401 재로그인 redirect / 403 forbidden / 429 rateLimited / 503·timeout degraded)를 공유 헬퍼로 유지. S5 `meta.warning`은 snapshot 뷰모델로 그대로 통과.

**화면 분할** `features/scm-ops/components/` — `ScmOpsScreen`(개요+조달+재고 합본)을 둘로 분할:
- `ScmProcurementScreen.tsx` — 발주 필터/페이지네이션 상태 + `ScmPoTable` + `PoDetailDialog` (읽기 전용, 기존 로직 이관).
- `ScmInventoryScreen.tsx` — 스냅샷 행 정규화 + SKU 조회 상태 + `ScmSnapshotTable` + `ScmSkuBreakdown` + `ScmStalenessTable` (읽기 전용, 기존 로직 이관, S5 상시 노출 유지).
- `ScmOpsScreen.tsx` 제거(합본 더 이상 미사용). `ScmSnapshotTable`/`ScmSkuBreakdown`/`ScmStalenessTable`/`ScmPoTable`/`PoDetailDialog`/`scm-ops-helpers` 재사용.

**라우트**:
- `app/(console)/scm/page.tsx`(개요) — `ScmOpsScreen` 제거, `getScmSectionState` 제거. eligibility preflight + `getScmOverviewState` 후 `ScmOverview` 밴드만 렌더(전반 요약). notEligible/registryDegraded 인라인 상태 유지.
- `app/(console)/scm/procurement/page.tsx`(신규) — eligibility preflight + `getScmProcurementState` → `ScmProcurementScreen`. 개요와 동일한 인라인 상태(notEligible/forbidden/rateLimited/degraded).
- `app/(console)/scm/inventory/page.tsx`(신규) — eligibility preflight + `getScmInventoryState` → `ScmInventoryScreen`.

**barrel** `features/scm-ops/index.ts` — `ScmProcurementScreen`/`ScmInventoryScreen`/`getScmProcurementState`/`getScmInventoryState` export 추가; `ScmOpsScreen`/`getScmSectionState`/`ScmSectionState` 제거.

**가이드 데이터** `features/scm-guide/data.ts` — 화면 배치 참조 텍스트(개요=발주+재고 → 조달/재고 분리, 보충/설정 → 보충 계획/보충 계획 설정)를 새 메뉴에 맞게 갱신(구조 무변경, 설명 텍스트만).

**테스트**:
- `ScmOpsScreen.test.tsx` → `ScmProcurementScreen.test.tsx` + `ScmInventoryScreen.test.tsx`로 분할(기존 케이스 이관: PO 필터/페이지네이션/상세 다이얼로그/읽기전용 · 스냅샷/SKU/staleness/S5/degrade/a11y).
- `scm-state.test.ts` → 두 신규 함수의 eligibility/resilience 케이스로 재작성.
- `scm-nav.test.tsx` — 합본 대신 신규 화면(또는 `ScmOverview`) 마운트로 갱신, 카탈로그 라우팅 additive 단언 유지.
- nav 테스트(`sidebar-drilldown` · `scm-guide-nav` · `replenishment-nav` · `config-nav`) — 신규 조달/재고 testid + 보충 계획/보충 계획 설정 라벨 반영.

**Out of scope:** 백엔드/producer/contract 무변경. 발주 쓰기(제출/확정/취소) 도입 없음 — 콘솔은 계속 읽기 전용. 권한 모델 무변경. 개요 밴드(`ScmOverview`) 내부 로직 무변경.

## Acceptance Criteria
- **AC-1** SCM drill을 열면 자식이 **개요 · 가이드 · 조달 · 재고 · 보충 계획 · 보충 계획 설정** 순으로 보인다. 조달=`nav-scm-procurement`→`/scm/procurement`, 재고=`nav-scm-inventory`→`/scm/inventory`. `보충 계획`=`nav-scm-replenishment`(href `/scm/replenishment` 유지), `보충 계획 설정`=`nav-scm-config`(href `/scm/config` 유지). 각 딥링크 시 SCM drill 자동 오픈 + 해당 자식 active(longest-match).
- **AC-2** 개요(`/scm`)는 `ScmOverview` 밴드(카운트 타일·PO 상태 분포·최근 발주)만 렌더한다. 발주 테이블/스냅샷/SKU/staleness 테이블은 개요에 **없다**. notEligible/registryDegraded 인라인 상태 유지.
- **AC-3** 조달(`/scm/procurement`)은 발주 목록(필터·페이지네이션·읽기전용 상세 다이얼로그)을 렌더한다. 쓰기 어포던스 없음(제출/확정/취소 버튼 부재). 403/429/503 인라인 degrade, 401 재로그인.
- **AC-4** 재고(`/scm/inventory`)는 스냅샷 테이블 + SKU별 분해 + 노드 staleness를 렌더하고, 모든 재고 가시성 뷰에 S5 경고를 상시 노출(never stripped). 403/429/503 인라인 degrade, 401 재로그인.
- **AC-5** `getScmProcurementState`/`getScmInventoryState`가 eligibility 게이트(비적격 시 scm 호출 미발생) + 401/403/429/503 resilience를 각각 만족한다.
- **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest run` green. 분할된 화면/상태/nav 테스트 통과, 기존 catalog-routing additive 회귀 없음.

## Edge Cases / Failure Scenarios
- 개요는 조달/재고 테이블 제거 후에도 `ScmOverview`의 per-cell degrade로 부분 장애를 흡수(밴드 자체 유지).
- 조달/재고 라우트는 개요와 동일한 eligibility preflight(레지스트리 `getCatalog`)를 각자 수행 — 비적격 운영자에게 cross-tenant 호출을 만들지 않음(§ 2.4.6).
- `/scm/inventory`는 `/scm`의 prefix가 아니므로 longest-match active가 개요와 충돌하지 않아야 함(nav active 단언).
- 라벨만 바뀐 보충 계획/보충 계획 설정은 href·testid 불변 → 기존 딥링크/프록시 라우트 회귀 없음.
- 가이드는 정적이라 배치 변경과 무관하게 항상 열림(텍스트만 새 메뉴명 반영).

## Related
- 분할 대상: `features/scm-ops/`(TASK-PC-FE-008 read section · TASK-PC-FE-144 god-file split · TASK-PC-FE-167 overview snapshot).
- 미러 패턴(drill 다중 자식 + 화면별 라우트 분리): WMS(`/wms` 개요 · `/wms/inventory` · `/wms/outbound`) · E-Commerce(`/ecommerce` + 상품/주문/…).
- 가이드 카피 동반 갱신: `features/scm-guide/data.ts`(TASK-PC-FE-188).
- 연계 ADR: ADR-MONO-013(콘솔 federation Phase 4 slice 2 — scm).
