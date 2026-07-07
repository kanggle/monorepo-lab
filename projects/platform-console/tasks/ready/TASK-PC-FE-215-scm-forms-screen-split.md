# TASK-PC-FE-215 — SCM god-file 분할 (SupplierMapForm · PolicyForm · ReplenishmentScreen)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving component split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (SCM 폼/스크린 3종 — testid/markup/aria 보존)

---

## Goal

SCM에 남은 250줄 초과 컴포넌트 god-file 3종을 PC-FE 분할 시리즈 휴리스틱으로 분해한다:
- `scm-config/components/SupplierMapForm.tsx` (283줄)
- `scm-replenishment/components/ReplenishmentScreen.tsx` (262줄)
- `scm-config/components/PolicyForm.tsx` (255줄)

## Scope

각 파일을 응집 단위로 분해:
- **SupplierMapForm / PolicyForm**(config 폼): 필드 그룹·행 편집기·advisory 배너 등 프레젠테이셔널 조각 추출. flat 폼이면 hook-only(PC-FE-196 선례 — prop-drilling 강제 금지). validation/제출 로직은 hook 또는 container에 유지.
- **ReplenishmentScreen**(스크린): 필터 바·suggestion 테이블(행 포함)·approve/dismiss 액션 묶음 추출, 오케스트레이터는 배선만.
- dialog/포커스-트랩 존재 시 container 유지(PC-FE 분할 규약).

**Out of scope:** api/상태/hook 로직·`demand-planning-api.ts`/`demand-planning-seed-api.ts` 무변경(그 파일 네이밍 정규화는 PC-FE-217 소관). 엔드포인트·proxy·contract 무변경.

## Acceptance Criteria
- **AC-1** 3개 파일 각각 렌더 출력 byte-동일: `data-testid`·DOM 구조·className·aria·텍스트 보존.
- **AC-2** 상호작용 보존: 폼 validation·제출·필터·approve/dismiss·토스트/낙관 동작 불변.
- **AC-3** 추출 컴포넌트는 순수 프레젠테이셔널(또는 hook)·서버/클라이언트 경계(`'use client'`) 보존.
- **AC-4** 각 파일 목표 ≤ ~180줄(강제 아님 — 자연 경계 우선).
- **AC-5** `tsc --noEmit` + `pnpm lint` + `vitest`(scm-config·scm-replenishment 관련 테스트) green. 기존 테스트=계약, 신규 불필요.

## Edge Cases / Failure Scenarios
- config 폼이 flat이면 억지 컴포넌트 분할 대신 hook 추출 우선(PC-FE-196).
- barrel export 표면 유지.

## Related
- 미러: PC-FE-098~212 분할 시리즈, PC-FE-196(flat 폼 hook-only 선례).
- 파일 disjoint 병렬 가능: PC-FE-213/214/216/217.
