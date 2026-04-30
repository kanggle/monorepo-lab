# Task ID

TASK-BE-005

# Title

auth-service 부트스트랩 — 로그인, JWT 발급, Redis 실패 카운터, 내부 JWKS 엔드포인트

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

# depends_on

- TASK-BE-002
- TASK-BE-003
- TASK-BE-004

---

# Goal

auth-service를 실행 가능한 Spring Boot 애플리케이션으로 초기화하고, 이메일·패스워드 로그인(`POST /api/auth/login`), 로그아웃(`POST /api/auth/logout`), 토큰 갱신(`POST /api/auth/refresh`), JWKS 배포(`GET /internal/auth/jwks`)의 최소 골든패스를 구현한다.

---

# Scope

## In Scope

- `apps/auth-service/` 모듈 생성
- 패키지 구조: `presentation / application / domain / infrastructure` ([architecture.md](../../specs/services/auth-service/architecture.md))
- Flyway: `credentials`, `refresh_tokens`, `outbox_events` 테이블
- 로그인 흐름: account-service 내부 HTTP로 credential lookup → argon2id 비교 → JWT 발급
- Redis 로그인 실패 카운터 (`login:fail:{email_hash}`, 5회/15분)
- Refresh token rotation (DB chain) — 재사용 탐지는 TASK-BE-009(backlog)로 미루되 **데이터 구조(rotated_from)는 이 태스크에서 준비**
- 로그아웃: Redis blacklist
- JWKS 내부 엔드포인트 (RS256 공개 키 배포)
- Outbox 이벤트: `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`
- `libs/java-security`의 `JwtSigner`, `PasswordHasher` 사용

## Out of Scope

- 토큰 재사용 탐지 (TASK-BE-009)
- OAuth 소셜 로그인 (백로그)
- 패스워드 변경/재설정 (백로그)
- 2FA

---

# Acceptance Criteria

- [ ] `./gradlew :apps:auth-service:bootRun` 성공, `/actuator/health` → 200
- [ ] `POST /api/auth/login` (올바른 이메일+패스워드) → 200 + `{ accessToken, refreshToken, expiresIn, tokenType }`
- [ ] 잘못된 패스워드 → 401 `CREDENTIALS_INVALID`
- [ ] LOCKED 계정 로그인 → 403 `ACCOUNT_LOCKED`
- [ ] 5회 연속 실패 → 429 `LOGIN_RATE_LIMITED`
- [ ] `POST /api/auth/logout` → 204 + refresh token 블랙리스트 등록
- [ ] `POST /api/auth/refresh` → 200 + 새 토큰 페어 (기존 토큰 rotation)
- [ ] `GET /internal/auth/jwks` → 200 + JWKS JSON
- [ ] JWT access token이 RS256으로 서명되고 `sub`, `iss`, `exp`, `jti`, `scope` claim 포함
- [ ] Outbox에 로그인 이벤트 기록 확인
- [ ] account-service 장애 시 로그인 503 (fail-closed)

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/dependencies.md`
- `specs/services/auth-service/data-model.md`
- `specs/services/auth-service/redis-keys.md`
- `specs/features/authentication.md`
- `specs/features/session-management.md`
- `specs/use-cases/signup-and-login.md` (UC-2)

# Related Skills

- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/redis-session/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/internal/auth-to-account.md`
- `specs/contracts/http/internal/gateway-to-auth.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered 4-layer.

---

# Edge Cases

- account-service에 계정이 존재하지만 credentials가 아직 없는 상태 (가입 중간 실패) → 401
- Redis 장애 시 실패 카운터 조회 불가 → 로그인 허용하되 경고 메트릭
- JWKS 엔드포인트 호출 빈도가 높을 때 → 내부 호출이므로 rate limit 불필요

---

# Failure Scenarios

- account-service 장애 → circuit breaker open → 503
- MySQL 장애 → credential 조회·토큰 저장 불가 → 503
- Redis 장애 → 실패 카운터 fail-open, blacklist fail-closed
- JWT 서명 키 초기화 실패 → 앱 기동 실패

---

# Test Requirements

- Unit: `LoginUseCase`, `RefreshTokenUseCase`, 도메인 로직
- Slice: `@WebMvcTest` — LoginController, RefreshController, JwksController
- Integration: `@SpringBootTest` + Testcontainers (MySQL + Redis) + WireMock (account-service) — 로그인 성공/실패/rate-limit E2E

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match (auth-api.md, internal contracts)
- [ ] JWT spec (RS256, claims, TTL) verified
- [ ] Ready for review
