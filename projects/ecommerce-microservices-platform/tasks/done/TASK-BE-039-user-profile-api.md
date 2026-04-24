# Task ID

TASK-BE-039

# Title

사용자 프로필 조회/수정 API + UserProfileUpdated 이벤트 발행

# Status

review

# Owner

backend

# Task Tags

- code
- api
- event

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

인증된 사용자가 자신의 프로필을 조회하고 수정할 수 있는 API를 구현한다. 프로필 수정 시 UserProfileUpdated 이벤트를 발행한다. 관리자용 사용자 목록/상세 조회 API도 함께 구현한다.

이 태스크 완료 후: GET /api/users/me, PATCH /api/users/me, GET /api/admin/users, GET /api/admin/users/{userId} API가 동작하고, 프로필 수정 시 이벤트가 발행된다.

---

# Scope

## In Scope

- `GET /api/users/me` — 내 프로필 조회
- `PATCH /api/users/me` — 내 프로필 수정 (nickname, phone, profileImageUrl)
- `GET /api/admin/users` — 사용자 목록 조회 (페이지네이션, 필터)
- `GET /api/admin/users/{userId}` — 특정 사용자 프로필 조회
- UserProfileUpdated 이벤트 발행 (Kafka)
- 입력 유효성 검증 (Bean Validation)
- GlobalExceptionHandler 구성

## Out of Scope

- 주소 관리 API (TASK-BE-040)
- 사용자 탈퇴 (UserWithdrawn 이벤트 — 향후 태스크)
- 사용자 정지/복구 (관리자 기능 — 향후 태스크)

---

# Acceptance Criteria

- [ ] `GET /api/users/me` — X-User-Id 헤더 기반 프로필 반환 (200)
- [ ] `GET /api/users/me` — 프로필 미존재 시 404 USER_PROFILE_NOT_FOUND
- [ ] `PATCH /api/users/me` — nickname, phone, profileImageUrl 부분 수정 (200)
- [ ] `PATCH /api/users/me` — 유효하지 않은 필드 시 400 VALIDATION_ERROR
- [ ] `PATCH /api/users/me` — 성공 시 UserProfileUpdated 이벤트 발행
- [ ] `GET /api/admin/users` — 페이지네이션 응답 (page, size, totalElements)
- [ ] `GET /api/admin/users?status=ACTIVE` — status 필터 동작
- [ ] `GET /api/admin/users?email=test` — email 부분 검색 동작
- [ ] `GET /api/admin/users/{userId}` — 존재하지 않는 userId 시 404
- [ ] 응답 형식이 계약(`specs/contracts/http/user-api.md`)과 일치한다
- [ ] 에러 응답이 플랫폼 에러 형식을 따른다
- [ ] Controller 테스트, Service 테스트, 통합 테스트가 통과한다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/platform/api-gateway-policy.md`
- `specs/platform/security-rules.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/coding-rules.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/http/user-api.md`
- `specs/contracts/events/user-events.md` (UserProfileUpdated — 발행)

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

계층 배치:
- Presentation: `UserController`, `AdminUserController`, Request/Response DTO
- Application: `UserProfileService` — 조회, 수정 유스케이스
- Domain: `UserProfile` 엔티티 (TASK-BE-038에서 생성됨)
- Infrastructure: Kafka 프로듀서 (UserProfileUpdated 발행)

---

# Implementation Notes

### 인증 및 권한

- 일반 사용자 API(`/api/users/me`): X-User-Id 헤더 사용 (gateway에서 주입)
- 관리자 API(`/api/admin/users`): admin role 체크 필요
- X-User-Id가 없으면 401 반환

### 이벤트 발행

- 토픽: `user.user-profile.updated`
- 이벤트 envelope 형식 준수 (`event-driven-policy.md`)
- 트랜잭션 커밋 후 발행 (transactional outbox 또는 @TransactionalEventListener)

### 부분 수정

- PATCH 요청에서 null이 아닌 필드만 수정
- email, name은 수정 불가 (auth-service 소유)

---

# Edge Cases

- X-User-Id 헤더 없이 요청 → 401 UNAUTHORIZED
- 프로필이 아직 생성되지 않은 사용자 (UserSignedUp 이벤트 미수신) → 404
- nickname에 빈 문자열 전달 → null로 처리하거나 VALIDATION_ERROR
- admin/users에서 page=음수 → 0으로 보정 또는 400
- 동시에 같은 프로필 수정 → 낙관적 잠금으로 처리

---

# Failure Scenarios

- Kafka 발행 실패 → 프로필 수정은 성공하되, 이벤트 발행 재시도 또는 로그 경고
- DB 조회 실패 → 500 INTERNAL_ERROR
- 관리자 인증 실패 → 403 Forbidden

---

# Test Requirements

- 단위 테스트: `UserProfileServiceTest` — 조회, 수정 로직 검증
- Controller 테스트: `UserControllerTest` — 요청/응답 매핑, 유효성 검증
- Controller 테스트: `AdminUserControllerTest` — 목록 조회, 필터, 페이지네이션
- 통합 테스트: API 엔드포인트 전체 흐름 (Testcontainers)
- 통합 테스트: 프로필 수정 → Kafka 이벤트 발행 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
