# TASK-PC-FE-216 — ecommerce-ops PromotionForm god-file 분할

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving component split
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet 4.6 (단일 폼 borderline god-file — 분할 시리즈 표준 패턴)

---

## Goal

이커머스에 남은 유일한 250줄 초과 컴포넌트 `ecommerce-ops/components/PromotionForm.tsx`(258줄)를 PC-FE 분할 시리즈 휴리스틱으로 분해한다.

## Scope

`PromotionForm.tsx`를 응집 단위로 분해:
- 필드 그룹(기본 정보·기간·할인 규칙·대상 조건 등)·행 편집기·advisory 조각을 프레젠테이셔널 leaf로 추출.
- validation/제출 로직은 폼 hook(`use-promotion-form.ts`가 이미 존재 — 소비 유지)에 두고, container는 조립만.
- flat 폼이면 hook-only 우선(PC-FE-196 — prop-drilling 강제 금지).

**Out of scope:** api/상태 무변경. `use-promotion-form.ts`를 `components/`→`hooks/`로 이동하는 배치 정규화는 PC-FE-217 소관(이 task는 컴포넌트 분할만). 엔드포인트·proxy·contract 무변경.

## Acceptance Criteria
- **AC-1** 렌더 출력 byte-동일: `data-testid`·DOM 구조·className·aria·텍스트 보존.
- **AC-2** 상호작용 보존: validation·제출·필드 상호작용·토스트 동작 불변.
- **AC-3** 추출 컴포넌트는 순수 프레젠테이셔널(또는 hook)·`'use client'` 경계 보존.
- **AC-4** 목표 ≤ ~180줄(강제 아님).
- **AC-5** `tsc --noEmit` + `pnpm lint` + `vitest`(promotion 관련 테스트) green. 기존 테스트=계약.

## Edge Cases / Failure Scenarios
- flat 폼이면 억지 분할 금지 — hook 추출 우선.
- barrel export 표면 유지.

## Related
- 미러: PC-FE-098~212 분할 시리즈, PC-FE-196.
- 파일 disjoint 병렬 가능(단 PC-FE-217과 ecommerce components 접촉 순서 주의).
