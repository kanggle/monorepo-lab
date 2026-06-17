# Task ID

TASK-BE-397

# Title

Naver 소셜 로그인 제공자 추가 (SAS 브라우저 플로우)

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Goal

IAM 의 외부 IdP 제공자에 **Naver** 를 추가한다. TASK-BE-396 이 확립한 SAS 브라우저 플로우 통합 메커니즘 위에서, Naver 로 로그인한 사용자가 born-unified 계정으로 연결되고 SAS 표준 토큰을 발급받을 수 있도록 한다.

완료 후 참이 되어야 하는 것: `/login` 페이지에 **Naver 버튼**이 노출되고, Naver 인증 → SAS 세션 확립 → SAS 토큰 발급이 다른 제공자와 동일하게 동작한다.

---

# Scope

## In Scope

- `OAuthProvider` enum 에 `NAVER` 추가.
- `NaverOAuthClient` 구현(`OAuthClient` 포트). **Kakao 미러링** — Naver 도 비-OIDC OAuth2 + userinfo API 기반(id_token 미발급), Kakao 와 형태 유사.
  - auth-uri: `https://nid.naver.com/oauth2.0/authorize`
  - token-uri: `https://nid.naver.com/oauth2.0/token`
  - user-info-uri: `https://openapi.naver.com/v1/nid/me` (응답 `response.id` / `response.email` / `response.name`)
- `OAuthClientFactory` switch 에 `NAVER` 분기 추가.
- `application.yml` `oauth.naver.*` config 블록(client-id/secret/redirect-uri/allowed-redirect-uris/scopes/token-uri/auth-uri/user-info-uri). 기본값은 stub.
- `/login` 커스텀 페이지에 Naver 버튼 추가.
- `oauth-social-login.md` 의 지원 provider 목록에 Naver 반영.

## Out of Scope

- SAS 플로우 통합 메커니즘 자체(이미 TASK-BE-396).
- 실 Naver dev 앱 credential 프로비저닝(데모는 stub 가능, 라이브 E2E 는 credential 가용 시).

---

# Acceptance Criteria

- [ ] `OAuthProvider.from("naver")` 가 `NAVER` 를 반환한다.
- [ ] `OAuthClientFactory.getClient(NAVER)` 가 `NaverOAuthClient` 를 반환한다(switch exhaustive — 컴파일 보장).
- [ ] `NaverOAuthClient` 가 authorize URL 생성 + code↔token 교환 + userinfo(`response.{id,email,name}`) 파싱을 수행한다.
- [ ] `/login` 페이지에 Naver 버튼이 노출된다.
- [ ] Naver 이메일 미제공 시 기존 `EMAIL_REQUIRED` 규칙을 따른다.
- [ ] `oauth-social-login.md` 가 Naver 를 지원 provider 로 명시한다.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` + `rules/domains/saas.md` + 선언된 trait 파일들. Unknown tags = Hard Stop.

- `docs/adr/ADR-006-external-idp-login-sas-integration.md`
- `specs/features/oauth-social-login.md` (갱신 대상)
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/external-http-integration`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` (provider enum 값에 `naver` 추가)
- `specs/contracts/events/auth-events.md` (`loginMethod: OAUTH_NAVER`)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- 기존 `KakaoOAuthClient` 구조를 blueprint 로 사용(비-OIDC OAuth2 + userinfo).

---

# Implementation Notes

- 선행: **TASK-BE-396** 의 SAS 브라우저 플로우 통합이 머지되어 있어야 버튼·세션 종결이 의미를 가진다.
- Naver userinfo 응답은 `{ resultcode, message, response: { id, email, name, ... } }` 형태 — `response` 래퍼 안에서 추출.
- Naver 는 id_token(JWKS) 미발급 → Kakao 처럼 JWKS 검증 미적용, userinfo API 신뢰.

---

# Edge Cases

- Naver 이메일 동의 거부(이메일 미제공) → `EMAIL_REQUIRED`.
- 동일 이메일 기존 계정 → auto-link.
- userinfo `resultcode` 비정상 → `PROVIDER_ERROR`.

---

# Failure Scenarios

- Naver token/userinfo endpoint 장애 → `PROVIDER_ERROR`(브라우저: `/login?error=provider_error`).
- enum 추가 후 switch 미갱신 → 컴파일 실패(exhaustive switch 로 조기 적발).

---

# Test Requirements

- unit test: `NaverOAuthClient` authorize URL / token 교환 / userinfo 파싱(WireMock 또는 모킹).
- integration test: `OAuthClientFactory` 가 `NAVER` 를 해소.
- contract test: auth-api.md provider enum 정합.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required (`oauth-social-login.md`)
- [ ] Ready for review
