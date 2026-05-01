# Task ID

TASK-BE-251

# Title

Spring Authorization Server 도입 + 표준 OIDC 엔드포인트 (`/oauth2/*`, `/.well-known/*`)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- adr

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`auth-service`를 표준 OIDC 1.0 / OAuth 2.0 Authorization Server 로 승격한다. Spring Authorization Server (SAS) 1.x 라이브러리 기반으로 표준 엔드포인트 풀세트를 제공하고, multi-tenant `tenant_id` claim 보존을 그대로 유지한다.

ADR-001 = ACCEPTED (D1=A) 의 결정에 따른 P0 핵심 태스크.

완료 시점에 다음이 모두 참:

1. `GET /.well-known/openid-configuration` 이 표준 OIDC discovery JSON을 반환 (issuer, jwks_uri, authorization_endpoint, token_endpoint, userinfo_endpoint, scopes_supported, response_types_supported 포함).
2. `GET /oauth2/jwks` 가 access token 검증용 공개 키 JWK Set 반환 (admin-service의 `/.well-known/admin/jwks.json`과 분리 운영).
3. `GET /oauth2/authorize` 가 `authorization_code` flow를 처리하며 PKCE (`code_challenge`, `code_challenge_method=S256`) 필수.
4. `POST /oauth2/token` 이 `authorization_code`, `client_credentials`, `refresh_token` grant를 처리.
5. `GET /oauth2/userinfo` 가 ID token claim을 반환.
6. `POST /oauth2/revoke` (RFC 7009), `POST /oauth2/introspect` (RFC 7662) 구현.
7. 발급되는 모든 access/ID token에 `tenant_id`, `tenant_type` claim이 포함됨.
8. 기존 `POST /api/auth/login` 흐름은 deprecate 표시만 추가하고 그대로 동작 (D2-b: 90일 후 별도 태스크에서 제거).

---

# Scope

## In Scope

- `apps/auth-service`에 `org.springframework.security:spring-security-oauth2-authorization-server:1.x` 의존성 추가.
- `OAuth2AuthorizationServerConfiguration` 빈 구성:
  - issuer = `https://gap.example.com` (env로 외부화)
  - JWT signer: 기존 RS256 key pair 재사용 (별도 `oauth2.signing-key` 분리 권장은 후속 ADR)
  - authorization endpoint, token endpoint, userinfo endpoint, JWKS endpoint, revocation, introspection 활성화
  - PKCE 필수 (S256만 허용)
- `OAuth2TokenCustomizer<JwtEncodingContext>` 구현으로 access/ID token에 `tenant_id`, `tenant_type` claim 주입.
- `OidcUserInfoMapper` 구현: account-service에서 사용자 프로필 조회 → `userinfo` payload 구성.
- gateway-service 라우팅 추가:
  - `/.well-known/openid-configuration`, `/oauth2/**` 를 auth-service로 forward
  - JWT 검증은 기존 자체 발급 토큰과 SAS 발급 토큰을 모두 동일 JWKS로 검증 가능하도록 보장
- Discovery 문서의 `subject_types_supported`, `id_token_signing_alg_values_supported`, `grant_types_supported` 정합성.
- `client_credentials` grant 흐름:
  - `tenant_id` claim은 client에 사전 등록된 tenant 사용 (token 발급 시 client metadata에서 조회)
- `refresh_token` rotation: 기존 refresh token 회전·재사용 탐지 로직(`AuthRefreshTokenStore`)과 SAS의 refresh token 처리 통합 — SAS 기본 구현으로 갈지 기존 로직 유지할지 결정 (`Implementation Notes` 참조)
- `specs/contracts/http/auth-api.md` 갱신: `/oauth2/*` 표준 엔드포인트 섹션 추가, 기존 `POST /api/auth/login` 에 `Deprecated since 2026-05-XX, removal target 2026-08-XX` 명시.
- `specs/services/auth-service/architecture.md` 갱신: SAS 도입에 따른 컴포넌트 다이어그램 변경.

