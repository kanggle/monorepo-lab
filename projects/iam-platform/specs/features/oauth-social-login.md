# Feature: OAuth Social Login (소셜 로그인)

## Purpose

외부 OAuth 2.0 / OpenID Connect 제공자(Google, Kakao, Microsoft, Naver)를 통한 인증 흐름을 정의한다. 사용자는 이메일·패스워드 대신 소셜 계정으로 로그인할 수 있으며, 기존 인증 시스템(JWT 발급, 디바이스 세션, 이벤트)과 동일한 후처리 파이프라인을 공유한다.

## Related Services

| Service | Role |
|---|---|
| auth-service | OAuth 인증 흐름 소유. Authorization URL 생성, authorization code ↔ token 교환, id_token 검증, SAS 세션 확립 |
| account-service | 소셜 로그인 시 계정 자동 생성 / 기존 계정 연결 (내부 HTTP) |
| gateway-service | `/oauth2/**` 라우팅 (레거시 `/api/auth/oauth/**` 는 TASK-BE-398 로 제거) |
| security-service | 로그인 이벤트 소비, 비정상 탐지 (기존과 동일) |

## SAS Browser Session Flow (TASK-BE-396)

> **이것이 consumer(web-store 등)가 실제 사용하는 표준 경로다.** ADR-006(옵션 B)에 따라
> 외부 IdP(소셜) 로그인을 IAM Spring Authorization Server(SAS) 브라우저 플로우에
> **upstream identity brokering**으로 통합한다. 소셜 인증은 **SAS 가 소비하는 인증된 HTTP
> 세션**(JSESSIONID `SecurityContext`)으로 종결되고, 그 결과 **SAS 표준 토큰**(issuer
> `http://iam.local`, JWKS 검증)이 발급된다. 커스텀 JWT 를 발급하지 않는다.

### 엔드포인트

| 경로 | 역할 |
|---|---|
| `GET /login` | 커스텀 Thymeleaf 로그인 페이지(`LoginPageController` + `templates/login.html`). email/password 폼 + 소셜 버튼(Google/Kakao/Microsoft/Naver, `OAuthProvider.values()` 자동 렌더). CSRF 토큰 포함. `DefaultLoginPageGeneratingFilter` 대체(`.loginPage("/login")`). |
| `GET /login/oauth/{provider}` | 소셜 인증 개시. 요청 base 로부터 브라우저 콜백 URI(`scheme://host[:port]/login/oauth/{provider}/callback`)를 계산해 `OAuthLoginUseCase.authorize` 호출 → provider authorization URL 로 redirect. |
| `GET /login/oauth/{provider}/callback` | provider 콜백. `OAuthLoginUseCase.resolveBrowserLogin` 으로 계정 해소 → SAS 세션 확립 → saved `/oauth2/authorize` 로 redirect. |

### 플로우

1. consumer 가 "Global Account 로 로그인" → IAM `GET /oauth2/authorize?client_id=ecommerce-web-store-client&...`
2. 미인증 → SAS chain 의 `LoginUrlAuthenticationEntryPoint` 가 `/login` 으로 redirect (원래 요청은 `HttpSessionRequestCache` 에 saved)
3. `/login` 렌더 → 사용자가 **Google 버튼** 클릭 → `GET /login/oauth/google`
4. `OAuthLoginUseCase.authorize(GOOGLE, browserCallbackUri)` → state(Redis) 저장 → Google authorization URL 로 redirect
5. Google 인증 → `GET /login/oauth/google/callback?code=...&state=...`
6. `OAuthLoginUseCase.resolveBrowserLogin(command, tenantId)`:
   a. state 검증 → token+userinfo 교환 → email 검증
   b. `social_identities` 조회 / auto-link / auto-create(`/internal/accounts/social-signup`, ADR-036 born-unified mint)
   c. **`SocialIdentityPersistStep`**(신규 transactional bean): `social_identity` upsert + 계정 상태 검사(LOCKED/DORMANT/DELETED 거부)만 수행. **JWT/디바이스 세션/refresh token/로그인 이벤트는 발급하지 않음.**
