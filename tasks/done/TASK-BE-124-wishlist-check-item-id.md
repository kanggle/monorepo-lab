# Task ID

TASK-BE-124

# Title

GET /api/wishlists/me/check 응답에 wishlistItemId 추가 — 컨트랙트와 구현 불일치 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# Goal

`/api/wishlists/me/check` 응답이 컨트랙트(`specs/contracts/http/wishlist-api.md`)와 프런트엔드 타입(`packages/types/src/wishlist.ts`)에서 요구하는 `wishlistItemId` 필드를 누락하고 있다. 프런트엔드는 이 값으로 DELETE 요청의 itemId를 확보하는데, 누락되어 홈 화면에서 활성화된 하트 버튼을 다시 눌러도 찜이 해제되지 않는 버그가 발생한다. 백엔드 DTO/서비스/레포지토리를 보완하여 컨트랙트를 충족한다.

# Scope

## In Scope

- `WishlistCheckResult`에 `wishlistItemId: UUID`(nullable) 추가
- `WishlistService.checkItem`이 `findByUserIdAndProductId`로 아이템을 조회해 ID 포함한 결과 반환 (없으면 null)
- `WishlistCheckResponse`에 `wishlistItemId` 필드 추가
- `WishlistItemRepository`/`WishlistItemJpaRepository`에 `findByUserIdAndProductId` 메서드 추가 (이미 있으면 재사용)
- 단위/통합 테스트 갱신

## Out of Scope

- 프런트엔드 변경 (이미 해당 필드 기대)
- 다른 엔드포인트 수정

# Acceptance Criteria

- [ ] `GET /api/wishlists/me/check?productId=X`가 `inWishlist=true`일 때 `wishlistItemId` UUID 반환
- [ ] `inWishlist=false`일 때 `wishlistItemId: null` 반환
- [ ] `WishlistServiceTest`, `WishlistControllerTest`, `WishlistIntegrationTest`가 새 필드를 검증하도록 갱신
- [ ] 모든 user-service 테스트 통과

# Related Specs

- `specs/contracts/http/wishlist-api.md`
- `specs/services/user-service/architecture.md`

# Related Contracts

- `specs/contracts/http/wishlist-api.md` (이미 wishlistItemId 명시되어 있음 — 추가 수정 불필요)

# Target Service

- `user-service`

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

# Implementation Notes

- `WishlistItemJpaRepository`: `Optional<WishlistItemEntity> findByUserIdAndProductId(UUID userId, UUID productId)` 추가
- `WishlistItemRepository`(domain port): `Optional<WishlistItem> findByUserIdAndProductId(...)` 추가
- `WishlistService.checkItem`: `findByUserIdAndProductId`로 조회하고 결과에 따라 `WishlistCheckResult(productId, exists, itemId or null)` 반환
- `addItem`의 `existsByUserIdAndProductId`는 그대로 유지(성능)

# Edge Cases

- 찜 목록에 없는 경우: `inWishlist=false`, `wishlistItemId=null`
- 동일 상품이 복수 행 존재 불가 (unique 제약) → `findByUserIdAndProductId`는 0 또는 1

# Failure Scenarios

- DB 조회 실패: 기존 예외 핸들러로 500 폴백

# Test Requirements

- `WishlistServiceTest.checkItem`: inWishlist true/false 두 케이스에서 wishlistItemId 필드 검증
- `WishlistIntegrationTest`: 응답 JSON에 `wishlistItemId` 포함 확인

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Ready for review
