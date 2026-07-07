# TASK-PC-FE-214 — wms-outbound-ops OutboundOpsScreen god-file 분할

**Status:** done
**Done:** 2026-07-07 · impl PR #2299 squash `e575f1964` (3-dim verified) — OutboundOpsScreen 383→228, 3 dialog-생명주기 hook 추출(PC-FE-196), tsc 0·lint 0·vitest 57/57.
**Area:** platform-console / console-web · **Refactor:** behavior-preserving component split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (WMS 분할 시리즈 선례 정합 — testid/markup/aria/dialog focus-trap 보존)

---

## Goal

WMS의 다른 스크린들은 PC-FE 분할 시리즈에서 Screen/DataTable/Filters/Drill 서브컴포넌트로 분해됐으나, **오케스트레이터 스크린 `wms-outbound-ops/components/OutboundOpsScreen.tsx`(383줄)만 미분할**로 남아 남은 최대 컴포넌트 god-file이다(3 도메인 통틀어). 분할 시리즈(PC-FE-098~212)와 동일 휴리스틱으로 프레젠테이셔널 조각을 추출해 오케스트레이터를 얇게 만든다.

## Scope

`wms-outbound-ops/components/OutboundOpsScreen.tsx`를 응집 단위로 분해:
- 필터/탭 바, 데이터 테이블(행 컴포넌트 포함), 드릴/상세 패널, 액션(mutation) 핸들러 묶음 등 자연 경계로 leaf 컴포넌트 추출.
- 오케스트레이터는 상태 배선 + 서브컴포넌트 조립만 남긴다(목표 ≤ ~180줄, 강제 아님 — 자연 경계 우선).
- dialog/포커스-트랩 로직이 있으면 **container에 유지**(PC-FE 분할 규약: open-reset/auto-focus effect·Escape/Tab querySelectorAll trap·dialogRef/reasonRef는 컨테이너 소유 — leaf로 내리지 말 것).
- 폼이 flat이면 hook-only(PC-FE-196 선례 — 불필요한 prop-drilling 강제 금지).

**Out of scope:** api/상태/hook 로직·엔드포인트·proxy·contract 무변경. `outbound-core-api.ts` 등 api 계층은 PC-FE-217 소관. types.ts(352줄) 분할은 별건(효용 낮음 — 이 task 아님).

## Acceptance Criteria
- **AC-1** 렌더 출력 byte-동일: 모든 `data-testid`·DOM 구조·className·aria 속성·텍스트 보존.
- **AC-2** 상호작용 보존: 필터/탭/페이지네이션/드릴/mutation 동작·dialog 포커스-트랩(open reset·auto-focus·Escape·Tab wrap)·낙관/토스트 동작 불변.
- **AC-3** 추출 컴포넌트는 순수 프레젠테이셔널(또는 hook)·서버/클라이언트 경계 보존(`'use client'` 위치 불변).
- **AC-4** `tsc --noEmit` + `pnpm lint` + `vitest`(wms-outbound-ops 관련 화면/컴포넌트 테스트) green. 기존 테스트가 계약 — 신규 테스트 불필요.

## Edge Cases / Failure Scenarios
- 포커스-트랩을 leaf로 내리면 querySelectorAll 범위가 깨져 Tab wrap 회귀 → container 유지.
- barrel(`index.ts`) export 표면 유지 — 소비처 import 깨지지 않게.

## Related
- 미러: PC-FE-098~212 컴포넌트 분할 시리즈(동일 휴리스틱·오케스트레이션).
- 파일 disjoint 병렬 가능: PC-FE-213/215/216/217.
