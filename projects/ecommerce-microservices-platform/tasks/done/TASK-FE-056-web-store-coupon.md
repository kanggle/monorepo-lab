# Task ID

TASK-FE-056

# Title

web-store 쿠폰 — 내 쿠폰 목록 조회 및 체크아웃 시 쿠폰 적용

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

고객이 마이페이지에서 보유 쿠폰 목록을 확인하고, 체크아웃 과정에서 사용 가능한 쿠폰을 선택하여 할인을 적용할 수 있다.

# Scope

## In Scope

- 마이페이지 쿠폰 목록 (GET `/api/coupons/me`) — 페이지네이션, 상태 필터
- 쿠폰 상태 표시 (ISSUED, USED, EXPIRED)
- 체크아웃 페이지에 쿠폰 선택 UI 추가
- 쿠폰 적용 시 할인 금액 계산 표시 (POST `/api/coupons/{couponId}/apply`)
- 마이페이지 라우트 `/my/coupons` 추가

## Out of Scope

- 쿠폰 발급 (서버 이벤트 또는 관리자가 처리)
- 프로모션 목록 페이지 (고객 대상 프로모션 노출은 후속 태스크)
- 쿠폰 코드 직접 입력

# Acceptance Criteria

- [ ] 마이페이지 `/my/coupons`에서 보유 쿠폰 목록이 표시된다
- [ ] 쿠폰 상태별 필터링(전체/사용가능/사용완료/만료)이 동작한다
- [ ] 쿠폰 카드에 할인 유형, 할인값, 최소 주문금액, 유효기간이 표시된다
- [ ] 체크아웃 페이지에서 사용 가능한 쿠폰 목록을 선택할 수 있다
- [ ] 쿠폰 적용 시 할인 금액이 주문 요약에 반영된다
- [ ] 만료/사용 완료 쿠폰은 선택 불가 상태로 표시된다
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/promotion-service/overview.md`
- `specs/services/web-store/overview.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/frontend/implementation-workflow.md`
- `.claude/skills/frontend/architecture/feature-sliced-design.md`
- `.claude/skills/frontend/api-client.md`
- `.claude/skills/frontend/state-management.md`
- `.claude/skills/frontend/loading-error-handling.md`
- `.claude/skills/frontend/testing-frontend.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- feature-sliced-design에 따라 `features/coupon/` 디렉토리 구성
- 체크아웃 페이지(`features/checkout/`)에 쿠폰 선택 컴포넌트 통합
- 쿠폰 적용 API 응답으로 할인 금액을 받아 주문 요약 UI에 반영
- 쿠폰 상태: ISSUED(사용가능), USED(사용완료), EXPIRED(만료)
- 마이페이지 네비게이션에 "쿠폰" 메뉴 추가

# Edge Cases

- 쿠폰이 없는 경우 (빈 상태)
- 최소 주문금액 미달 쿠폰
- 체크아웃 도중 쿠폰 만료
- 쿠폰 적용 후 주문 금액 변경 시 재계산
- 동시에 여러 쿠폰 적용 시도 (서버 정책에 따름)

# Failure Scenarios

- API 호출 실패
- 쿠폰 적용 시 validation 실패 (최소 주문금액 등)
- 이미 사용된 쿠폰 적용 시도 (409)
- 타임아웃
- 권한 없는 접근 (401)

# Test Requirements

- CouponList 컴포넌트 테스트 (목록, 필터링)
- CouponCard 컴포넌트 테스트 (상태별 표시)
- CouponSelector 컴포넌트 테스트 (체크아웃 쿠폰 선택)
- MyCoupons 페이지 테스트
- 에러/로딩/빈 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