## Out of Scope

- `oauth_clients`/`oauth_scopes`/`oauth_consent` 스키마 정의·마이그레이션 — TASK-BE-252.
- consent 화면 UI (B2B는 admin 사전 동의로 skip; B2C 연결 시 별도 화면 — fan-platform 연동 시점에 결정).
- fan-platform · wms 등 소비 서비스 마이그레이션 — TASK-BE-253, TASK-MONO-019.
- DPoP, mTLS client auth 등 고급 보안 — 후속 ADR.
- `POST /api/auth/login` 제거 — 90일 후 별도 cleanup 태스크.

---

# Acceptance Criteria

- [ ] `GET /.well-known/openid-configuration` 200 응답에 `issuer`, `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint`, `jwks_uri`, `response_types_supported=[code]`, `grant_types_supported=[authorization_code, client_credentials, refresh_token]`, `code_challenge_methods_supported=[S256]` 포함.
- [ ] `GET /oauth2/jwks` 200 응답에 `keys[].kty`, `keys[].use=sig`, `keys[].alg=RS256`, `keys[].kid`, `keys[].n`, `keys[].e` 포함.
- [ ] `authorization_code` + PKCE flow E2E: client → `/oauth2/authorize?response_type=code&...&code_challenge=...` → 사용자 로그인 → callback redirect with `code` → `POST /oauth2/token` (`code_verifier`) → access + refresh + id token 발급.
- [ ] `client_credentials` flow E2E: 등록된 client → `POST /oauth2/token` (Basic auth: client_id/client_secret) → access token 발급. token claim에 `tenant_id` = client에 매핑된 tenant.
- [ ] 발급된 모든 access/ID token에 `tenant_id`, `tenant_type` claim 포함 검증.
- [ ] `POST /oauth2/revoke` 후 `POST /oauth2/introspect` 가 `active=false` 반환.
- [ ] gateway-service가 `/oauth2/*` 라우팅을 처리 (JWT 미검증 통과 — auth-service가 처리).
- [ ] 기존 `POST /api/auth/login` 흐름이 deprecate 표시 외에 동작 변경 없음 (회귀 테스트 PASS).
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:check` PASS.
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:integrationTest` PASS (`@Tag("integration")`).

---

# Related Specs

> Step 0: read `PROJECT.md`, `rules/common.md`, `rules/domains/saas.md`, `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md`.

