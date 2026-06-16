# Feature: OAuth Social Login (소셜 로그인)

## Purpose

외부 OAuth 2.0 / OpenID Connect 제공자(Google, Kakao, Microsoft)를 통한 인증 흐름을 정의한다. 사용자는 이메일·패스워드 대신 소셜 계정으로 로그인할 수 있으며, 기존 인증 시스템(JWT 발급, 디바이스 세션, 이벤트)과 동일한 후처리 파이프라인을 공유한다.

## Related Services

| Service | Role |
|---|---|
| auth-service | OAuth 인증 흐름 소유. Authorization URL 생성, authorization code ↔ token 교환, id_token 검증, JWT 발급 |
| account-service | 소셜 로그인 시 계정 자동 생성 / 기존 계정 연결 (내부 HTTP) |
| gateway-service | `/api/auth/oauth/**` 라우팅 |
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
| `GET /login` | 커스텀 Thymeleaf 로그인 페이지(`LoginPageController` + `templates/login.html`). email/password 폼 + 소셜 버튼(Google/Kakao/Microsoft). CSRF 토큰 포함. `DefaultLoginPageGeneratingFilter` 대체(`.loginPage("/login")`). |
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

> ⚠️ **DEPRECATED (legacy / standalone)** — 아래 `### BFF 패턴` 이하가 기술하는
> 커스텀-JWT JSON 종결 플로우(`POST /api/auth/oauth/callback` 가 `{ accessToken,
> refreshToken, ... }` 반환)는 **레거시**다. SAS issuer 를 신뢰하는 표준 OIDC consumer
> (ecommerce gateway 등, ADR-MONO-027)는 이 커스텀 JWT 를 거부한다. 신규 통합은 위
> **SAS Browser Session Flow** 를 사용해야 한다. 레거시 경로는 standalone 소비자를 위해
> deprecation window 동안 보존되며, `POST /api/auth/login`(`LoginController`)과 함께
> **2026-08-01 일몰** 예정이다(ADR-006). `social_identities` upsert / auto-link /
> auto-create / state CSRF / born-unified mint 등 계정해소 자산은 두 플로우가 공유한다.

### BFF 패턴 (Server-Side Token Exchange)

authorization code 교환은 **auth-service가 서버 사이드에서 수행**한다. 클라이언트(브라우저/앱)는 authorization code를 auth-service에 전달하고, auth-service가 provider의 token endpoint에 직접 요청하여 id_token을 획득한다. client_secret이 프론트엔드에 노출되지 않는다.

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

- auth-service가 `GET /api/auth/oauth/authorize` 시 cryptographic random `state` 생성 → Redis에 TTL 10분 저장
- callback 시 `state` 검증 후 Redis에서 삭제 (one-time use)
- state 불일치 또는 만료 시 `INVALID_STATE` 에러

## User Flows

### 소셜 로그인 (신규 사용자)

1. 클라이언트가 `GET /api/auth/oauth/authorize?provider=google&redirectUri=...` 호출
2. auth-service가 state 생성 → Redis 저장 → provider의 authorization URL + state 반환
3. 클라이언트가 반환된 URL로 리다이렉트 → 사용자가 provider에서 동의
4. provider가 authorization code + state를 redirectUri로 전달
5. 클라이언트가 `POST /api/auth/oauth/callback` 에 `{ provider, code, state, redirectUri }` 전송
6. auth-service가:
   a. state 검증 (Redis 조회 + 삭제)
   b. provider token endpoint에 authorization code 교환 (server-side)
   c. id_token 파싱 → `{ providerUserId, email, displayName }` 추출
   d. `social_identities` 테이블에서 `(provider, provider_user_id)` 조회
   e. 미존재 → account-service `/internal/accounts/social-signup` 호출 (계정 자동 생성)
   f. `social_identities` row 생성
   g. device session 생성 (기존 로그인과 동일)
   h. JWT access/refresh token pair 발급
   i. outbox: `auth.login.succeeded` 이벤트 (loginMethod: `OAUTH_GOOGLE`)
7. 응답: `{ accessToken, refreshToken, expiresIn, tokenType, isNewAccount: true }`

