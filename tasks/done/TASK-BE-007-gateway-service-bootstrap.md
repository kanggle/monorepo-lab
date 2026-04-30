# Task ID

TASK-BE-007

# Title

gateway-service 부트스트랩 — JWT 검증 필터, rate limit, 라우팅, JWKS 캐시

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-004
- TASK-BE-005

---

# Goal

gateway-service를 Spring Cloud Gateway 기반으로 초기화하고, JWT 인증 필터, Redis 기반 rate limit, 다운스트림 라우팅, JWKS 캐시의 최소 골든패스를 구현한다.

---

# Scope

## In Scope

- `apps/gateway-service/` 모듈 생성
- 패키지 구조: `filter / route / security / ratelimit / config` ([architecture.md](../../specs/services/gateway-service/architecture.md))
- `JwtAuthenticationFilter`: RS256 서명 검증 + `exp`/`nbf` 확인 + `X-Account-ID` 헤더 주입 + spoofed 헤더 제거
- `RateLimitFilter`: Redis 토큰 버킷 (`login`, `signup`, `refresh`, `global` scopes)
- `RequestIdFilter`: `X-Request-ID` 생성/전파
- 라우트 설정: `/api/auth/*` → auth-service, `/api/accounts/*` → account-service, `/api/admin/*` → admin-service (별도 필터 체인 예약)
- JWKS 캐시: auth-service `/internal/auth/jwks` 10분 주기 페치 → Redis 캐시
- `libs/java-security` JwtVerifier 사용
- 에러 응답: 401/429/503/504 ([gateway-api.md](../../specs/contracts/http/gateway-api.md))
- 헬스체크: `/actuator/health`

## Out of Scope

- admin-service 라우트의 별도 인증 필터 (TASK-BE-010 admin bootstrap에서)
- CORS 동적 설정 (기본값 `.env` 기반)
- Resilience4j circuit breaker (향후 개선)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:gateway-service:bootRun` 성공, `/actuator/health` → 200
- [ ] 인증 불필요 경로 (`/api/auth/login`, `/api/accounts/signup`) → 다운스트림 정상 전달
- [ ] 인증 필요 경로 (`/api/accounts/me`) + 유효 JWT → 200 + `X-Account-ID` 주입됨
- [ ] 만료 JWT → 401 `TOKEN_INVALID`
- [ ] 변조 JWT → 401 `TOKEN_INVALID`
- [ ] Authorization 헤더 없음 (인증 필수 경로) → 401
- [ ] Rate limit 초과 → 429 + `Retry-After` 헤더
- [ ] 외부에서 `X-Account-ID` 직접 전송 → gateway가 덮어씀 (spoofing 방지)
- [ ] JWKS 페치 성공 → Redis 캐시 저장
- [ ] JWKS kid mismatch → 즉시 리페치
- [ ] 다운스트림 5xx → 503 투명 전달

---

# Related Specs

- `specs/services/gateway-service/architecture.md`
- `specs/services/gateway-service/overview.md`
- `specs/services/gateway-service/dependencies.md`
- `specs/services/gateway-service/redis-keys.md`
- `specs/features/authentication.md`
- `specs/features/rate-limiting.md`

# Related Skills

- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/backend/gateway-security/SKILL.md`
- `.claude/skills/backend/rate-limiting/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/gateway-api.md`
- `specs/contracts/http/internal/gateway-to-auth.md`

---

# Target Service

- `apps/gateway-service`

---

# Architecture

`specs/services/gateway-service/architecture.md` — Thin Layered (filter pipeline). Spring Cloud Gateway WebFlux 기반.

---

# Edge Cases

- auth-service JWKS 엔드포인트 장애 시 캐시된 키로 5분 grace → 이후 새 키 검증 실패
- Redis 장애 시 rate limit 정책 선택 (환경 변수: fail-open 기본)
- 동시에 여러 요청이 kid miss를 트리거 → JWKS 리페치는 한 번만 수행 (single-flight 패턴)

---

# Failure Scenarios

- Redis 전체 장애 → JWKS in-memory grace + rate limit fail-open → 경고 메트릭
- auth-service 장애 → JWKS 캐시 만료까지 검증 가능. 신규 키 발급분은 실패
- 다운스트림 타임아웃 → 504 반환

---

# Test Requirements

- Unit: `TokenValidator` (만료/변조/유효 토큰), `TokenBucketRateLimiter` 수식
- Filter slice: `WebTestClient` — JWT 필터, rate limit 필터, request ID 필터
- Integration: Testcontainers (Redis) + WireMock (auth-service JWKS, downstream services) — E2E 라우팅 + 인증 + rate limit

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Gateway API contract match (gateway-api.md)
- [ ] Spoofing prevention verified (`X-Account-ID` 덮어쓰기)
- [ ] Ready for review
