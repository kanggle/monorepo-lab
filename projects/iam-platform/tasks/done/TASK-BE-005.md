# Task ID

TASK-BE-005

# Title

GlobalExceptionHandler 베이스 클래스 추출 — libs/java-web 공통 예외 핸들러

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

account-service, membership-service, community-service 등 6개+ 서비스의 `GlobalExceptionHandler`가 아래 공통 예외 처리 메서드를 동일하게 복제하고 있다:
- `MethodArgumentNotValidException`
- `HttpMessageNotReadableException`
- `MissingRequestHeaderException`
- `MissingServletRequestParameterException`
- `IllegalArgumentException`
- `ObjectOptimisticLockingFailureException`

`libs/java-web`에 `CommonGlobalExceptionHandler` 베이스 클래스를 추가하고, 각 서비스의 `GlobalExceptionHandler`가 이를 상속하여 서비스별 예외만 오버라이드/추가하도록 한다.

---

# Scope

## In Scope

- `libs/java-web`에 `CommonGlobalExceptionHandler` 추가
  - 공통 HTTP/검증 예외 처리 메서드 6가지 이전
  - `@RestControllerAdvice` 적용
- 각 서비스 `GlobalExceptionHandler`가 `CommonGlobalExceptionHandler` 상속
- 서비스별 고유 예외 처리는 각 서비스 핸들러에 유지
- `platform/shared-library-policy.md` 준수

## Out of Scope

- 에러 응답 구조(JSON body 필드) 변경 없음
- HTTP 상태 코드 변경 없음
- gateway-service 포함 여부는 기존 코드 확인 후 결정

---

# Acceptance Criteria

- [ ] `libs/java-web`에 `CommonGlobalExceptionHandler`가 추가된다
- [ ] 6개 공통 예외 처리 메서드가 베이스 클래스로 이전된다
- [ ] 각 서비스 `GlobalExceptionHandler`가 베이스 클래스를 상속한다
- [ ] 각 서비스에서 공통 메서드 중복이 제거된다
- [ ] 에러 응답 구조가 변경되지 않는다
- [ ] `libs/java-web`이 서비스 모듈에 의존하지 않는다 (단방향 의존)
- [ ] 베이스 클래스 단위 테스트 추가
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `platform/shared-library-policy.md`
- `specs/services/account-service/architecture.md`

# Related Skills

- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 에러 응답 구조 변경 없음

---

# Target Service

- `libs/java-web`
- `account-service`, `admin-service`, `auth-service`, `community-service`, `membership-service`, `security-service`

---

# Architecture

Follow:

- `platform/shared-library-policy.md`
- 각 서비스의 presentation 계층 규칙

---

# Implementation Notes

- `CommonGlobalExceptionHandler`는 `@RestControllerAdvice`를 붙이지 않고, 상속받는 서비스 핸들러에 `@RestControllerAdvice`를 유지한다. 또는 베이스에 붙이고 서비스 핸들러에서 `@RestControllerAdvice`를 제거 — 스프링 컨텍스트 동작 확인 필요.
- 에러 응답 DTO(`ErrorResponse` 등)가 서비스마다 다를 경우 공통 DTO도 `libs/java-web`으로 이전 가능 (Decision Rule 확인).
- `libs/java-web`은 Spring Web 의존성만 가지며 서비스 도메인 클래스를 import하지 않는다.

---

# Edge Cases

- 서비스마다 `ErrorResponse` 구조가 다른 경우 → 공통 DTO 사용 강제하지 않고 베이스에서 `protected` 메서드로 오버라이드 허용
- 동일한 예외 타입을 서비스가 다르게 처리해야 하는 경우 → 서비스 핸들러에서 `@ExceptionHandler` 오버라이드

---

# Failure Scenarios

- 동일한 예외 타입에 대해 베이스 + 서비스 핸들러 두 곳에 `@ExceptionHandler`가 존재 → Spring이 중복 매핑 감지, 스타트업 실패
- `libs/java-web`이 서비스 도메인 예외를 import → 순환 의존, 빌드 실패

---

# Test Requirements

- `CommonGlobalExceptionHandler` 단위 테스트 (MockMvc 슬라이스, 각 예외 유형별 응답 검증)
- 각 서비스 `GlobalExceptionHandler` 테스트: 서비스별 예외가 올바르게 처리되는지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
