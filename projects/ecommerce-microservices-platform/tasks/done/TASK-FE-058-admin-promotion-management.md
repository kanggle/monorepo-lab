# Task ID

TASK-FE-058

# Title

admin-dashboard 프로모션/쿠폰 관리 — 프로모션 CRUD 및 쿠폰 발급

# Status

review

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

관리자가 프로모션을 생성/조회/수정/삭제하고, 특정 사용자에게 쿠폰을 발급할 수 있다.

# Scope

## In Scope

- 프로모션 목록 페이지 (GET `/api/promotions`) — 페이지네이션, 상태 필터
- 프로모션 상세 페이지 (GET `/api/promotions/{promotionId}`)
- 프로모션 생성 폼 (POST `/api/promotions`)
- 프로모션 수정 폼 (PUT `/api/promotions/{promotionId}`)
- 프로모션 삭제 (DELETE `/api/promotions/{promotionId}`)
- 쿠폰 발급 기능 (POST `/api/promotions/{promotionId}/coupons/issue`)
- 라우트: `/promotions`, `/promotions/new`, `/promotions/[id]`, `/promotions/[id]/edit`

## Out of Scope

- 쿠폰 사용 내역 상세 분석
- 프로모션 성과 대시보드
- 대량 쿠폰 일괄 발급 (CSV 업로드 등)

# Acceptance Criteria

- [ ] 프로모션 목록이 페이지네이션과 상태 필터(SCHEDULED/ACTIVE/ENDED)로 표시된다
- [ ] 프로모션 생성 폼에서 이름, 할인 유형, 할인값, 기간, 최소 주문금액을 입력할 수 있다
- [ ] 프로모션 상세에서 정보 확인 및 수정/삭제가 가능하다
- [ ] 프로모션 상세에서 대상 사용자에게 쿠폰을 발급할 수 있다
- [ ] 삭제 시 확인 다이얼로그가 표시된다
- [ ] 폼 validation이 동작한다 (필수값, 날짜 범위 등)
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/promotion-service/overview.md`
- `specs/services/promotion-service/architecture.md`
- `specs/services/admin-dashboard/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/layered-by-feature.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/form-handling.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

# Target App

- `apps/admin-dashboard`

# Implementation Notes

- layered-by-feature 아키텍처에 따라 `features/promotion-management/` 디렉토리 구성
- 기존 product-management, order-management 패턴 참고
- 프로모션 상태: SCHEDULED(예정), ACTIVE(진행중), ENDED(종료)
- 쿠폰 발급 시 대상 사용자 ID 목록 입력 필요
- 사이드바 네비게이션에 "프로모션 관리" 메뉴 추가
- 기존 ConfirmDialog, DataTable 등 공통 컴포넌트 활용

# Edge Cases

- 프로모션이 없는 경우 (빈 상태)
- 이미 ENDED된 프로모션 수정 시도
- 쿠폰 발급 대상 사용자가 존재하지 않는 경우
- 동일 프로모션에 동일 사용자 중복 발급 시도
- 프로모션 기간 validation (시작일 < 종료일)

# Failure Scenarios

- API 호출 실패
- 프로모션 생성/수정 시 validation 실패
- 쿠폰 발급 실패 (사용자 미존재 등)
- 권한 없는 접근 (403)
- 타임아웃
- 삭제 중 에러

# Test Requirements

- PromotionList 페이지 테스트 (목록, 필터링, 페이지네이션)
- PromotionForm 컴포넌트 테스트 (생성/수정 폼, validation)
- PromotionDetail 페이지 테스트
- CouponIssueForm 컴포넌트 테스트
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
