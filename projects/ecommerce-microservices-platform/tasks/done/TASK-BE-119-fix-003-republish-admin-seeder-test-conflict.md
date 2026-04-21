# Task ID

TASK-BE-119-fix-003

# Title

TASK-BE-118-fix-001 리뷰 fix: RepublishSignupEventsIntegrationTest AdminAccountSeeder 충돌 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- fix

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

`RepublishSignupEventsIntegrationTest.republish_zeroUsers()` 테스트가 `AdminAccountSeeder` 때문에 실패하는 문제를 수정한다.

현재 상태:
- `AdminAccountSeeder`(ApplicationRunner)가 `@SpringBootTest` 기동 시 admin 유저 1건을 DB에 삽입
- `@AfterEach cleanup()`은 `DELETE FROM users`로 정리하지만, 첫 번째 테스트 실행 전 정리 로직이 없음
- `republish_zeroUsers()`가 실행될 때 admin 유저가 존재하여 `totalUsers=0` 기댓값이 실패

수정:
- `@BeforeEach` 메서드 추가: `DELETE FROM users` (또는 seeder가 삽입한 admin 유저만 삭제)
- 또는 `AdminAccountSeeder`에 `@Profile("!test")` 또는 조건부 시딩 로직 추가

---

# Scope

## In Scope

- `apps/auth-service/src/test/java/com/example/auth/RepublishSignupEventsIntegrationTest.java`에 `@BeforeEach` 정리 로직 추가
- 또는 `apps/auth-service/src/main/java/com/example/auth/infrastructure/config/AdminAccountSeeder.java`에 테스트 환경 제외 처리 추가
- 두 방법 중 테스트 격리 관점에서 더 안전한 방법 선택

## Out of Scope

- 다른 통합 테스트 파일 변경
- AdminAccountSeeder 기능 변경

---

# Acceptance Criteria

- [ ] `RepublishSignupEventsIntegrationTest.republish_zeroUsers()` 테스트 통과
- [ ] 기존 `republish_oneHundredUsers()` 및 `republish_partialFailure()` 테스트도 통과
- [ ] 기존 auth-service 테스트 전체 빌드/통과

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

없음 (변경 없음)

---

# Target Service

- `auth-service`

---

# Edge Cases

- `AdminAccountSeeder`에 `@Profile("!test")` 추가 시 다른 통합 테스트에서 admin 유저 존재를 가정하는 테스트 주의

---

# Failure Scenarios

- `@BeforeEach`에서 `DELETE FROM users`가 참조 무결성 위반 → 연관 테이블도 함께 정리 필요 시 CASCADE 또는 순서 조정

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Ready for review
