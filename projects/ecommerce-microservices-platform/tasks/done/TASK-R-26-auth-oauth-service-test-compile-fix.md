# Task ID

TASK-R-26

# Title

auth-service OAuthServiceTest compileTestJava 실패 수정 (AuthEventPublisher 인자 추가)

# Status

review

# Owner

backend

# Task Tags

- test
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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`OAuthService`가 OAuth 신규 가입 시 `UserSignedUp` 도메인 이벤트를 발행하도록 확장되어 생성자에 `AuthEventPublisher eventPublisher` 파라미터(10번째 인자)가 추가되었으나, `OAuthServiceTest`는 기존 9개 인자만 전달하고 있어 `compileTestJava` 단계에서 실패한다. 테스트에 `AuthEventPublisher` mock을 추가하여 컴파일 및 기존 검증을 복구한다.

---

# Scope

## In Scope

- `apps/auth-service/src/test/java/com/example/auth/application/service/OAuthServiceTest.java`에 `@Mock AuthEventPublisher authEventPublisher` 필드 추가
- `setUp()`의 `new OAuthService(...)` 호출에 10번째 인자로 해당 mock 전달
- 필요 시 OAuth 신규 가입 시나리오 테스트에 이벤트 발행 검증(`then(authEventPublisher).should().publish(...)`) 추가 — 기존 검증 깨짐 없이 이벤트 publish가 호출되는 것만 확인
- auth-service 빌드 및 단위 테스트 통과

## Out of Scope

- `OAuthService` 또는 `AuthEventPublisher` 프로덕션 코드 수정
- 다른 auth-service 테스트 전면 리팩터링
- 새로운 OAuth 시나리오(예: 가입 재시도, 실패 플로우) 테스트 추가
- `specs/services/auth-service/` 스펙 변경

---

# Acceptance Criteria

- [ ] `./gradlew :apps:auth-service:compileTestJava`가 성공한다
- [ ] `./gradlew :apps:auth-service:test`가 성공한다 (기존 통과하던 테스트 + `OAuthServiceTest` 포함)
- [ ] 신규 가입 시나리오가 있다면 해당 테스트에서 `authEventPublisher.publish(...)` 호출이 검증된다
- [ ] mock 선언/주입 스타일이 파일 내 기존 mock과 일관된다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/testing/` (관련 스킬이 있다면 참조)

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (`UserSignedUp` 이벤트)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

- 현재 `OAuthService` 생성자 시그니처 ([OAuthService.java:52-61](apps/auth-service/src/main/java/com/example/auth/application/service/OAuthService.java#L52-L61)):
  - `List<OAuthProvider>, OAuthStateStore, UserRepository, RefreshTokenStore, TokenGenerator, TokenProperties, SessionProperties, UserSessionRegistry, OAuthCallbackProperties, AuthEventPublisher`
- 이벤트 publish는 `getOrCreateUser` 경로 내 신규 생성 분기에서 `UserSignedUp` 발행 ([OAuthService.java:158](apps/auth-service/src/main/java/com/example/auth/application/service/OAuthService.java#L158))
- 테스트 파일: [OAuthServiceTest.java:59-64](apps/auth-service/src/test/java/com/example/auth/application/service/OAuthServiceTest.java#L59-L64)
- `AuthEventPublisher`는 도메인 포트(인터페이스)로 확인 필요, Mockito `@Mock`으로 충분

## 구현 결과

- [OAuthServiceTest.java](apps/auth-service/src/test/java/com/example/auth/application/service/OAuthServiceTest.java) 수정
  - `AuthEventPublisher` import 및 `@Mock` 필드 추가
  - `setUp()`의 `new OAuthService(...)` 호출에 10번째 인자로 `authEventPublisher` 전달
  - `handleCallback_newUser_createsAndIssuesTokens`에 `then(authEventPublisher).should().publish(any())` 검증 추가
- `./gradlew :apps:auth-service:compileTestJava` BUILD SUCCESSFUL
- `./gradlew :apps:auth-service:test` 결과:
  - `OAuthServiceTest`: 12개 테스트 전부 통과 (failures=0, errors=0)
  - 전체 329개 테스트 중 1건 실패 — `AuthAuditLogIntegrationTest.login_xForwardedFor_lastIpStored` (expected `3.3.3.3` but got `1.1.1.1`)
  - 이 실패는 X-Forwarded-For IP 파싱 관련 기존 이슈로, 최근 커밋 "ClientIpResolver 패키지 이동" 영향 가능성. 본 태스크(OAuthServiceTest 컴파일 복구) 범위 밖이며 별도 fix 태스크로 분리 필요

---

# Edge Cases

- 기존 signup 시나리오 테스트에서 이벤트 publish stub 누락 시 NPE/검증 실패 → mock 기본 리턴값(void) 허용 확인
- `@Mock` 필드 순서와 생성자 인자 순서가 불일치하면 가독성 저하 → 생성자 파라미터 순서대로 정렬

---

# Failure Scenarios

- 인자 타입 불일치로 컴파일 실패 → `AuthEventPublisher` 정확한 import 확인
- 기존 테스트가 mock 동작 검증 `verifyNoInteractions` 등을 사용 중이라면 신규 mock이 호출되는 시나리오에서 실패 → 필요 시 해당 검증 보강

---

# Test Requirements

- `./gradlew :apps:auth-service:test` 전체 통과
- 회귀 검증: `OAuthServiceTest` 기존 테스트 메서드는 모두 유지 및 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
