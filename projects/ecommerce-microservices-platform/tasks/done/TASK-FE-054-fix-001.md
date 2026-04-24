# Task ID

TASK-FE-054-fix-001

# Title

TASK-FE-054 리뷰 수정 — 위시리스트 삭제 시 itemId 없는 경우 처리, 100개 초과 에러 안내

# Status

review

# Owner

frontend

# Task Tags

- code
- test

# Goal

TASK-FE-054 리뷰에서 발견된 critical 이슈를 수정한다. 상품 목록에서 기존 찜 상품 삭제가 불가능한 버그를 해결한다.

# Scope

## In Scope

- WishlistButton에서 `wishlistItemId`가 없는 경우에도 삭제 가능하도록 수정
  - 옵션 1: `WishlistCheckResponse`에 `wishlistItemId` 반환하도록 컨트랙트 수정 → 타입 업데이트
  - 옵션 2: productId 기반 삭제 방식 검토
- `WishlistCheckResult` 타입에서 컨트랙트에 없는 필드 정리
- 위시리스트 100개 초과 시 구체적 에러 메시지 표시
- 삭제 불가 시나리오 테스트 추가
- 찜 상태 조회 실패 시 기본값 처리 테스트 추가

## Out of Scope

- 기능 추가

# Acceptance Criteria

- [ ] 상품 목록에서 이미 찜된 상품의 하트 클릭 시 삭제가 동작한다
- [ ] 위시리스트 100개 초과 시 안내 메시지가 표시된다
- [ ] 타입이 컨트랙트와 일치한다
- [ ] 관련 테스트가 추가된다

# Related Specs

- `specs/contracts/http/wishlist-api.md`

# Related Contracts

- `specs/contracts/http/wishlist-api.md`

# Edge Cases

- wishlistItemId 없이 삭제 시도
- 100개 초과 추가 시도

# Failure Scenarios

- 찜 상태 조회 API 실패
