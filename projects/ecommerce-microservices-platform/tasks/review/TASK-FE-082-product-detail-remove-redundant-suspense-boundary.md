# Task ID

TASK-FE-082

# Title

상품 상세 페이지 이중 Suspense 경계 제거 (next/dynamic loading ↔ 명시적 Suspense 중복)

# Status

review

# Owner

frontend

# Task Tags

- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

상품 상세 페이지(`/products/[id]`)에서 리뷰 섹션을 감싸는 **중복 Suspense 경계**를 제거해, React 19.2의 async-info 정리 시 발생하는 콘솔 경고
`"We are cleaning up async info that was not on the parent Suspense boundary. This is a bug in React."` 의 앱-측 트리거를 없앤다. 리뷰 로딩 스켈레톤·기능 동작은 사용자 관점에서 동일하게 유지한다.

---

# Scope

## In Scope

- `apps/web-store/src/app/(store)/products/[id]/page.tsx` 의 리뷰 렌더 블록에서 **이중 경계 중 하나만 남긴다**:
  - `ReviewList` 는 `next/dynamic(..., { loading: () => <ReviewListSkeleton/> })` 로 이미 lazy 청크 로드용 Suspense 경계 + fallback 을 갖는다.
  - 이를 다시 감싼 명시적 `<Suspense fallback={<ReviewListSkeleton count={3}/>}>` 는 **같은 서브트리에 대한 중복 경계**다.
  - 택1: (a) 바깥 `<Suspense>` 제거하고 dynamic 의 `loading` 을 단일 경계로 유지, 또는 (b) dynamic 에서 `loading` 제거하고 `<Suspense>` 만 유지. **(a) 권장** (코드-스플리팅 유지 + 경계 단일화).
- 스켈레톤 개수/모양이 기존과 시각적으로 동일하도록 fallback 정합 유지 (`ReviewListSkeleton count={3}`).

## Out of Scope

- `getCachedProduct = cache(...)` 를 `generateMetadata` + 페이지 컴포넌트 두 스코프에서 읽는 구조 — 별도 async-info 후보이나 App Router 표준 패턴(메타데이터+렌더 캐시 공유)이라 변경하지 않는다.
- `ReviewList` 내부(클라이언트 컴포넌트 + TanStack Query) 로직.
- React/Next 버전 업그레이드 — 경고의 근본은 React 19.2 계측 결함("bug in React")이며 그 수정은 React 패치 범프 소관. 본 task 는 앱-측 중복 경계만 제거한다.
- 다른 라우트의 `loading.tsx` / Suspense 구조.

---

# Acceptance Criteria

- [ ] `/products/[id]` 페이지에서 리뷰 섹션을 감싸는 Suspense 경계가 **단일**이다 (dynamic loading 과 명시적 `<Suspense>` 중복 제거).
- [ ] 리뷰 청크 로드 중 `ReviewListSkeleton`(3개) 이 기존과 동일하게 표시된다 (로딩 상태 testable).
- [ ] 상품 상세 렌더·리뷰 목록·리뷰 작성/수정/삭제 동작이 회귀 없이 유지된다.
- [ ] `next build` (prod) 통과 + `pnpm lint` + `tsc` 통과 (unused import 잔재 없음 — 예: 미사용 `Suspense` import 제거).
- [ ] dev 모드 브라우저 콘솔에서 해당 async-info 경고가 상품 상세 진입 시 더 이상 재현되지 않음(수동 확인; DevTools 확장 무관 환경).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/ecommerce.md` and `rules/traits/<trait>.md` matching the declared classification.

- `PROJECT.md` (domain=ecommerce; traits: transactional, content-heavy, read-heavy, integration-heavy, multi-tenant)
- `specs/features/...` (상품 상세 / 리뷰 표시 관련 피처 스펙 — 존재 시)
- `platform/service-types/frontend-app.md`

# Related Skills

- `.claude/skills/frontend/bundling-perf/SKILL.md` (코드-스플리팅/`next/dynamic` 경계)
- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

- 없음 (순수 렌더-경계 리팩터; product-service / review-service API 계약 변경 없음)

---

# Target App

- `apps/web-store`

---

# Implementation Notes

- 트리거 파일: `apps/web-store/src/app/(store)/products/[id]/page.tsx` (L12-15 dynamic 정의, L66-68 명시적 Suspense).
- `ReviewList` 는 `'use client'` + TanStack Query 훅(`useProductReviews`) 이라 **데이터로 suspend 하지 않는다** → 바깥 `<Suspense>` 가 실제로 잡는 것은 dynamic 청크 로드뿐이고 dynamic 의 `loading` 과 완전 중복. 따라서 (a) 바깥 `<Suspense>` 제거가 안전.
- 제거 후 미사용이 되는 `Suspense` import(`react`)를 함께 정리 (lint no-unused-vars → CI 프런트 잡 RED 방지, 로컬 검증에 `pnpm lint` 필수).
- 배경: React 19.2 는 Suspense 경계마다 async-info 를 매달아 정리하는데, 중복 경계가 부모-경계 매칭을 어긋나게 해 위 invariant 경고를 유발. 무해(렌더/하이드레이션 정상)하지만 콘솔 노이즈 제거 + 경계 단일화 클린업 목적.

---

# Edge Cases

- 리뷰 청크 로드 지연 시 스켈레톤 표시 (loading state)
- 리뷰 0건 (empty state — `ReviewList` 내부 EmptyState)
- 리뷰 API 오류 (`isError` → ErrorMessage; 페이지 자체는 렌더 유지)
- 상품 fetch 실패/notFound (기존 분기 유지 — 본 task 무관 영역, 회귀만 확인)
- 비로그인 사용자 (리뷰 작성 폼 게이팅 — 기존 동작 유지)

---

# Failure Scenarios

- 리뷰 청크 로드 실패 → dynamic loading/에러 경계 동작 유지
- review-service 타임아웃 → TanStack Query 재시도/에러 상태 (기존 동작)
- 스켈레톤 정합 깨짐(개수/레이아웃 시프트) → CLS 회귀 주의 (fallback count=3 유지)
- 미사용 import 잔재 → lint RED

---

# Test Requirements

- 컴포넌트/페이지 렌더 테스트: 상품 상세가 `ProductDetailWithCart` + 리뷰 섹션을 렌더하고, 청크 로드 중 스켈레톤이 표시되는지 (vitest).
- 회귀: 리뷰 목록/작성/수정/삭제 플로우 기존 테스트 통과.
- 로컬 3종 게이트: `pnpm lint` + `tsc` + `vitest` (프런트 CI 잡 정합).
- e2e 는 본 task 범위상 불요(렌더-경계 리팩터, 계약/플로우 불변).

---

# Definition of Done

- [ ] 이중 Suspense 경계 제거 (단일 경계)
- [ ] 미사용 import 정리
- [ ] 로딩/빈/에러 상태 유지 확인
- [ ] `pnpm lint` + `tsc` + `vitest` 통과
- [ ] `next build`(prod) 통과
- [ ] dev 콘솔 async-info 경고 미재현 수동 확인
- [ ] Ready for review
