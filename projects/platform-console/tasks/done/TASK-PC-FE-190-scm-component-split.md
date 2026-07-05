# TASK-PC-FE-190 — SCM god-file 컴포넌트 분할 (보충·설정 화면·폼 프레젠테이션 조각화)

**Status:** done
**Area:** platform-console / console-web · **Refactor:** behavior-preserving component split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (렌더 구조 정리 — 동작 불변)

---

## Goal

SCM 콘솔 화면의 큰 파일을 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 프레젠테이션 조각으로 분할한다. 동작·마크업·testid 불변, 순수 구조 리팩토링. 선행 TASK-PC-FE-189(client dedup) 위에 얹는다.

대상(라인수):
- `scm-replenishment/components/ReplenishmentScreen.tsx` (461) — list+filter+action-dialog+approve-success affordance+table+pagination이 한 파일. 오케스트레이션은 유지하되 프레젠테이션 조각 추출.
- `scm-config/components/{PolicyForm,SupplierMapForm}.tsx` (303/385) — 공유 seed-form scaffold(라벨·숫자입력·검증·에러 표시) 추출 후 두 폼이 소비.
- (여력 시) `scm-ops/components/ScmPoTable.tsx` (240) — row/detail 조각 정리.

## Scope

**보충** `scm-replenishment/components/`:
- `ReplenishmentScreen.tsx` = 컨테이너(쿼리·필터 state·action 오케스트레이션)로 축소.
- 추출: `ReplenishmentTable`(행 렌더 + 작업 버튼 gating) · `ReplenishmentFilters`(status/skuCode form) · `ApprovedDraftBanner`(승인 성공 DRAFT PO affordance). 기존 testid(`repl-*`, `repl-row-*`, `repl-approved-*`) 전부 보존.

**설정** `scm-config/components/`:
- `PolicyForm`·`SupplierMapForm`이 각자 byte-identical하게 중복 정의하던 로컬 `NumberField`를 공유 `SeedNumberField`로 추출. 두 폼이 그것을 소비. 기존 testid(`<testid>`·`<testid>-error`)·검증 메시지·submit·404-empty 동작 보존.

**Out of scope:** API/hook/proxy/producer/contract 무변경. 새 기능 0. 권한·데이터 흐름 불변.

## Acceptance Criteria
- **AC-1** 보충 화면: 기존 마크업·testid·동작(필터·페이지네이션·승인/기각 다이얼로그·DRAFT PO affordance·degraded/forbidden/ratelimited/empty 분기) 불변. `ReplenishmentScreen.test.tsx`·`replenishment-nav.test.tsx` 무수정 통과.
- **AC-2** 설정 화면: 두 폼의 검증·에러·submit·404-empty 동작 불변. `SeedConfigScreen.test.tsx` 무수정 통과.
- **AC-3** 각 분할된 조각은 순수 프레젠테이션(가능한 한 `'use client'` 최소화) + props 계약 명확.
- **AC-4** `tsc --noEmit` + `next lint` + `vitest run` green. 기존 SCM 테스트 전부 통과(회귀 0). First Load byte 회귀 없음(barrel import 주의 — [[project_console_first_load_barrel_rsc_optimization]]).

## Edge Cases / Failure Scenarios
- 분할 시 `'use client'` 경계 이동으로 서버/클라 렌더 오염 금지 — 조각은 부모의 client 경계 안에 둔다.
- testid/aria/문구는 화면 테스트가 pin — 하나라도 바뀌면 RED. 순수 이동만.
- barrel(`index.ts`) 재수출이 RSC First Load에 전체-feature-load를 유발하지 않도록 leaf import 유지.

## Related
- 선행: TASK-PC-FE-189 (scm-gateway client dedup).
- 방법론: [[project_console_web_godfile_split_series]] (PC-FE-098~153) · [[project_console_first_load_barrel_rsc_optimization]].
- 기존 테스트(계약): `tests/unit/{ReplenishmentScreen,SeedConfigScreen,replenishment-nav}.test.tsx`.