- `docs/adr/ADR-001-oidc-adoption.md` (본 태스크의 결정 근거)
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/data-model.md`
- `specs/services/auth-service/redis-keys.md`
- `specs/features/authentication.md`
- `specs/features/multi-tenancy.md` § "JWT Claims"

# Related Skills

- `.claude/skills/backend/` 중 OAuth/OIDC 관련 (없으면 본 태스크 종료 후 신규 추가 검토)

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — `/oauth2/*` 표준 엔드포인트 섹션 신규 추가
- `specs/contracts/http/gateway-api.md` — `/oauth2/**`, `/.well-known/openid-configuration` 라우팅 추가

---

# Target Service

- `auth-service` (primary)
- `gateway-service` (라우팅)

---

# Architecture

`specs/services/auth-service/architecture.md` 준수. 변경 포인트:

- `infrastructure/oauth2/`: SAS 설정 빈 (`OAuth2AuthorizationServerConfiguration`, `RegisteredClientRepository` (in-memory placeholder, 252에서 JPA로 대체), `OAuth2TokenCustomizer`).
- `infrastructure/security/`: JWT signer 빈 노출 (기존 자체 발급 토큰과 SAS 발급 토큰이 동일 JWK Set으로 검증되도록 통합).
- `application/oidc/`: `OidcUserInfoMapper` (account-service 호출 → UserInfo 응답 구성).

---

# Implementation Notes

- **SAS 버전 선정**: Spring Boot 3.x와 호환되는 SAS 1.4.x 또는 1.5.x. 프로젝트의 Spring Boot 버전에 맞춰 선택.
- **Refresh token 통합**: SAS 기본 refresh token 발급/회전을 사용하되, 재사용 탐지(`auth.token.reuse.detected`)는 SAS의 `OAuth2TokenRevocationAuthenticationProvider` 또는 custom `OAuth2RefreshTokenAuthenticationProvider`로 보존. 기존 `AuthRefreshTokenStore`의 의미를 SAS 모델에 맵핑.
- **`tenant_id` claim 주입**: `authorization_code` flow에서는 사용자의 account → tenant 매핑으로 결정. `client_credentials`에서는 client metadata의 `tenant_id` 사용.
- **JWKS 분리**: admin-service의 `/.well-known/admin/jwks.json`과 별도 운영. 동일 RSA key pair를 공유하되 endpoint는 분리.
- **Discovery 캐싱**: 클라이언트가 discovery 문서를 캐싱하므로 `Cache-Control: public, max-age=3600` 권장.
- **In-memory client placeholder**: TASK-BE-252가 JPA 기반 `RegisteredClientRepository`를 추가하기 전까지 in-memory 등록으로 선행 검증. 252 머지 후 JPA로 대체.

---

# Edge Cases

- **Discovery 문서의 endpoint URL**: gateway 경유 외부 URL과 auth-service 내부 URL이 다름 — `issuer` 와 endpoint URL은 외부 URL로 설정 필요 (env: `OIDC_ISSUER_URL`).
- **multiple audience**: `client_credentials` 토큰의 `aud` claim에 caller client_id를 포함할지 결정 — Spring Security Resource Server는 `aud` 검증 옵션이 있음.
- **Discovery `userinfo_endpoint` 호출 시 토큰 부족**: 표준 OIDC `scope=openid` 가 없으면 ID token 미발급. `/oauth2/userinfo` 호출 시 `WWW-Authenticate: error="invalid_token"` 응답.
- **Multi-tenant cross-tenant token reuse**: tenantA 사용자가 tenantB의 client로 발급받은 token을 사용하려 시도 — `tenant_id` claim 불일치로 reject (gateway에서 X-Tenant-Id와 token claim 비교).

---

# Failure Scenarios

- **SAS 1.x major-version drift**: 라이브러리 업그레이드 시 API 변경 가능 — SemVer pin (`1.4.x`), 업그레이드는 별도 태스크.
- **PKCE 미사용 client 강제 차단**: `requireProofKey(true)` 설정으로 PKCE 미전송 요청은 `invalid_request`. legacy non-PKCE client가 있다면 별도 등록 옵션 필요 — 현재 신규 도입이므로 PKCE 강제.
- **token endpoint rate limit 미적용**: `/oauth2/token` 에 brute-force 보호가 필요. 기존 rate limiting 인프라(`gateway-service`)에 OAuth endpoint pattern 추가.
- **Refresh token 회전 누락**: SAS 기본 회전 동작과 기존 `AuthRefreshTokenStore` 회전 로직이 중복되거나 누락될 위험 — 통합 테스트로 회전 동작 검증.

---

# Test Requirements

- 단위 테스트:
  - `OAuth2TokenCustomizer`: token에 `tenant_id`, `tenant_type` claim 주입 검증.
  - `OidcUserInfoMapper`: account-service 응답 → UserInfo 매핑 검증.
- 통합 테스트 (`@Tag("integration")`):
  - Discovery + JWKS 엔드포인트 응답 schema 검증.
  - `authorization_code` + PKCE E2E (테스트 client 1건 in-memory 등록).
  - `client_credentials` E2E.
  - `refresh_token` rotation + reuse detection.
  - revocation + introspection.
  - 회귀: 기존 `POST /api/auth/login` 동작 유지.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added and passing
- [ ] `specs/contracts/http/auth-api.md` 갱신 (oauth2 섹션 + login deprecate)
- [ ] `specs/contracts/http/gateway-api.md` 라우팅 갱신
- [ ] `specs/services/auth-service/architecture.md` 갱신
- [ ] CI green
- [ ] Ready for review
