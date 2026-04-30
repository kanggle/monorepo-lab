# Task ID

TASK-BE-056

# Title

OAuth 소셜 로그인에 Microsoft provider 추가 — auth-service

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-053

---

# Goal

Microsoft Identity Platform (Azure AD v2.0)을 세 번째 OAuth provider로 추가한다. Google·Kakao와 동일한 `/api/auth/oauth/authorize`·`/callback` 흐름을 공유하며, `provider=microsoft` 파라미터로 구분된다. Microsoft는 OpenID Connect 표준을 따르므로 Google 구현과 같은 id_token (JWT) 기반 패턴을 재사용한다.

---

# Scope

## In Scope

### auth-service

1. **Domain Layer**:
   - `OAuthProvider` enum에 `MICROSOFT` 추가

2. **Infrastructure Layer**:
   - `MicrosoftOAuthClient implements OAuthClient` — Google 패턴 준용 (token endpoint POST → id_token JWT payload 디코딩)
   - `OAuthProperties`에 `microsoft: ProviderProperties` 필드 추가
   - `OAuthClientFactory` switch에 `case MICROSOFT` 분기 추가

3. **설정**:
   - `application.yml`의 `oauth:` 섹션에 `microsoft:` 블록 추가
     - `tenant`: 기본 `common` (환경변수로 override)
     - `token-uri`: `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token`
     - `auth-uri`: `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize`
     - `scopes`: `openid,email,profile`
   - 테스트 profile(`application-test.yml`)에도 `microsoft` 블록 추가 (더미값)
   - e2e 환경에는 배선 불필요 (기존 compose는 touching 없음)

4. **이벤트**: 기존 `auth.login.succeeded`의 `loginMethod` 값에 `OAUTH_MICROSOFT`가 자동 포함됨 (enum 기반). 이벤트 계약 자체는 변경 없음.

### Out of Scope

- 3rd party provider (Apple, LINE 등)
- tenant 화이트리스트(단일 테넌트 강제) — 현재는 `common` 고정
- id_token의 JWKS 서명 검증 강화 (현재 Google과 동일하게 payload만 디코딩; 전용 서명 검증 강화는 별도 태스크로 분리)
- admin UI 또는 사용자 설정 UI의 provider 목록 변경 — 프론트는 별도 태스크

---

# Acceptance Criteria

- [ ] `GET /api/auth/oauth/authorize?provider=microsoft&redirectUri=...` → 200 + `{ authorizationUrl, state }`, `authorizationUrl`이 `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize?...`로 시작
- [ ] `POST /api/auth/oauth/callback` with `provider=microsoft` + 유효 code/state → 200 + 토큰 pair 발급 (Google/Kakao와 동일 응답 형식)
- [ ] Microsoft id_token의 `sub` claim이 `social_identities.provider_user_id`에 저장됨
- [ ] Microsoft id_token에 `email` claim 없을 때 `preferred_username`을 fallback으로 사용
- [ ] `email`, `preferred_username` 모두 없으면 422 `EMAIL_REQUIRED`
- [ ] 신규 사용자 → `isNewAccount: true` + 계정 자동 생성 (기존 Google 흐름과 동일)
- [ ] 기존 이메일 사용자 → `isNewAccount: false` + `social_identities` 자동 연결
- [ ] Microsoft token endpoint 장애 → 502 `PROVIDER_ERROR`
- [ ] 소셜 로그인 성공 outbox 이벤트 `loginMethod: OAUTH_MICROSOFT`
- [ ] `OAuthProvider.from("microsoft")` / `from("MICROSOFT")` 파싱 정상
- [ ] `./gradlew :apps:auth-service:test` 통과
- [ ] 스펙·계약 문서 일치 ([oauth-social-login.md](../../specs/features/oauth-social-login.md), [auth-api.md](../../specs/contracts/http/auth-api.md))

---

# Related Specs

- `specs/features/oauth-social-login.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/data-model.md`
- `specs/services/auth-service/redis-keys.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — `GET /api/auth/oauth/authorize`, `POST /api/auth/oauth/callback` (provider 목록에 microsoft 추가됨)
- `specs/contracts/events/auth-events.md` — `auth.login.succeeded.loginMethod` 값에 `OAUTH_MICROSOFT` 허용

---

# Target Service

- `apps/auth-service` (primary)

gateway-service/account-service는 변경 없음 (기존 `/api/auth/oauth/**` 라우트·social-signup은 provider 무관).

---

# Architecture

- `specs/services/auth-service/architecture.md` — Layered 4-layer

---

# Edge Cases

- Microsoft가 personal 계정에서 `email` 미제공 (계정 설정에 따라) → `preferred_username`이 이메일 형식이면 사용, 아니면 `EMAIL_REQUIRED`
- 동일 MS 계정을 다른 tenant에서 로그인 시도 → `sub`는 app+user pairwise이므로 동일 앱 등록이라면 동일 `sub` 반환, 문제 없음
- `preferred_username`이 UPN이고 도메인이 외부 사용자(`#EXT#`)인 경우 → 일단 저장하되 이메일 형식 검증(간단한 `@` 존재) 실패 시 `EMAIL_REQUIRED`
- tenant 변경(`common` → `organizations`) 시 재시작만으로 적용되도록 `tenant`를 authorization/token URI에 runtime placeholder 대신 `application.yml` resolve time 치환

---

# Failure Scenarios

- Microsoft token endpoint 타임아웃/5xx → 502 `PROVIDER_ERROR`
- id_token payload base64 디코딩 실패 또는 JSON 파싱 실패 → 502 `PROVIDER_ERROR` (`Malformed Microsoft id_token`)
- id_token에 `sub` 없음 (비정상 응답) → 502 `PROVIDER_ERROR`
- Redis 장애 → state 검증 불가 → 기존과 동일 fail-closed

---

# Test Requirements

## Unit Tests

- `OAuthProvider.from("microsoft")` 파싱 / `loginMethod()` → `"OAUTH_MICROSOFT"` 반환
- `OAuthClientFactory.getClient(MICROSOFT)` → `MicrosoftOAuthClient` 반환
- `MicrosoftOAuthClient`:
  - id_token payload에 `sub`, `email`, `name` 모두 있는 정상 케이스
  - `email` 없고 `preferred_username` 있는 케이스 → email fallback 동작
  - `email`, `preferred_username` 모두 없는 케이스 → `EMAIL_REQUIRED` 신호 (상위 useCase가 매핑)
  - `sub` 없음 → `OAuthProviderException`
  - token endpoint 5xx → `OAuthProviderException`

## Integration Tests

- WireMock으로 Microsoft token endpoint 모킹
- 전체 callback 흐름: `provider=microsoft` + 유효 code → JWT 발급 + `social_identities` row 생성 확인
- 이벤트 outbox에 `loginMethod=OAUTH_MICROSOFT` 포함 확인
- 기존 이메일 계정과 자동 연결 시나리오

## Contract Tests

- `POST /api/auth/oauth/callback` with `provider=microsoft` validation 통과

---

# Definition of Done

- [ ] `OAuthProvider.MICROSOFT` 추가 + factory 분기
- [ ] `MicrosoftOAuthClient` 구현
- [ ] `application.yml` / `application-test.yml` `oauth.microsoft` 블록 추가
- [ ] 단위·통합 테스트 추가 및 통과
- [ ] 스펙/계약 문서 정합성 확인 (oauth-social-login.md, auth-api.md)
- [ ] Ready for review