7. **tenant 귀속** — saved `/oauth2/authorize` 의 `client_id` → `RegisteredClientRepository.findByClientId` → `ClientSettings` 의 `custom.tenant_id`/`custom.tenant_type` (`SavedRequestTenantResolver`). saved request 부재 시 `fan-platform` 기본값.
8. **SAS 세션 확립** — `UsernamePasswordAuthenticationToken(email, null, [ROLE_USER])` + `details = HashMap{tenant_id, tenant_type, account_id}`(반드시 `HashMap` — `JdbcOAuth2AuthorizationService` 의 `SecurityJackson2Modules` allowlist), `HttpSessionSecurityContextRepository` 로 세션 영속.
9. saved `/oauth2/authorize` 로 redirect → SAS `authorization_code` → **SAS 표준 토큰** 발급.
10. **role 시딩(신규 코드 0)** — `TenantClaimTokenCustomizer` → `RoleSeedPolicy.seed(platform)`, `platform = 개시 client 의 tenant_id`. `ecommerce-web-store-client` → `roles:[CUSTOMER]`. operator 는 assume-tenant 단계에서 별도 파생.

### 에러 → redirect 매핑

| 예외 | redirect |
|---|---|
| `OAuthEmailRequiredException` | `/login?error=email_required` |
| `AccountLockedException` / `AccountStatusException` | `/login?error=account_unavailable` |
| `InvalidOAuthStateException` | `/login?error=invalid_state` |
| `OAuthProviderException` | `/login?error=provider_error` |
| `UnsupportedProviderException` | `/login?error=unsupported_provider` |

### tenant 귀속 규칙 (ADR-006 옵션 1)

소셜 principal 의 `tenant_id` = 로그인을 **개시한 consumer 의 tenant**. 메커니즘: 콜백 시점에
세션의 `RequestCache`(saved `/oauth2/authorize?client_id=...`)에서 `client_id` 를 읽어
client 의 `ClientSettings` tenant 설정을 추출(`SavedRequestTenantResolver`). state 스레딩
불필요(saved request 에 이미 `client_id` 존재). saved request 부재(직접 `/login` 진입) →
`TenantContext.DEFAULT_TENANT_ID`(`fan-platform`) fallback.

---

## Design Decisions

