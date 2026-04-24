# Task ID

TASK-FE-053

# Title

web-store 리뷰 시스템 — 상품 리뷰 목록/요약, 리뷰 작성/수정/삭제, 내 리뷰 관리

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

고객이 상품 상세 페이지에서 다른 사용자의 리뷰와 평점 요약을 확인하고, 구매한 상품에 대해 리뷰를 작성/수정/삭제할 수 있으며, 마이페이지에서 내가 작성한 리뷰 목록을 관리할 수 있다.

# Scope

## In Scope

- 상품 상세 페이지에 리뷰 목록 섹션 추가 (GET `/api/reviews/products/{productId}`)
- 상품 상세 페이지에 평점 요약 표시 (GET `/api/reviews/products/{productId}/summary`)
- 리뷰 작성 폼 (POST `/api/reviews`) — 별점(1~5) + 내용
- 리뷰 수정 (PUT `/api/reviews/{reviewId}`) — 본인 작성분만
- 리뷰 삭제 (DELETE `/api/reviews/{reviewId}`) — 본인 작성분만
- 마이페이지 내 리뷰 목록 (GET `/api/reviews/me`)
- 페이지네이션 처리

## Out of Scope

- 리뷰 이미지 첨부
- 관리자 리뷰 관리 (admin-dashboard에서 별도 처리)
- 리뷰 신고 기능

# Acceptance Criteria

- [ ] 상품 상세 페이지에서 해당 상품의 리뷰 목록이 페이지네이션으로 표시된다
- [ ] 평균 평점과 별점 분포(1~5점)가 요약 섹션에 표시된다
- [ ] 로그인한 사용자가 구매한 상품에 대해 리뷰를 작성할 수 있다
- [ ] 본인 리뷰에 수정/삭제 버튼이 노출되고 동작한다
- [ ] 마이페이지 `/my/reviews`에서 내 리뷰 목록을 확인할 수 있다
- [ ] 비로그인 사용자도 리뷰 목록과 평점 요약을 볼 수 있다
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/review-service/overview.md`
- `specs/services/review-service/architecture.md`
- `specs/services/web-store/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/review-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- 리뷰 목록/요약은 인증 불필요 (public endpoint)
- 리뷰 작성은 구매 이력 검증이 서버에서 처리됨 — 프론트에서는 403 에러 핸들링 필요
- feature-sliced-design 아키텍처에 따라 `features/review/` 디렉토리 구성
- 기존 상품 상세 페이지(`(store)/products/[id]`)에 리뷰 섹션 통합
- 마이페이지 라우트에 `/my/reviews` 추가

# Edge Cases

- 리뷰가 없는 상품 (빈 상태)
- 구매하지 않은 상품에 리뷰 작성 시도 (403)
- 이미 리뷰를 작성한 상품에 중복 작성 시도
- 삭제된 상품의 리뷰 표시
- 긴 리뷰 내용 처리

# Failure Scenarios

- API 호출 실패 (네트워크 에러)
- 리뷰 작성/수정 시 validation 실패
- 권한 없는 수정/삭제 시도 (403)
- 타임아웃
- 페이지네이션 중 데이터 변경

# Test Requirements

- ReviewList 컴포넌트 테스트 (목록 렌더링, 페이지네이션)
- RatingSummary 컴포넌트 테스트 (평점 분포 표시)
- ReviewForm 컴포넌트 테스트 (작성/수정 폼)
- MyReviews 페이지 테스트
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
