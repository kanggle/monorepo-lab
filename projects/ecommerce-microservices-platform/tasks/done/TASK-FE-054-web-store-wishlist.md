# Task ID

TASK-FE-054

# Title

web-store 위시리스트 — 상품 찜 추가/삭제, 위시리스트 목록, 찜 상태 표시

# Status

backlog

# Owner

frontend

# Task Tags

- code
- api
- test

# Goal

고객이 관심 있는 상품을 위시리스트에 추가/삭제하고, 마이페이지에서 위시리스트를 관리할 수 있으며, 상품 목록/상세 페이지에서 찜 상태를 확인할 수 있다.

# Scope

## In Scope

- 상품 목록/상세 페이지에 찜 토글 버튼 추가
- 위시리스트 추가 (POST `/api/wishlists`)
- 위시리스트 삭제 (DELETE `/api/wishlists/{wishlistItemId}`)
- 위시리스트 목록 조회 (GET `/api/wishlists/me`) — 페이지네이션
- 찜 여부 확인 (GET `/api/wishlists/me/check?productId=`)
- 마이페이지 위시리스트 페이지 (`/my/wishlist`)

## Out of Scope

- 위시리스트 공유 기능
- 위시리스트에서 바로 장바구니 추가 (후속 태스크)
- 최대 개수 제한 UI (서버에서 처리)

# Acceptance Criteria

- [ ] 상품 카드와 상세 페이지에 찜 버튼(하트 아이콘)이 표시된다
- [ ] 찜 버튼 클릭 시 위시리스트에 추가/삭제가 토글된다
- [ ] 마이페이지 `/my/wishlist`에서 위시리스트 목록이 페이지네이션으로 표시된다
- [ ] 위시리스트 항목에 상품명, 가격, 상태가 표시된다
- [ ] 삭제된 상품은 "판매 종료" 등으로 표시된다
- [ ] 비로그인 사용자가 찜 버튼 클릭 시 로그인 페이지로 이동한다
- [ ] 로딩/에러/빈 상태가 처리된다
- [ ] 컴포넌트 테스트와 페이지 테스트가 작성된다

# Related Specs

- `specs/services/user-service/overview.md`
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

- `specs/contracts/http/wishlist-api.md`

# Target App

- `apps/web-store`

# Implementation Notes

- feature-sliced-design에 따라 `features/wishlist/` 디렉토리 구성
- 찜 상태 체크 API를 활용하여 상품 목록에서 일괄 확인 (productId 쿼리)
- 위시리스트 최대 100개 제한은 서버에서 처리 — 409 에러 핸들링 필요
- 상품 정보(이름, 가격, 상태)는 서버가 product-service에서 조회하여 반환
- 마이페이지 라우트에 `/my/wishlist` 추가

# Edge Cases

- 위시리스트가 비어 있는 경우 (빈 상태)
- 삭제된 상품이 위시리스트에 있는 경우 (status: DELETED, name: null)
- 위시리스트 최대 개수(100) 초과 시도
- 이미 추가된 상품 중복 추가 시도 (409)
- 비로그인 상태에서 찜 시도

# Failure Scenarios

- API 호출 실패
- 위시리스트 추가/삭제 중 네트워크 에러
- 찜 상태 조회 실패 시 기본값 처리
- 권한 없는 접근 (401)
- 타임아웃

# Test Requirements

- WishlistButton 컴포넌트 테스트 (토글 동작, 비로그인 처리)
- WishlistPage 페이지 테스트 (목록 렌더링, 페이지네이션)
- 빈 상태/삭제된 상품 표시 테스트
- 에러/로딩 상태 테스트

# Definition of Done

- [ ] UI 구현 완료
- [ ] API 연동 완료
- [ ] 로딩/에러/빈 상태 처리
- [ ] 테스트 추가
- [ ] 테스트 통과
- [ ] 리뷰 준비 완료
