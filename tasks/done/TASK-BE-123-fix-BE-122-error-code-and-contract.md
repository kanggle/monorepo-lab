# Task ID

TASK-BE-123

# Title

TASK-BE-122 리뷰 이슈 수정: DATA_INTEGRITY_VIOLATION 에러 코드 등록 및 wishlist-api 컨트랙트 갱신

# Status

ready

# Owner

backend

# Task Tags

- code

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

TASK-BE-122 리뷰에서 발견된 두 가지 스펙 위반을 수정한다.

1. `GlobalExceptionHandler`에서 사용 중인 `DATA_INTEGRITY_VIOLATION` 에러 코드가 `specs/platform/error-handling.md`에 등록되어 있지 않다. error-handling.md 규칙("Error codes must be registered in this document before use")에 따라 해당 코드를 등록하거나, 이미 등록된 표준 코드(`CONFLICT`)로 교체해야 한다.

2. `specs/contracts/http/wishlist-api.md`의 `POST /api/wishlists` 에러 응답 표에 `USER_PROFILE_NOT_FOUND` (404) 항목이 누락되어 있다. 구현과 컨트랙트 간의 불일치를 해소해야 한다.

---

# Scope

## In Scope

- `specs/platform/error-handling.md`: `DATA_INTEGRITY_VIOLATION` 코드를 General 또는 적절한 섹션에 추가하거나, 해당 코드 사용을 제거하고 이미 등록된 표준 코드로 대체
- `specs/contracts/http/wishlist-api.md`: `POST /api/wishlists` 에러 응답 표에 `| 404 | USER_PROFILE_NOT_FOUND | user_profiles 행이 없는 유저 요청 |` 추가
- `apps/user-service/.../GlobalExceptionHandler.java`: `DATA_INTEGRITY_VIOLATION` 코드 처리 방향이 결정된 경우 코드 수정
- 관련 테스트 코드 갱신 (에러 코드 변경 시)

## Out of Scope

- 그 외 비즈니스 로직 변경
- 다른 엔드포인트 컨트랙트 수정

---

# Acceptance Criteria

- [ ] `specs/platform/error-handling.md`에 `DATA_INTEGRITY_VIOLATION` 코드가 등록되어 있거나, `GlobalExceptionHandler`가 등록된 표준 코드를 반환한다
- [ ] `specs/contracts/http/wishlist-api.md`의 `POST /api/wishlists` 에러 응답 표에 `404 USER_PROFILE_NOT_FOUND` 항목이 포함된다
- [ ] `WishlistControllerTest`의 `addItem_dataIntegrityViolation_returns409` 테스트가 최종 결정된 에러 코드를 검증한다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/services/user-service/architecture.md`

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

## 이슈 1: DATA_INTEGRITY_VIOLATION 처리

두 가지 선택지가 있다:

**Option A (권장)**: `DATA_INTEGRITY_VIOLATION` 코드를 `specs/platform/error-handling.md`의 General 섹션에 등록한다.
```
| DATA_INTEGRITY_VIOLATION | 409 | Data integrity constraint was violated |
```

**Option B**: `GlobalExceptionHandler`의 `handleDataIntegrityViolation`이 `CONFLICT` (이미 등록된 코드) 또는 `ALREADY_IN_WISHLIST` 등의 등록된 코드를 반환하도록 변경한다. 이 경우 `WishlistControllerTest`의 해당 테스트도 함께 수정한다.

## 이슈 2: 컨트랙트 갱신

`specs/contracts/http/wishlist-api.md`의 `POST /api/wishlists` 에러 응답 표에 아래 항목을 추가한다:
```
| 404 | USER_PROFILE_NOT_FOUND | user_profiles 행이 없는 유저 요청 |
```

---

# Edge Cases

- `DataIntegrityViolationException`이 unique constraint 위반(중복 찜)으로 발생하는 경우: 등록된 코드로 409 반환
- `DataIntegrityViolationException`이 FK 위반으로 발생하는 경우: 선체크가 주 방어선이므로 백스톱으로만 동작

---

# Failure Scenarios

- 에러 코드 변경 시 기존 테스트가 실패할 수 있음 → 테스트도 함께 수정

---

# Test Requirements

- `WishlistControllerTest.addItem_dataIntegrityViolation_returns409`: 최종 결정된 에러 코드로 검증
- 기존 테스트 전체 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