> **레거시 커스텀-JWT JSON 플로우는 2026-08-01 제거되었다 (TASK-BE-398).**
>
> `GET /api/auth/oauth/authorize` + `POST /api/auth/oauth/callback`(응답이
> `{ accessToken, refreshToken, ... }` 커스텀 JWT) 은 ADR-006 이 예고한 대로
> `POST /api/auth/login` 과 함께 일몰되었다. 제거 사유는 그대로다 — SAS issuer 를 신뢰하는
> 표준 OIDC consumer(ecommerce gateway 등, ADR-MONO-027)는 커스텀 JWT 를 거부하므로,
> 소셜 플로우만 커스텀-JWT 로 남기는 것은 방향에 역행한다.
>
> **유일한 소셜 로그인 경로는 위 [SAS Browser Session Flow](#sas-browser-session-flow-task-be-396) 다.**
>
> 아래 설계 결정(BFF 서버사이드 교환 / provider token 비저장 / 계정 연결 전략 / state CSRF)은
> **제거된 것이 아니라 브라우저 플로우가 그대로 물려받은 것**이다 — 두 플로우가 공유하던
> 계정해소 자산(`social_identities` upsert · auto-link · auto-create · born-unified mint ·
> Redis state)은 전부 보존된다. 제거된 것은 그 뒤에 붙어 있던 **커스텀 JWT 발급 꼬리**뿐이다
> (`OAuthLoginUseCase.callback()` + `OAuthLoginTransactionalStep`).

### BFF 패턴 (Server-Side Token Exchange)

authorization code 교환은 **auth-service가 서버 사이드에서 수행**한다. 브라우저는 provider
콜백을 auth-service 의 `/login/oauth/{provider}/callback` 으로 받고, auth-service가 provider의
token endpoint에 직접 요청하여 id_token을 획득한다. client_secret이 프론트엔드에 노출되지 않는다.

### Provider Token 비저장 원칙

provider로부터 받는 access_token, refresh_token은 **저장하지 않는다**. id_token에서 사용자 식별 정보(sub, email, name)만 추출하여 계정 매칭에 사용하고, provider token은 즉시 폐기한다. 이 플랫폼은 provider API를 대리 호출할 필요가 없으므로 불필요한 PII 보유를 피한다.

### 계정 연결 전략

1. `social_identities` 테이블에서 `(provider, provider_user_id)` 조합으로 기존 연결 조회
2. 연결이 있으면 해당 `account_id`로 로그인 처리
3. 연결이 없으면:
   a. provider email과 동일한 이메일의 기존 계정이 있으면 → 자동 연결 (auto-link)
   b. 기존 계정이 없으면 → 계정 자동 생성 (auto-create)
4. 계정 자동 생성·연결은 account-service의 `/internal/accounts/social-signup` 내부 API를 통해 수행

### CSRF 방어 (state 파라미터)

- auth-service가 `GET /login/oauth/{provider}` 시 cryptographic random `state` 생성 → Redis에 TTL 10분 저장 (`OAuthLoginUseCase.authorize`)
- callback 시 `state` 검증 후 Redis에서 삭제 (one-time use). state 는 발급 시점의 provider 에 **바인딩**되며 다른 provider 의 콜백에서는 소비되지 않는다 (TASK-BE-521)
- state 불일치 또는 만료 시 `InvalidOAuthStateException` → `/login?error=invalid_state`

## User Flows

### 소셜 로그인 (신규 사용자)

1. consumer 가 IAM `GET /oauth2/authorize?client_id=...` 로 진입 → 미인증 → `/login` redirect (원 요청 saved)
2. 사용자가 `/login` 에서 provider 버튼 클릭 → `GET /login/oauth/{provider}`
3. auth-service가 state 생성 → Redis 저장 → provider의 authorization URL로 redirect
4. 사용자가 provider에서 동의 → provider가 authorization code + state를 `GET /login/oauth/{provider}/callback` 으로 전달
5. auth-service가:
   a. state 검증 (Redis GETDEL + provider 바인딩 확인)
   b. provider token endpoint에 authorization code 교환 (server-side)
   c. id_token 파싱 → `{ providerUserId, email, displayName }` 추출
   d. `social_identities` 테이블에서 `(provider, provider_user_id)` 조회
   e. 미존재 → account-service `/internal/accounts/social-signup` 호출 (계정 자동 생성)
   f. `SocialIdentityPersistStep` — `social_identities` row upsert + 계정 상태 검사
   g. **SAS 세션 확립** (JSESSIONID `SecurityContext`; session id 회전)
6. saved `/oauth2/authorize` 재개 → SAS `authorization_code` → **표준 OIDC 토큰** 발급
   (`POST /oauth2/token`). 커스텀 JWT · device session · refresh row · `auth.login.*` 이벤트는
   이 경로에서 발생하지 않는다 — 그것들은 제거된 레거시 JSON 플로우의 꼬리였다.

### 소셜 로그인 (기존 사용자, 이미 연결)

1~5.d까지 동일. `social_identities` 매칭 → 해당 `account_id`로 계정 상태 확인 → `last_used_at` 갱신 → 5.g 이후 동일.

### 소셜 로그인 (기존 이메일 계정, 자동 연결)

1~5.d까지 동일. `social_identities` 미존재 → account-service social-signup → 이메일 일치 기존 계정 발견 시 기존 `accountId` 반환 → `social_identities` row 를 그 계정에 연결 → 5.f 이후 동일.

### Microsoft 특이 사항

Microsoft Identity Platform (Azure AD v2.0)은 OpenID Connect 표준을 따르며 Google과 동일한 id_token (JWT) 기반 흐름을 사용한다. 다음 차이가 있다:

- **Tenant**: authorization/token endpoint URL에 tenant 세그먼트 포함. 기본값 `common` (개인 Microsoft 계정 + 조직/학교 계정 모두 허용). 정책상 조직 계정만 허용하려면 `organizations`, 개인만 허용하려면 `consumers`, 단일 조직만 허용하려면 해당 tenant ID 사용.
- **사용자 식별자**: id_token의 `sub` claim 사용 (app-user pairwise identifier, 안정적). `oid`는 tenant-wide object ID로 참고용으로만 사용하며 DB의 `provider_user_id`에는 `sub`를 저장.
- **이메일**: `email` claim은 선택적. 없으면 `preferred_username`을 fallback으로 사용하며, 둘 다 없으면 `EMAIL_REQUIRED` 반환.
- **Scope**: `openid email profile` (기본).

## Business Rules

- 지원 provider: **Google**, **Kakao**, **Microsoft**, **Naver** (TASK-BE-397; 추가 provider는 `OAuthClient` 인터페이스 구현으로 확장)
- Naver는 id_token 미발급(Kakao와 동일 비-OIDC) → user-info API(`response` 래퍼)의 `id`/`email`/`name` 사용. `resultcode != "00"` → `PROVIDER_ERROR`
- provider id_token의 `email` 필드가 없으면 로그인 거부 (이메일 필수)
- 계정 상태가 ACTIVE가 아니면 소셜 로그인도 거부 (LOCKED / DORMANT / DELETED → `/login?error=account_unavailable`)
- 소셜 로그인 성공 시 발급하는 토큰은 폼 로그인과 동일한 **SAS 표준 OIDC 토큰**이다 (TASK-BE-398 이전에는 커스텀 JWT 였다)
- 하나의 계정에 여러 provider 연결 가능 (Google + Kakao 동시 사용)
- 하나의 provider_user_id는 하나의 계정에만 연결 (unique constraint)
- state TTL: **10분** (Redis `oauth:state:{state}`)

## Edge Cases

- provider에서 이메일 미제공 (Kakao 이메일 미동의) → 422 `EMAIL_REQUIRED`
- 동일 provider_user_id로 다른 계정에 이미 연결 → 로그인 시 기존 연결 계정으로 로그인 (새 연결 시도 없음)
- provider에서 이메일 미제공 시 브라우저 플로우는 `/login?error=email_required` 로 표면화한다
- provider token endpoint 장애 → `OAuthProviderException` → `/login?error=provider_error`
- provider 가 authorization code 자체를 거절(4xx `invalid_grant`) → `OAuthCodeInvalidException`
  (어댑터가 장애와 구별해 분류 — TASK-MONO-350). 브라우저 플로우에서는 상위 타입과 같은
  `/login?error=provider_error` 로 표면화된다
- state 만료 (10분 초과) → `/login?error=invalid_state`
- 동시 callback 요청 (같은 state) → 첫 요청만 처리, 두 번째는 `invalid_state` (Redis GETDEL 로 방어)
- social-signup 시 account-service 장애 → fail-closed (토큰 미발급)

## Security Considerations

- client_secret은 auth-service의 환경 변수로만 관리, 로그 출력 금지
- provider로부터 받은 token은 메모리에서만 사용, DB/로그에 저장 금지
- id_token 검증: Google은 JWKS 기반 서명 검증, Kakao는 token info API 호출, Microsoft는 JWKS 기반 서명 검증
- state 파라미터는 `SecureRandom` 기반 256-bit

## Related Contracts

- HTTP: [auth-api.md](../contracts/http/auth-api.md) — `GET /oauth2/authorize`, `POST /oauth2/token`
  (레거시 `GET /api/auth/oauth/authorize` · `POST /api/auth/oauth/callback` 은 TASK-BE-398 제거 기록으로 남아 있다)
- Internal: [auth-to-account-social.md](../contracts/http/internal/auth-to-account-social.md)
- Events: [auth-events.md](../contracts/events/auth-events.md) `auth.login.succeeded` (loginMethod 필드).
  **주의**: SAS 브라우저 소셜 플로우는 이 이벤트를 발행하지 않는다 — 발행하던 것은 제거된
  레거시 커스텀-JWT 꼬리(`OAuthLoginTransactionalStep`)였다.
