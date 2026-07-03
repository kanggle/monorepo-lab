# TASK-PC-FE-170 — wms 개요: 부적합한 "알림" 카운트 타일 제거

**Status:** done
**Area:** platform-console / console-web · **Route:** `app/(console)/wms/page.tsx` (개요 밴드)
**Follows:** TASK-PC-FE-166 (wms 개요 스냅샷 최초 구현) — 그 구현의 개요-적합성 정리.
**Analysis model:** Opus 4.8 · **Impl model 권장:** Sonnet (프런트 표현 정리 + 테스트 조정, 도메인 판단 없음).
**Implemented:** branch `task/pc-fe-170-wms-overview-drop-alerts-tile`. `pnpm lint` + `tsc --noEmit` + `vitest`(wms 79/79) green.
**Merged:** PR #2156 (squash `a448c2f`, 2026-07-03) — CI 전체 GREEN(failing 0, mergeStateStatus CLEAN) 확인 후 머지.

## Goal

WMS 개요(`WmsOverview`)의 카운트 타일 3개(재고 / 배송 / **알림**) 중 **"알림" 타일**을 제거한다.
"알림"은 개요의 카운트-타일 자리(운영 규모 스냅샷)에 어울리지 않는 항목이기 때문이다:

1. **성격 불일치** — 재고·배송은 WMS가 관리하는 *업무 객체*라 총건수(규모) 스냅샷이 의미가 있다(자매
   도메인도 동일: SCM=발주/재고, ERP=부서/직원/직급/원가센터/거래처 — 전부 업무·마스터 객체). 반면 **알림은
   파생된 "주의 신호" 스트림**으로, "총 몇 건인가"는 규모 지표로서 의미가 약하고 재고/배송과 같은 층위에 세우기
   이질적이다.
2. **바로 아래 "알림 상태" 섹션과 중복** — 알림 타일 총계 = (미확인 + 확인) 합. 같은 수치를 타일과 분포에서 두 번
   보여준다. `overview-state.ts`는 `listAlerts`를 총계·미확인·확인 3회 호출하는데, 총계 호출은 나머지 둘의 합이라
   잉여 팬아웃이다.
3. **글랜스에서 의미 있는 건 미확인** — 개요 원칙은 *읽기 전용 + 주의 필요 항목을 한눈에*. 실제 조치 대상인
   **미확인**은 "알림 상태" 섹션에 이미 있으므로, 알림 정보는 그 섹션 하나에 두는 것이 옳다.

## Scope

- **MODIFY** `features/wms-ops/api/overview-state.ts`
  - 카운트 팬아웃에서 총계 알림 레그(`cell(listAlerts({ page: 0, size: 1 }))`) 제거.
  - `counts` = `[재고, 배송]` 2개만.
  - `alertStatus`(미확인/확인 = 유일한 알림 표현)와 `recentShipments`는 그대로. 팬아웃 6→5 레그로 감소.
- **MODIFY** `features/wms-ops/components/WmsOverview.tsx`
  - 문서주석을 (재고/배송) 타일 + 알림은 "알림 상태" 분포로만 표현으로 갱신. 렌더 로직은 `state.counts`를 그대로
    map 하므로 컴포넌트 구조 변경 없음.
- **MODIFY** tests `wms-overview-state.test.ts`, `wms-overview.test.tsx`
  - `baseState`/`seedHappy`의 알림 카운트 타일 기대치 및 `byKey.alerts` 단언 제거. 알림 상태(미확인/확인) + 최근
    출고 커버리지는 유지. 총계 알림 레그가 더 이상 호출되지 않음을 단언(회귀 가드).

## Out of Scope (의도적 유지)

- **"알림 상태"(미확인/확인) 섹션 유지** — 알림 정보의 적절한 전용 위치. 확인(처리완료) 버킷도 그대로 둔다(개요에서
  분포의 한 축으로는 정상 정보이며, 문제였던 것은 "카운트 타일" 승격이었다). 전체 알림 테이블은 개요 아래
  `WmsOpsScreen`에 그대로 있다.
- WmsOpsScreen / page.tsx 로직 무변경(개요는 여전히 슬롯으로 전달).

## Acceptance Criteria

- [x] **AC-1** `/wms` 개요 카운트 타일은 재고·배송 2개만 렌더된다. `wms-alerts-count`(및 `-degraded`) testid 미출력.
- [x] **AC-2** `getWmsOverviewState`는 총계 알림용 `listAlerts({page:0,size:1})`(무필터)를 호출하지 않는다.
  미확인(`acknowledged:false`)·확인(`acknowledged:true`) 레그와 재고/배송/최근출고 레그는 유지.
- [x] **AC-3** "알림 상태"(미확인/확인) 분포와 "최근 출고" 글랜스는 기존과 동일하게 렌더된다.
- [x] **AC-4** 재고/배송 카운트의 per-cell 회복탄력성(403→권한 없음, 503/timeout→점검 필요, 401→whole-session
  redirect)은 회귀 없음.
- [x] **AC-5** `pnpm lint` + `tsc --noEmit` + `vitest`(대상: `wms-overview-state`, `wms-overview`) green.

## Related Specs / Contracts

- `specs/contracts/console-integration-contract.md` § 2.4.5.2 (wms 개요 스냅샷) — 카운트 타일 집합에서 알림 제외로 축소
  (알림은 상태 분포에서만 표현). 계약 문구 갱신 필요 시 함께 반영.
- `specs/services/console-web/architecture.md` § 도메인 랜딩 운영 개요 스냅샷 — wms 카운트 타일 재고/배송 명시.

## Edge Cases

- 알림 상태 레그(미확인/확인)가 degraded → 분포는 "—" 표시(기존 동작 유지, 카운트 타일 제거와 무관).
- 재고 또는 배송 단일 레그 degrade/forbidden → 해당 타일만 placeholder, 나머지 정상(회귀 없음).

## Failure Scenarios

- 총계 알림 레그를 지웠으나 컴포넌트가 여전히 `alerts` 타일을 기대 → `state.counts`를 map 하므로 배열에서 빠지면
  자동 미출력(하드코딩 없음). 테스트가 미출력을 단언해 가드.
- 실수로 미확인/확인 레그까지 제거 → "알림 상태" 섹션 공백. 테스트(미확인/확인 렌더 단언)가 가드.
