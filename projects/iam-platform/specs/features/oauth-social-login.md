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

## Design Decisions

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
