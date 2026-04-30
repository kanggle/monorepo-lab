# Task ID

TASK-BE-053

# Title

OAuth 소셜 로그인 (Google + Kakao) — auth-service, account-service, gateway-service

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event

# depends_on

- TASK-BE-005
- TASK-BE-004

---

# Goal

Google 및 Kakao OAuth 2.0 Authorization Code 흐름을 구현하여, 사용자가 소셜 계정으로 로그인할 수 있도록 한다. 소셜 로그인 성공 시 기존 이메일·패스워드 로그인과 동일한 JWT 토큰 발급, 디바이스 세션 생성, 이벤트 발행 파이프라인을 공유한다. 신규 사용자는 계정이 자동 생성되고, 기존 이메일 계정이 있으면 자동 연결된다.

---

# Scope

## In Scope

### auth-service

1. **Flyway 마이그레이션**: `V0005__create_social_identities.sql`
   - `social_identities` 테이블 생성 (id, account_id, provider, provider_user_id, provider_email, connected_at, last_used_at)
   - Unique index: `uk_social_provider_user (provider, provider_user_id)`
   - Index: `idx_social_account_id (account_id)`

2. **Presentation Layer**:
   - `OAuthController`: `GET /api/auth/oauth/authorize`, `POST /api/auth/oauth/callback`
   - `OAuthAuthorizeRequest` / `OAuthAuthorizeResponse` (authorize 요청/응답 DTO)
   - `OAuthCallbackRequest` / `OAuthCallbackResponse` (callback 요청/응답 DTO)

3. **Application Layer**:
   - `OAuthLoginUseCase` (application service):
     - `generateAuthorizationUrl(provider, redirectUri)` → authorizationUrl + state
     - `processCallback(provider, code, state, redirectUri)` → 토큰 발급 결과
   - `OAuthLoginCommand` / `OAuthLoginResult` (Command/Result DTO)

4. **Domain Layer**:
   - `OAuthProvider` enum: `GOOGLE`, `KAKAO`
   - `SocialIdentity` 도메인 엔티티
   - `OAuthUserInfo` value object (providerUserId, email, displayName)
   - `OAuthProviderClient` interface: `getAuthorizationUrl()`, `exchangeCode()`, `getUserInfo()`

5. **Infrastructure Layer**:
   - `GoogleOAuthClient` implements `OAuthProviderClient` — Google OAuth 2.0 연동
   - `KakaoOAuthClient` implements `OAuthProviderClient` — Kakao OAuth 2.0 연동
   - `OAuthConfig` (application.yml 기반 provider 설정: clientId, clientSecret, tokenUri, userInfoUri)
   - `SocialIdentityJpaEntity` + `SocialIdentityJpaRepository`
   - `OAuthStateRedisRepository` — state 생성/검증/삭제 (TTL 10분)

6. **이벤트 발행**: `auth.login.succeeded` payload에 `loginMethod` 필드 추가 (`EMAIL_PASSWORD | OAUTH_GOOGLE | OAUTH_KAKAO`)

### account-service

7. **Presentation Layer**: `InternalSocialSignupController`
   - `POST /internal/accounts/social-signup` 엔드포인트
   - `SocialSignupRequest` / `SocialSignupResponse` DTO

8. **Application Layer**: `SocialSignupUseCase`
   - 이메일 조회 → 존재하면 기존 accountId 반환 (200), 미존재하면 새 계정 생성 (201)

### gateway-service

9. `/api/auth/oauth/**` 라우트 추가 (public, 인증 불필요)

## Out of Scope

- Apple 로그인
- 소셜 계정 연결 해제 (unlink) API
- provider token 저장
- 2FA와의 통합
- 이메일 검증 강제화

---

# Acceptance Criteria