### 소셜 로그인 (기존 사용자, 이미 연결)

1~6.d까지 동일
6. e. `social_identities`에 매칭 → 해당 `account_id`로 계정 상태 확인
6. f. `last_used_at` 갱신
6. g~i. 동일 (device session + JWT + 이벤트)
7. 응답: `{ ..., isNewAccount: false }`

### 소셜 로그인 (기존 이메일 계정, 자동 연결)

1~6.d까지 동일
6. e. `social_identities` 미존재 → account-service에 social-signup 요청
6. f. account-service가 이메일 일치 기존 계정 발견 → 200 + 기존 `accountId` 반환
6. g. `social_identities` row 생성 (기존 accountId에 연결)
6. h~j. 동일
7. 응답: `{ ..., isNewAccount: false }`

### Microsoft 특이 사항

Microsoft Identity Platform (Azure AD v2.0)은 OpenID Connect 표준을 따르며 Google과 동일한 id_token (JWT) 기반 흐름을 사용한다. 다음 차이가 있다:

- **Tenant**: authorization/token endpoint URL에 tenant 세그먼트 포함. 기본값 `common` (개인 Microsoft 계정 + 조직/학교 계정 모두 허용). 정책상 조직 계정만 허용하려면 `organizations`, 개인만 허용하려면 `consumers`, 단일 조직만 허용하려면 해당 tenant ID 사용.
- **사용자 식별자**: id_token의 `sub` claim 사용 (app-user pairwise identifier, 안정적). `oid`는 tenant-wide object ID로 참고용으로만 사용하며 DB의 `provider_user_id`에는 `sub`를 저장.
- **이메일**: `email` claim은 선택적. 없으면 `preferred_username`을 fallback으로 사용하며, 둘 다 없으면 `EMAIL_REQUIRED` 반환.
- **Scope**: `openid email profile` (기본).

## Business Rules

- 지원 provider: **Google**, **Kakao**, **Microsoft** (추가 provider는 `OAuthClient` 인터페이스 구현으로 확장)
- provider id_token의 `email` 필드가 없으면 로그인 거부 (이메일 필수)
- 계정 상태가 ACTIVE가 아니면 소셜 로그인도 거부 (LOCKED → 403, DORMANT → 403, DELETED → 403)
- 소셜 로그인 성공 시 발급하는 JWT는 이메일·패스워드 로그인과 **동일 형식·TTL**
- 하나의 계정에 여러 provider 연결 가능 (Google + Kakao 동시 사용)
- 하나의 provider_user_id는 하나의 계정에만 연결 (unique constraint)
- state TTL: **10분** (Redis `oauth:state:{state}`)

## Edge Cases

- provider에서 이메일 미제공 (Kakao 이메일 미동의) → 422 `EMAIL_REQUIRED`
- 동일 provider_user_id로 다른 계정에 이미 연결 → 로그인 시 기존 연결 계정으로 로그인 (새 연결 시도 없음)
- provider token endpoint 장애 → 502 `PROVIDER_ERROR`
- state 만료 (10분 초과) → 401 `INVALID_STATE`
- 동시 callback 요청 (같은 state) → 첫 요청만 처리, 두 번째는 `INVALID_STATE` (Redis 삭제로 방어)
- social-signup 시 account-service 장애 → 503 (fail-closed)

## Security Considerations

- client_secret은 auth-service의 환경 변수로만 관리, 로그 출력 금지
- provider로부터 받은 token은 메모리에서만 사용, DB/로그에 저장 금지
- id_token 검증: Google은 JWKS 기반 서명 검증, Kakao는 token info API 호출, Microsoft는 JWKS 기반 서명 검증
- state 파라미터는 `SecureRandom` 기반 256-bit

## Related Contracts

- HTTP: [auth-api.md](../contracts/http/auth-api.md) `GET /api/auth/oauth/authorize`, `POST /api/auth/oauth/callback`
- HTTP Internal: [auth-to-account-social.md](../contracts/http/internal/auth-to-account-social.md)
- Events: [auth-events.md](../contracts/events/auth-events.md) `auth.login.succeeded` (loginMethod 필드 추가)
