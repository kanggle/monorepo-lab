# Task ID

TASK-FE-056-fix-001

# Title

TASK-FE-056 리뷰 수정 — 빈 orderId 컨트랙트 위반, window.alert 제거, 쿠폰 만료 처리

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-056 리뷰에서 발견된 critical/warning 이슈를 수정한다.

# Scope

## In Scope

- CouponSelector에서 빈 orderId 전달 문제 해결: 체크아웃에서 쿠폰 적용 시 orderId 없이 할인 계산하는 방식으로 변경 (클라이언트 계산 또는 컨트랙트 수정)
- use-apply-coupon.ts에서 `window.alert` 제거 → 인라인 에러 상태로 변경
- CouponSelector에서 쿠폰 만료(422 COUPON_EXPIRED) 시 선택 자동 해제 및 안내 메시지
- 태스크 파일 Status를 `review`로 수정 (현재 `backlog`)
- 100개 하드코딩 페이지 크기 개선
- 관련 테스트 추가

## Out of Scope

- minOrderAmount 필드 추가 (컨트랙트 변경이 필요하므로 별도 태스크)

# Acceptance Criteria

- [ ] 빈 orderId가 서버에 전달되지 않는다
- [ ] `window.alert`가 제거된다
- [ ] 쿠폰 만료 시 선택이 자동 해제되고 안내 메시지가 표시된다
- [ ] 기존 테스트가 통과한다

# Related Specs

- `specs/contracts/http/promotion-api.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

# Edge Cases

- 체크아웃 도중 쿠폰 만료

# Failure Scenarios

- 쿠폰 적용 API 실패
