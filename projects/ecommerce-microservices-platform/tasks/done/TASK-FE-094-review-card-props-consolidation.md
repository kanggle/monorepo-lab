# Task ID

TASK-FE-094

# Title

web-store ReviewCard prop consolidation — group 13 flat props into review + actions objects, drop dead reviewId

# Status

done

# Owner

frontend

# Task Tags

- code
- refactor

# Goal

TASK-FE-092 스캔의 Tier 3(컴포넌트 구조) 후속. 정밀 조사 결과 Tier 3 4종 중 **ReviewCard 하나만 실질 가치**가 있고 나머지 3종(OrderItemsSection·AddressSection·AddressForm)은 marginal/false-dup으로 보류. `ReviewCard`의 13개 flat props(그중 `reviewId`는 본문 미사용 `_reviewId`)를 `review` 데이터 객체 + `actions` 콜백 객체로 그룹하고 죽은 `reviewId` prop을 제거한다. 동작 무변경(`platform/refactoring-policy.md` 준수).

# Scope

## In Scope

- `features/review/ui/ReviewCard.tsx`: props 인터페이스를 `{ review: {rating,title,content,createdAt}, isEditing, showActions, isUpdatePending, actions: {onEdit,onDelete,onUpdate,onCancelEdit}, productLink? }`로 재구성. 미사용 `reviewId` prop 제거. 본문을 `review.*`/`actions.*` 참조로 갱신.
- `features/review/ui/ReviewList.tsx`: 호출부를 `review={review}` + `actions={{...}}`로 갱신(현재 필드별 개별 전달 제거).
- `features/review/ui/MyReviews.tsx`: 동일 + `productLink` 유지.

## Out of Scope

- ReviewCard의 렌더 출력·스타일·동작 변경.
- **보류된 Tier 3 3종**(별도 판단, 대개 미실행): OrderItemsSection 공유 추출(checkout↔order = 아이템 타입·레이아웃·요약 상이 = false-dup) / AddressSection 9-props 그룹(호출부 1곳, 혼합 출처 = marginal) / AddressForm `useAddressFormFields` 훅(field-hook↔validation-hook 결합 + checkbox/address2 error-clear 엣지 = net 개선 불확실).
- Tier 4 폴리시(FE-093에서 보류), applyCoupon(KEEP), PriceDisplay(시각 변경).

# Acceptance Criteria

- [ ] **AC-1** `ReviewCard`가 `review`/`actions` 객체 props를 받고, 평면 데이터/콜백 props와 미사용 `reviewId`가 없다.
- [ ] **AC-2** 두 호출부(ReviewList·MyReviews)가 새 시그니처로 갱신되고 렌더 출력 불변.
- [ ] **AC-3** `tsc --noEmit` 0 + `next lint` 0.
- [ ] **AC-4** 테스트 무수정 통과 — ReviewCard 직접 테스트는 없고, review-list.test·my-reviews-page.test 는 부모에 review 객체를 먹여 **출력**을 단언(props 직접 전달 안 함) → 출력 불변이면 통과. CI(Node20)가 권위.

# Related Specs

- `specs/services/web-store/architecture.md`
- `platform/refactoring-policy.md`

# Related Skills

- `.claude/skills/frontend/architecture/feature-sliced-design.md`

# Related Contracts

- N/A

# Target App

- `apps/web-store`

# Implementation Notes

- 두 호출부가 이미 `reviews.map((review) => ...)`의 `review` 객체에서 rating/title/content/createdAt 를 개별 전달 중 → `review={review}`(변수라 excess property 허용, 구조적 서브타이핑) 로 대체 가능.
- `reviewId`는 ReviewCard 본문에서 안 쓰임(`_reviewId`) → 제거. key(`review.reviewId`)는 호출부에서 계속 사용.
- 로컬 vitest 불가(Node24↔vitest4) → tsc+lint 로컬, CI 권위.

# Edge Cases

- `review={review}` 시 caller review 객체가 `{rating,title,content,createdAt}` 를 만족해야 함(현재 만족). 타입 초과 필드는 변수 전달이라 허용.
- `actions` 객체를 호출부에서 인라인 생성 — 콜백 신원(재생성)은 기존과 동일(매 렌더 새 화살표, 기존도 동일) → 리렌더 동작 불변.

# Failure Scenarios

- 본문에서 `review.`/`actions.` 로 못 바꾼 참조가 남아 tsc 에러(→ tsc 게이트).
- 호출부 review 객체가 필드명 불일치(→ tsc 에러로 포착).

# Test Requirements

- 기존 review-list.test / my-reviews-page.test 무수정 통과(출력 단언).

# Definition of Done

- [ ] ReviewCard + 2 호출부 갱신, 미사용 reviewId 제거
- [ ] `tsc` 0 + `next lint` 0
- [ ] CI web-store 레인 GREEN
- [ ] worktree 정리