- [ ] `GET /api/auth/oauth/authorize?provider=google&redirectUri=...` → 200 + `{ authorizationUrl, state }`
- [ ] `GET /api/auth/oauth/authorize?provider=kakao&redirectUri=...` → 200 + `{ authorizationUrl, state }`
- [ ] `GET /api/auth/oauth/authorize?provider=apple&redirectUri=...` → 400 `UNSUPPORTED_PROVIDER`
- [ ] `POST /api/auth/oauth/callback` (유효한 code + state) → 200 + `{ accessToken, refreshToken, expiresIn, tokenType, isNewAccount }`
- [ ] 신규 사용자 callback → `isNewAccount: true` + account-service에 계정 자동 생성
- [ ] 기존 이메일 사용자 callback → `isNewAccount: false` + `social_identities` 자동 연결
- [ ] 이미 연결된 소셜 계정 callback → `isNewAccount: false` + `last_used_at` 갱신
- [ ] LOCKED 계정 소셜 로그인 → 403 `ACCOUNT_LOCKED`
- [ ] state 불일치 → 401 `INVALID_STATE`
- [ ] 만료된 state → 401 `INVALID_STATE`
- [ ] provider 장애 시 → 502 `PROVIDER_ERROR`
- [ ] 소셜 로그인 성공 시 outbox에 `auth.login.succeeded` 이벤트 + `loginMethod` 필드 포함
- [ ] 소셜 로그인 성공 시 device session 생성 (기존 로그인과 동일)
- [ ] `POST /internal/accounts/social-signup` → 신규 201, 기존 200
- [ ] gateway에서 `/api/auth/oauth/**` 라우팅 정상 동작
- [ ] `./gradlew :apps:auth-service:test` 통과
- [ ] `./gradlew :apps:account-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/data-model.md`
- `specs/services/auth-service/device-session.md`
- `specs/services/auth-service/redis-keys.md`
- `specs/services/account-service/architecture.md`
- `specs/features/oauth-social-login.md`
- `specs/features/authentication.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (GET /api/auth/oauth/authorize, POST /api/auth/oauth/callback)
- `specs/contracts/http/internal/auth-to-account-social.md` (POST /internal/accounts/social-signup)
- `specs/contracts/events/auth-events.md` (auth.login.succeeded loginMethod 추가)

---

# Target Service

- `apps/auth-service` (primary)
- `apps/account-service` (social-signup endpoint)
- `apps/gateway-service` (route 추가)

---

# Architecture

- `specs/services/auth-service/architecture.md` — Layered 4-layer
- `specs/services/account-service/architecture.md` — Layered 4-layer

---

# Edge Cases

- provider에서 이메일 미제공 (Kakao 이메일 미동의) → 422 `EMAIL_REQUIRED`
- 동일 provider_user_id로 이미 다른 계정에 연결 → 해당 기존 계정으로 로그인
- 동시 callback 요청 (같은 state 두 번 전송) → 첫 요청 성공, 두 번째 `INVALID_STATE` (Redis 삭제로 원자적 방어)
- provider token endpoint 응답이 id_token을 포함하지 않는 경우 → `PROVIDER_ERROR`
- social-signup 호출 시 account-service 장애 → 503 fail-closed
- 사용자가 provider에서 이메일 변경 후 재로그인 → provider_user_id로 매칭하므로 기존 계정 유지 (provider_email 갱신)

---

# Failure Scenarios

- **Google OAuth API 장애**: token endpoint 또는 userinfo API 실패 → circuit breaker open → 502 `PROVIDER_ERROR`
- **Kakao OAuth API 장애**: 동일 처리
- **Redis 장애**: state 저장/검증 불가 → 소셜 로그인 불가 (fail-closed)
- **account-service 장애**: social-signup 호출 실패 → circuit breaker → 503
- **MySQL 장애**: social_identities 조회/저장 불가 → 503
- **id_token 서명 검증 실패**: 위조 가능성 → 401 `INVALID_CODE`

---

# Test Requirements

## Unit Tests
- `OAuthLoginUseCase`: authorize URL 생성, callback 처리 (신규/기존/자동연결), 계정 상태 거부
- `OAuthProvider` enum 매핑
- `SocialIdentity` 도메인 로직

## Slice Tests (`@WebMvcTest`)
- `OAuthController`: authorize 요청 파라미터 검증, callback 요청 body 검증, 에러 응답 형식
- `InternalSocialSignupController`: 요청/응답 직렬화, 에러 처리

## Integration Tests (`@SpringBootTest` + Testcontainers)
- **WireMock 기반**: Google/Kakao token endpoint, userinfo endpoint 모킹
- 소셜 로그인 전체 흐름: authorize → callback → JWT 발급 + DB 확인
- 신규 사용자 계정 자동 생성 확인 (account-service WireMock)
- 기존 이메일 자동 연결 확인
- state 만료/불일치 시나리오
- provider 장애 시 circuit breaker 동작 확인

---

# Definition of Done

- [ ] Implementation completed (auth-service, account-service, gateway-service)
- [ ] V0005 마이그레이션 적용 확인
- [ ] Tests added and passing
- [ ] Contracts match (auth-api.md, auth-to-account-social.md, auth-events.md)
- [ ] JWT spec (기존 login과 동일 형식) verified
- [ ] Ready for review
