# Task ID

TASK-BE-151

# Title

gateway-service 통합 테스트 커버리지 보강 — rate limit · JWKS rotation · 다운스트림 오류 · force-invalidated token

# Status

ready

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

---

# Goal

기존 `GatewayIntegrationTest`(공개 경로, JWT 검증, 헤더 주입)는 13개 시나리오를 커버하지만, `specs/services/gateway-service/architecture.md`의 Testing Expectations에 명시된 다음 4개 필수 시나리오가 빠져 있다.

1. **Rate limit 429 전이** — scope 초과 시 429 + `Retry-After` 헤더
2. **JWKS rotation 무중단** — kid 불일치 → 즉시 refetch → 새 key로 검증 성공
3. **다운스트림 오류 → 503** — 연결 실패 / HTTP 5xx 시 `SERVICE_UNAVAILABLE`
4. **force-invalidated access token** — Redis `access:invalidate-before:{accountId}` 키보다 오래된 `iat` → 401

이 4개 시나리오를 커버하는 통합 테스트 2개 파일을 추가한다.

---

# Scope

## In Scope

- `GatewayRateLimitIntegrationTest.java` 신규 추가
  - login scope 초과 → 429 + `Retry-After` 헤더
  - 다운스트림 연결 실패(WireMock 정지) → 503 `SERVICE_UNAVAILABLE`
- `GatewayResilienceIntegrationTest.java` 신규 추가
  - JWKS rotation: kid 불일치 → `refreshJwks()` → 새 kid로 검증 성공
  - 다운스트림 HTTP 500 → 투명 전달(500) 확인
  - force-invalidated access token → 401

## Out of Scope

- 기존 `GatewayIntegrationTest.java` 수정
- 새로운 프로덕션 코드 변경
- Contract 테스트 도구(Pact / Spring Cloud Contract) 도입
- 다른 서비스 통합 테스트

---

# Acceptance Criteria

- [ ] `GatewayRateLimitIntegrationTest`: login scope `max-requests=2` 오버라이드, 3번째 요청 → 429 + `Retry-After` 헤더 존재
- [ ] `GatewayRateLimitIntegrationTest`: 다운스트림 WireMock 정지 후 요청 → 503 `SERVICE_UNAVAILABLE`
- [ ] `GatewayResilienceIntegrationTest`: kid-2 키페어로 서명한 토큰, Redis에 kid-1만 캐시된 상태 → `refreshJwks()` 호출 후 200
- [ ] `GatewayResilienceIntegrationTest`: 다운스트림 HTTP 500 → 클라이언트에게 500 전달(5xx 투명 전달)
- [ ] `GatewayResilienceIntegrationTest`: Redis `access:invalidate-before:{accountId}` 값 > 토큰 iat → 401 `TOKEN_INVALID`
- [ ] `./gradlew :apps:gateway-service:test` BUILD SUCCESS

---

# Related Specs

- `specs/services/gateway-service/architecture.md` §Testing Expectations, §필수 시나리오
- `specs/features/rate-limiting.md`
- `platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/gateway-api.md`
- `specs/contracts/http/internal/gateway-to-auth.md`

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- gateway-service는 WebFlux(reactive). `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`
- Testcontainers: `com.redis:testcontainers-redis` 사용 (기존 패턴 동일)
- Rate limit 테스트에서 `gateway.rate-limit.login.max-requests=2`를 `@DynamicPropertySource`로 오버라이드 → 별도 Spring context
- JWKS rotation 시나리오: Redis에서 `jwks:cache` 키 삭제 후 kid-2 토큰 요청 → `JwksCache.getKeysFromRedis()` miss → `refreshJwks()` → WireMock kid-2 JWKS 반환 → 검증
- `access:invalidate-before:{accountId}` 키에 현재 epoch millis 이상의 값 저장 후, 그 이전 iat로 서명된 토큰 요청 → 401
- `GatewayErrorConfig`: `ConnectException` → 503, 다운스트림 HTTP 5xx는 그대로 프록시(투명)

---

# Edge Cases

- Rate limit: IP 격리 — 다른 IP는 별도 카운터 (X-Forwarded-For 헤더로 IP 지정)
- JWKS rotation: kid 동일, 서명키만 변경 시 → 캐시된 public key로 검증 실패 → 재시도 없이 401 (current behavior)
- force-invalidated token: Redis 키 없으면 fail-open (기존 동작)
- Redis 불가 시 rate limit fail-open (`EdgeGatewayProperties.rateLimit.failOpen=true`)

---

# Failure Scenarios

- WireMock JWKS 엔드포인트 500 → `JwksCache.refreshJwks()` 실패 → in-memory fallback 또는 401
- Redis 불가 + WireMock 정상 → rate limit fail-open, JWT 검증은 WireMock fetch 경유 진행
- kid 불일치 + WireMock JWKS에도 없는 kid → 401 `TOKEN_INVALID`

---

# Test Requirements

- Integration (Testcontainers Redis + WireMock): 5개 신규 시나리오
- 기존 unit/filter 테스트: 변경 없음

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
