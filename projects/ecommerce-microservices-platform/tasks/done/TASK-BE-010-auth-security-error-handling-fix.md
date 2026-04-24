# Task ID

TASK-BE-010

# Title

auth-service 보안 및 에러 처리 수정 — Authorization 헤더 검증, AccessDeniedException 처리, 에러 코드 등록

# Status

review

# Owner

backend

# Task Tags

- code
- api

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

세 가지 문제를 수정한다.

**문제 1: Authorization 헤더 안전성 부족**

`AuthController.logout()`에서 `authorizationHeader.substring(7)`을 수행하기 전에 헤더 길이를 검증하지 않는다. "Bearer " 미만 길이의 값이 전달되면 `StringIndexOutOfBoundsException`이 발생한다.

**문제 2: AccessDeniedException 처리 불명확**

`GlobalExceptionHandler`에서 `AccessDeniedException`을 catch 후 다시 throw한다. 이는 의도가 불명확하고, 다른 예외들과 일관성이 없다. Spring Security가 이를 처리해야 하지만, 명시적인 handler가 없다.

**문제 3: RATE_LIMIT_EXCEEDED 에러 코드 미등록**

`LoginRateLimitFilter`가 `RATE_LIMIT_EXCEEDED` 코드를 사용하지만 `specs/platform/error-handling.md`에 등록되어 있지 않다. 스펙 규칙("에러 코드는 사용 전 문서에 등록되어야 한다")을 위반한다.

이 태스크 완료 후: Authorization 헤더가 안전하게 파싱되고, 에러 처리가 일관성 있으며, RATE_LIMIT_EXCEEDED가 공식 에러 코드로 등록된다.

---

# Scope

## In Scope

- `AuthController.logout()`에서 Authorization 헤더 파싱 안전화
- `GlobalExceptionHandler`에서 `AccessDeniedException`에 대한 명시적 handler 추가 (403 반환)
- `GlobalExceptionHandler`에서 `AccessDeniedException` rethrow 분기 제거
- `specs/platform/error-handling.md`에 `RATE_LIMIT_EXCEEDED | 429 | Too many login attempts` 추가
- 관련 테스트 수정/추가

## Out of Scope

- Rate Limiting 로직 자체 변경
- X-Forwarded-For 신뢰 정책 변경 (별도 태스크)
- 다른 보안 필터 변경

---

# Acceptance Criteria

- [x] `AuthController.logout()`에서 `Authorization` 헤더가 `"Bearer "` prefix를 포함하지 않으면 400을 반환한다
- [x] `GlobalExceptionHandler`에 `AccessDeniedException` 전용 handler가 존재하고 HTTP 403을 반환한다
- [x] `GlobalExceptionHandler`에 `AccessDeniedException` rethrow 분기가 없다
- [x] `specs/platform/error-handling.md`의 Standard Error Codes에 `RATE_LIMIT_EXCEEDED | 429 | Too many login attempts` 항목이 존재한다
- [x] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/platform/security-rules.md`
- `specs/services/auth-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어:
- presentation: `AuthController` 헤더 파싱 수정
- presentation: `GlobalExceptionHandler` AccessDeniedException handler 추가
- specs: `error-handling.md` 에러 코드 등록

---

# Implementation Notes

### Authorization 헤더 파싱 수정

```java
// AuthController.java
String authorizationHeader = ...; // @RequestHeader("Authorization")
if (!authorizationHeader.startsWith("Bearer ")) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Authorization header");
}
String accessToken = authorizationHeader.substring(7);
```

단, `SecurityConfig`에서 이미 Bearer 토큰 검증이 수행되므로, Spring Security 필터 체인이 먼저 처리한다. logout 엔드포인트는 `Auth required: Yes`이므로, 잘못된 헤더는 필터에서 이미 거부된다. 이 경우 `substring(7)` 시점에 도달하면 헤더는 이미 유효한 Bearer 형식임이 보장된다.

검토 후 필터에서 완전히 처리된다면, 방어적 검증 추가만으로 충분.

### GlobalExceptionHandler AccessDeniedException 처리

```java
@ExceptionHandler(AccessDeniedException.class)
@ResponseStatus(HttpStatus.FORBIDDEN)
public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
    return ErrorResponse.of("FORBIDDEN", "Access denied");
}
```

단, `AccessDeniedException`을 handler에서 처리하면 Spring Security의 기본 AccessDeniedHandler가 호출되지 않을 수 있으므로, SecurityConfig의 `accessDeniedHandler` 설정과 충돌 여부를 확인해야 한다.

### error-handling.md 추가

```markdown
## Rate Limiting

| Code | HTTP | Description |
|---|---|---|
| RATE_LIMIT_EXCEEDED | 429 | Too many login attempts. Try again later. |
```

---

# Edge Cases

- `Authorization: Bearer` (토큰 없이 prefix만 있는 경우) → `substring(7)` 후 빈 문자열 → JWT 파싱 실패로 자연스럽게 401
- `Authorization: bearer token` (소문자) → Spring Security 필터에서 이미 처리
- `AccessDeniedException`이 Spring Security 필터 체인에서 발생하는 경우 → 필터가 먼저 처리, handler는 컨트롤러 내부 예외에만 적용

---

# Failure Scenarios

- `AccessDeniedException` handler 추가 후 Spring Security 기본 동작과 충돌 → SecurityConfig 확인 필요
- RATE_LIMIT_EXCEEDED를 에러 코드 문서에 추가했지만 HTTP 상태 코드(429)를 플랫폼 스펙에서 지원하지 않는 경우 → error-handling.md의 HTTP Status Code Mapping 테이블도 함께 업데이트

---

# Test Requirements

- 슬라이스 테스트: logout에서 Authorization 헤더 형식이 잘못된 경우 처리 검증
- 슬라이스 테스트: 403 응답이 표준 ErrorResponse 형식으로 반환되는지 검증

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
