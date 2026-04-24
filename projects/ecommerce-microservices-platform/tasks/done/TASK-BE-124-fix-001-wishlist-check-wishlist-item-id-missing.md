# Task ID

TASK-BE-124-fix-001

# Title

TASK-BE-124 리뷰 지적 수정 — wishlistItemId 미구현 전면 보완

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# Goal

TASK-BE-124 구현 리뷰 결과, `wishlistItemId` 필드가 모든 레이어에서 완전히 누락되어 있어 컨트랙트를 여전히 충족하지 못한다. 아래 Critical 이슈들을 수정한다.

## 발견된 Critical 이슈

1. `WishlistCheckResult` — `wishlistItemId: UUID` 필드 없음 (태스크 Scope: "WishlistCheckResult에 wishlistItemId: UUID(nullable) 추가" 미이행)
2. `WishlistCheckResponse` — `wishlistItemId` 필드 없음, `from()` 팩토리 메서드도 미반영
3. `WishlistService.checkItem` — `existsByUserIdAndProductId` 만 호출, `findByUserIdAndProductId` 미사용 → `wishlistItemId` 값을 채울 수 없음
4. `WishlistItemRepository` (도메인 포트) — `findByUserIdAndProductId` 메서드 없음
5. `WishlistItemJpaRepository` — `findByUserIdAndProductId` 쿼리 메서드 없음
6. `WishlistItemRepositoryImpl` — 위 JPA 메서드를 위임하는 `findByUserIdAndProductId` 구현 없음
7. 모든 테스트(`WishlistServiceTest`, `WishlistControllerTest`, `WishlistIntegrationTest`)에서 `wishlistItemId` 필드 검증 없음

# Scope

## In Scope

- `WishlistCheckResult` 레코드에 `UUID wishlistItemId` (nullable) 필드 추가
- `WishlistCheckResponse` 레코드에 `UUID wishlistItemId` 필드 추가, `from()` 메서드 수정
- `WishlistItemRepository` 도메인 포트에 `Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId)` 추가
- `WishlistItemJpaRepository`에 `Optional<WishlistItemJpaEntity> findByUserIdAndProductId(UUID userId, UUID productId)` 추가
- `WishlistItemRepositoryImpl`에 위 메서드 위임 구현 추가
- `WishlistService.checkItem` 수정: `findByUserIdAndProductId`로 조회하고 존재 시 해당 item의 id를, 없으면 null을 `WishlistCheckResult`에 포함
- `WishlistServiceTest.checkItem`: `wishlistItemId` 필드 값 검증 추가 (true 케이스 → UUID 값, false 케이스 → null)
- `WishlistControllerTest.CheckItem`: `checkItem_exists_returnsTrue` 테스트에 `$.wishlistItemId` 검증 추가, `checkItem_notExists_returnsFalse` 테스트에 `$.wishlistItemId` 가 null임을 검증
- `WishlistIntegrationTest.CheckItem`: `checkItem_exists_returnsTrue`에 `$.wishlistItemId` 존재 및 값 검증, `checkItem_notExists_returnsFalse`에 `$.wishlistItemId` null 검증

## Out of Scope

- 다른 엔드포인트 변경
- 프런트엔드 변경

# Acceptance Criteria

- [ ] `GET /api/wishlists/me/check?productId=X` 응답 JSON에 `wishlistItemId` 필드 포함
- [ ] `inWishlist=true`일 때 `wishlistItemId`가 UUID 문자열로 반환됨
- [ ] `inWishlist=false`일 때 `wishlistItemId: null` 반환
- [ ] `WishlistServiceTest.checkItem` 두 케이스 모두 `wishlistItemId` 값 검증 포함
- [ ] `WishlistControllerTest.CheckItem` 두 케이스 모두 `wishlistItemId` JSON 필드 검증 포함
- [ ] `WishlistIntegrationTest.CheckItem` 두 케이스 모두 `wishlistItemId` 검증 포함
- [ ] 모든 user-service 테스트 통과

# Related Specs

- `specs/contracts/http/wishlist-api.md`
- `specs/services/user-service/architecture.md`

# Related Contracts

- `specs/contracts/http/wishlist-api.md` (wishlistItemId 이미 명시 — 추가 수정 불필요)

# Target Service

- `user-service`

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

# Implementation Notes

- `WishlistItemJpaRepository`: `Optional<WishlistItemJpaEntity> findByUserIdAndProductId(UUID userId, UUID productId)` 추가
- `WishlistItemRepository` (도메인 포트): `Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId)` 추가
- `WishlistItemRepositoryImpl`: 위 포트 구현 — `jpaRepository.findByUserIdAndProductId(userId, productId).map(mapper::toDomain)` 반환
- `WishlistService.checkItem`: `findByUserIdAndProductId`로 아이템 조회 후 `WishlistCheckResult(productId, item.isPresent(), item.map(WishlistItem::getId).orElse(null))` 반환
- `addItem`의 `existsByUserIdAndProductId`는 성능상 그대로 유지

# Edge Cases

- 찜 목록에 없는 경우: `inWishlist=false`, `wishlistItemId=null`
- unique 제약으로 동일 상품 복수 행 불가 → `findByUserIdAndProductId`는 0 또는 1 행 반환

# Failure Scenarios

- DB 조회 실패: 기존 예외 핸들러로 500 폴백

# Test Requirements

- `WishlistServiceTest.checkItem_exists_returnsTrue`: `result.wishlistItemId()`가 non-null UUID임을 검증
- `WishlistServiceTest.checkItem_notExists_returnsFalse`: `result.wishlistItemId()`가 null임을 검증
- `WishlistControllerTest.checkItem_exists_returnsTrue`: `$.wishlistItemId` 존재 및 UUID 형식 검증
- `WishlistControllerTest.checkItem_notExists_returnsFalse`: `$.wishlistItemId` null 검증
- `WishlistIntegrationTest.checkItem_exists_returnsTrue`: DB에 저장된 아이템의 `wishlistItemId`와 응답 JSON 값이 일치하는지 검증
- `WishlistIntegrationTest.checkItem_notExists_returnsFalse`: `$.wishlistItemId` null 검증

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Ready for review
