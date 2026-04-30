# Task ID

TASK-BE-119

# Title

fix(TASK-BE-005): security-service QueryExceptionHandler CommonGlobalExceptionHandler 미적용

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

TASK-BE-005에서 `libs/java-web`에 `CommonGlobalExceptionHandler` 베이스 클래스를 추출하고 5개 서비스에 적용하였으나, **`security-service`의 `QueryExceptionHandler`는 여전히 `CommonGlobalExceptionHandler`를 상속하지 않고 공통 예외 처리 메서드를 직접 중복 구현**하고 있다.

Task의 Acceptance Criteria "각 서비스에서 공통 메서드 중복이 제거된다"와 Target Service 목록(security-service 포함)을 위반한다.

해당 파일:
- `apps/security-service/src/main/java/com/example/security/query/internal/QueryExceptionHandler.java`

중복 핸들러:
- `@ExceptionHandler(MissingServletRequestParameterException.class)`
- `@ExceptionHandler(IllegalArgumentException.class)`

---

# Scope

## In Scope

- `QueryExceptionHandler`가 `CommonGlobalExceptionHandler`를 상속하도록 수정
- 중복 공통 메서드(`handleMissingParam`, `handleIllegalArgument`) 제거
- `security-service`의 `build.gradle`에 `libs/java-web` 의존성 추가 (미포함 시)
- 기존 동작(응답 형식, HTTP 상태 코드) 유지
- 기존 서비스별 고유 핸들러(`handleTypeMismatch`) 유지

## Out of Scope

- 다른 서비스 핸들러 변경 없음
- `platform/error-handling.md` 에러 코드 변경 없음

---

# Acceptance Criteria

- [ ] `QueryExceptionHandler`가 `CommonGlobalExceptionHandler`를 상속한다
- [ ] 중복 공통 메서드가 제거된다
- [ ] `security-service` 빌드 및 테스트가 통과한다
- [ ] `@RestControllerAdvice(basePackages = ...)` 범위가 유지된다
- [ ] 에러 응답 구조(code, message, timestamp)가 변경되지 않는다

---

# Related Specs

- `platform/shared-library-policy.md`
- `specs/services/account-service/architecture.md` (의존성 방향 참조)

# Related Skills

- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 에러 응답 구조 변경 없음

---

# Target Service

- `apps/security-service`

---

# Architecture

Follow:

- `platform/shared-library-policy.md`
- 각 서비스의 presentation 계층 규칙

---

# Implementation Notes

- `QueryExceptionHandler`는 `@RestControllerAdvice(basePackages = "com.example.security.query.internal")`를 유지해야 한다.
- 베이스 클래스는 `@RestControllerAdvice` 없음 — 서브클래스가 보유.
- `security-service`의 `build.gradle`에 `implementation project(':libs:java-web')` 추가 필요 (없을 경우).
- `QueryExceptionHandler`의 `buildErrorResponse` private 메서드와 `Map<String, Object>` 반환 타입은 `ErrorResponse` 레코드로 교체한다 (libs/java-web의 `ErrorResponse.of()` 사용).

---

# Edge Cases

- `QueryExceptionHandler`가 `Map<String, Object>` 반환 방식을 사용하는 경우 → `ErrorResponse` 레코드로 일관성 있게 교체

---

# Failure Scenarios

- `libs/java-web` 미의존 상태에서 `CommonGlobalExceptionHandler` import → 컴파일 오류; `build.gradle` 수정 선행 필요

---

# Test Requirements

- `QueryExceptionHandler` 단위 또는 슬라이스 테스트: `MissingServletRequestParameterException`, `IllegalArgumentException` 처리 검증 (기존 테스트 커버리지 확인 및 추가)
