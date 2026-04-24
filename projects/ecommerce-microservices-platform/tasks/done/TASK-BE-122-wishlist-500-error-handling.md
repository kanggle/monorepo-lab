# Task ID

TASK-BE-122

# Title

POST /api/wishlists 500 에러 방지를 위한 FK 위반 예외 처리 및 user_profiles 선체크

# Status

ready

# Owner

backend

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

user-service 의 `POST /api/wishlists` 가 `user_profiles` 행 부재로 인한 FK 위반 시 현재 500(INTERNAL_ERROR)을 반환하는 문제를 해결한다. 클라이언트에는 의미 있는 4xx 상태코드를 반환하고, 서비스 계층에서 선체크로 방어한다.

---

# Scope

## In Scope

- `WishlistService.addItem` 진입 시 `UserProfileRepository.existsByUserId()` 선체크 → 없으면 `UserProfileNotFoundException` throw
- `GlobalExceptionHandler` 에 `DataIntegrityViolationException` 전용 핸들러 추가 (최후 방어선)
- 단위/통합 테스트 추가

## Out of Scope

- Kafka/이벤트 인프라 변경
- 누락된 `user_profiles` 데이터 backfill 스크립트 (별도 운영 작업)
- 프런트엔드 에러 메시지 UX 개선

---

# Acceptance Criteria

- [ ] `user_profiles` 에 행이 없는 유저로 `POST /api/wishlists` 호출 시 404 `USER_PROFILE_NOT_FOUND` 반환
- [ ] `DataIntegrityViolationException` 이 발생해도 500이 아닌 명시적 4xx 반환
- [ ] 정상 유저의 찜 추가는 기존과 동일하게 201 반환
- [ ] 기존 `AlreadyInWishlistException` 경로는 변경 없이 409 유지
- [ ] 단위/통합 테스트가 추가되어 통과

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/...`

---

# Related Contracts

- `specs/contracts/http/wishlist-api.md`

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

---

# Implementation Notes

- `WishlistService.addItem` 의 기존 `existsByUserIdAndProductId` 체크보다 먼저 `UserProfileRepository.existsByUserId(userId)` 호출
- `GlobalExceptionHandler` 핸들러는 `Exception.class` 폴백보다 우선 매칭되도록 `DataIntegrityViolationException` 전용 메서드 추가
- 서비스 계층 선체크가 주 방어선, 핸들러는 레이스 컨디션/기타 누락 케이스용 백스톱

---

# Edge Cases

- 이벤트 지연으로 직후 가입한 유저가 `user_profiles` 반영 전에 요청 → 404 `USER_PROFILE_NOT_FOUND` (프런트에서 재시도 유도)
- 동일 요청 동시 실행 시 unique constraint 위반 → 409 또는 명시적 에러코드
- `productId` 가 존재하지 않는 상품이어도 FK 제약이 없으므로 기존처럼 성공 (범위 밖)

---

# Failure Scenarios

- DB 커넥션 장애: 500 유지 (일반 예외 핸들러)
- FK 외 다른 무결성 위반(unique 등): 409 CONFLICT 로 반환
- 선체크 쿼리 실패: 일반 예외로 폴백

---

# Test Requirements

- `GlobalExceptionHandlerTest` 에 `DataIntegrityViolationException` → 404/409 매핑 테스트
- `WishlistServiceTest` 에 user_profiles 미존재 시 `UserProfileNotFoundException` throw 테스트
- `WishlistIntegrationTest` 에 user_profiles 행 없이 POST 시 404 응답 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
