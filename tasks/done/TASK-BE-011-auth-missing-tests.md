# Task ID

TASK-BE-011

# Title

auth-service 누락 테스트 추가 — LoginRateLimitFilter, RedisAccessTokenBlocklist, JsonAuthenticationEntryPoint, DTO 검증

# Status

review

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`specs/platform/testing-strategy.md`에 따르면 모든 구현에는 적절한 테스트가 있어야 한다. 현재 다음 컴포넌트에 테스트가 없다:

- `LoginRateLimitFilter`: 로그인 brute force 방지 핵심 보안 필터
- `RedisAccessTokenBlocklist`: logout 후 토큰 재사용 방지 컴포넌트
- `JsonAuthenticationEntryPoint`: 인증 실패 시 응답 형식 담당
- `LoginRequest`, `RefreshRequest`, `LogoutRequest` DTO 검증: `SignupRequest`는 테스트되지만 나머지 DTO 검증 테스트 없음

이 태스크 완료 후: 위 4개 영역에 테스트가 추가되고 테스트 커버리지가 개선된다.

---

# Scope

## In Scope

- `LoginRateLimitFilterTest.java` 생성 — rate limit 동작, IP 리졸빙, 429 응답 검증
- `RedisAccessTokenBlocklistTest.java` 생성 — block/isBlocked 동작, TTL 설정 검증 (단위 테스트)
- `JsonAuthenticationEntryPointTest.java` 생성 — 인증 실패 시 ErrorResponse JSON 형식 검증
- `LoginRequestTest.java`, `RefreshRequestTest.java`, `LogoutRequestTest.java` 생성 — 각 DTO의 validation 규칙 검증
- 기존 테스트와 중복되지 않도록 유의

## Out of Scope

- 통합 테스트 추가 (기존 통합 테스트에서 간접 검증 중)
- Rate Limiting 로직 자체 변경
- 기존 테스트 파일 수정 (테스트가 실패하는 경우 제외)

---

# Acceptance Criteria

- [x] `LoginRateLimitFilterTest`가 존재하고, rate limit 초과 시 429 응답 반환을 검증한다
- [x] `LoginRateLimitFilterTest`가 X-Forwarded-For 헤더로 IP가 리졸빙됨을 검증한다
- [x] `RedisAccessTokenBlocklistTest`가 존재하고, `block()` 후 `isBlocked()` 가 true를 반환함을 검증한다
- [x] `RedisAccessTokenBlocklistTest`가 block 시 TTL이 설정됨을 검증한다
- [x] `JsonAuthenticationEntryPointTest`가 존재하고, 401 응답이 `{ code, message, timestamp }` 형식의 JSON임을 검증한다
- [x] `LoginRequestTest`가 존재하고, email/password 필드 검증 규칙(공백, 최대 길이 등)을 검증한다
- [x] `RefreshRequestTest`가 존재하고, refreshToken 필드 필수 검증을 검증한다
- [x] `LogoutRequestTest`가 존재하고, refreshToken 필드 필수 검증을 검증한다
- [x] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/security-rules.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

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

테스트 계층:
- `LoginRateLimitFilterTest`: `@WebMvcTest` 슬라이스 테스트
- `RedisAccessTokenBlocklistTest`: 단위 테스트 (Mockito로 RedisTemplate mock)
- `JsonAuthenticationEntryPointTest`: 단위 테스트 (MockHttpServletRequest/Response 사용)
- DTO 검증 테스트: 단위 테스트 (`jakarta.validation` Validator 직접 사용)

---

# Implementation Notes

### LoginRateLimitFilterTest 접근법

`@WebMvcTest`에서 `LoginRateLimitFilter`를 `@Import`하고 `LoginRateLimiter`를 `@MockitoBean`으로 교체. rate limit 초과 시나리오는 mock에서 `false` 반환.

```java
@WebMvcTest(AuthController.class)
@Import({..., LoginRateLimitFilter.class})
class LoginRateLimitFilterTest {
    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @Test
    void whenRateLimitExceeded_returns429() {
        given(loginRateLimiter.tryAcquire(any())).willReturn(false);
        // ...
        .andExpect(status().isTooManyRequests());
    }
}
```

### RedisAccessTokenBlocklistTest 접근법

`RedisAccessTokenBlocklist`가 `StringRedisTemplate`에 의존하므로, mock 기반 단위 테스트.

### DTO 검증 테스트 접근법

`SignupRequestTest`와 동일하게 `jakarta.validation.Validator`를 직접 사용.

```java
private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
```

---

# Edge Cases

- `LoginRateLimitFilter`는 login 엔드포인트에만 적용되므로, 다른 엔드포인트 호출 시 rate limit 미적용을 검증
- `RedisAccessTokenBlocklist`에서 존재하지 않는 token에 대해 `isBlocked()` 가 false를 반환하는지 검증
- DTO 검증: null, blank, 경계값(max 길이) 케이스 포함

---

# Failure Scenarios

- `LoginRateLimitFilter` 테스트에서 필터 체인 설정 문제로 필터가 적용되지 않을 수 있음 → `@Import`와 필터 등록 순서 확인
- `RedisAccessTokenBlocklist` TTL 검증 시 Redis mock 동작 불일치 → `verify()`로 호출 인자 검증

---

# Test Requirements

- `LoginRateLimitFilterTest`: 슬라이스 테스트 (`@WebMvcTest`)
- `RedisAccessTokenBlocklistTest`: 단위 테스트 (Mockito)
- `JsonAuthenticationEntryPointTest`: 단위 테스트
- `LoginRequestTest`, `RefreshRequestTest`, `LogoutRequestTest`: 단위 테스트 (Validator)

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
