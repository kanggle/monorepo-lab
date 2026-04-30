# Task ID

TASK-BE-065

# Title

fix(account-service): AuthServiceUnavailable → 503 + integration rollback test (TASK-BE-063 review)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

# depends_on

- TASK-BE-063

---

# Goal

TASK-BE-063 사후 리뷰(#16)에서 드러난 두 건의 gap 수정:

1. `AuthServicePort.AuthServiceUnavailable` 이 `GlobalExceptionHandler` 에서 매핑되지 않아, signup 엔드포인트가 auth-service 장애 시 **503 SERVICE_UNAVAILABLE 대신 500 INTERNAL_ERROR** 반환. 스펙(`auth-internal.md` §Failure Scenarios: "signup 은 503 fail-closed")과 불일치. `@Transactional` 롤백 자체는 정상이지만 HTTP status code 가 틀려 gateway 및 호출측 관찰 행위가 일관되지 않음.
2. 통합 테스트 suite 에 auth-service 장애 시 `@Transactional` 롤백 경로를 검증하는 케이스 없음. 현재 `SignupUseCaseTest` (unit) 만 덮고 있고, 실 DB 와 섞인 `@Transactional` boundary 가 실제로 롤백하는지 integration 레벨 검증 부재.

---

# Scope

## In Scope

1. `GlobalExceptionHandler` 에 `@ExceptionHandler(AuthServicePort.AuthServiceUnavailable.class)` 추가. 응답:
   ```json
   {
     "code": "AUTH_SERVICE_UNAVAILABLE",
     "message": "Authentication service is temporarily unavailable",
     "timestamp": "..."
   }
   ```
   Status `503`.
2. `AccountSignupIntegrationTest` 확장 (또는 신규 `SignupRollbackIntegrationTest`):
   - WireMock 을 MySQL 컨테이너와 함께 시작
   - `POST /internal/auth/credentials` → 500 stub
   - `POST /api/accounts/signup` 호출 → **503** 응답 확인
   - DB 에 해당 email 의 `accounts` / `profiles` row 가 **없음** 확인 (롤백 검증)
3. `AuthServiceClient.maxAttempts(3)` 에 설명 주석 추가 — "1 initial + 2 retries per auth-internal.md '2회 재시도'" (향후 유지보수 혼란 방지).

## Out of Scope

- resilience4j 설정 값 변경
- `RedisIdempotencyStore` 관련 추가 대응 (별도 task — 리뷰의 "warning" 은 본 fix-task 스코프 아님. observability 알림/metric 은 plat-ops 성격)
- contract 변경 (`account-api.md` 에 `AUTH_SERVICE_UNAVAILABLE` 에러 코드 추가는 이 PR 에서 같이, 별도 task 아님)

---

# Acceptance Criteria

- [ ] `POST /api/accounts/signup` 이 auth-service 5xx 응답 시 `503` + `{"code":"AUTH_SERVICE_UNAVAILABLE"}` 반환
- [ ] 통합 테스트: WireMock 500 stub 로 재현한 롤백 시나리오에서 `accounts` / `profiles` row 0건
- [ ] `GlobalExceptionHandler` 의 기존 handler 회귀 없음 (`AccountAlreadyExistsException` 409, etc.)
- [ ] `AuthServiceClient.maxAttempts(3)` 주석 추가
- [ ] `specs/contracts/http/account-api.md` 에 `503 AUTH_SERVICE_UNAVAILABLE` 에러 코드 기재 (`POST /api/accounts/signup` 섹션)
- [ ] `./gradlew :apps:account-service:test` green
- [ ] `./gradlew build` CI green

---

# Related Specs

- `specs/contracts/http/internal/auth-internal.md` (§Failure Scenarios — signup 503 fail-closed)
- `platform/error-handling.md`
- `platform/testing-strategy.md`

---

# Related Contracts

- `specs/contracts/http/account-api.md` (signup error codes — `AUTH_SERVICE_UNAVAILABLE` 추가)

---

# Target Service

- `apps/account-service`

---

# Architecture

layered 4-layer. 변경 범위는 presentation layer (exception handler) 와 test. 구조 변경 없음.

---

# Edge Cases

- `AuthServiceUnavailable` 이 `CallNotPermittedException` (circuit open) 을 감싸는 경우에도 동일하게 503 반환되는지 확인. 현재 `AuthServiceClient.createCredential` 가 모든 CB open 케이스를 `AuthServiceUnavailable` 로 승격하므로 handler 하나로 커버됨.
- integration test 에서 WireMock 을 비활성화(미기동) 하는 분기도 `AuthServiceUnavailable` 경로로 들어오는지 (connection refused 케이스) 간접 검증.

---

# Failure Scenarios

- 통합 테스트의 WireMock 셋업 실패 시 sibling 테스트와 동일하게 `@EnabledIf("isDockerAvailable")` 로 가드.
- 핸들러 추가로 기존 `Exception` catch-all 경로의 다른 RuntimeException 이 403/404 등으로 잘못 분기되지 않도록 regression 테스트 (handler 우선순위 확인).

---

# Test Requirements

- unit 테스트: `GlobalExceptionHandler` 의 `AuthServiceUnavailable` 매핑을 `@WebMvcTest` slice 로 검증 (기존 `InternalControllerTest` 패턴 재사용 가능).
- integration 테스트: 위 Scope 2 항목.

---

# Definition of Done

- [ ] Handler + contract + test 3 항목 구현
- [ ] `./gradlew build` green
- [ ] Ready for review
