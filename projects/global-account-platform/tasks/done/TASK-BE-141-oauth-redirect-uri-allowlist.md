# Task ID

TASK-BE-141

# Title

auth-service — OAuthLoginUseCase redirect_uri 화이트리스트 검증 (Critical C-2)

# Status

done

# Owner

backend

# Task Tags

- security
- critical

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

2026-04-27 보안 리뷰(C-2, Critical): `OAuthLoginUseCase.authorize()` / `callback()` 가 client 가 보낸 임의의 `redirectUri` 를 그대로 OAuth 프로바이더에 전달.

- `apps/auth-service/src/main/java/com/example/auth/application/OAuthLoginUseCase.java:56-57, 108-109`
- `OAuthProperties.ProviderProperties.redirectUri` 에 허용된 redirect URI 가 이미 설정되어 있음에도 검증되지 않음.

공격자가 피해자에게 spoofed `redirect_uri` 가 들어간 OAuth 시작 URL 을 클릭하게 만들면, 프로바이더가 인가 코드를 공격자 도메인으로 리다이렉트 → 공격자가 토큰 교환 → 계정 탈취. RFC 6749 §10.6 / RFC 9700 §4.1 의 전형적인 authorization code interception attack.

본 태스크는 client 제공 `redirectUri` 를 서버 설정의 화이트리스트와 정확 일치 비교하고 불일치 시 `InvalidOAuthRedirectUriException`(또는 기존 예외) 으로 거부한다.

---

# Scope

## In Scope

- `OAuthLoginUseCase.authorize(provider, redirectUri, ...)` 진입 시 redirectUri validation 추가
- `OAuthLoginUseCase.callback(provider, code, state, redirectUri, ...)` 도 동일하게 validation
- 프로바이더별 허용 redirect URI 목록을 `OAuthProperties.ProviderProperties.allowedRedirectUris: List<String>` 로 확장 (기존 단일 `redirectUri` 와 호환 유지)
- 검증 실패 시 명확한 에러 (HTTP 400, code `INVALID_REDIRECT_URI`) — 기존 예외 핸들러 패턴 따름

## Out of Scope

- 다른 OAuth 보안 컨트롤(state, nonce, PKCE) — 별도 태스크
- 프론트엔드 redirect 처리 변경 없음 (서버 검증 통과한 URI 만 통과하도록 단순화)

---

# Acceptance Criteria

- [ ] `authorize()` 진입 시 client 제공 `redirectUri` 가 `allowedRedirectUris` 와 정확 일치하지 않으면 `InvalidOAuthRedirectUriException` 발생
- [ ] `callback()` 도 동일 검증 수행 (token exchange 호출 전)
- [ ] `application.yml` 의 OAuth provider 설정에 `allowedRedirectUris` 항목 추가 (Google, Microsoft, Kakao 모두)
- [ ] 단위 테스트: 화이트리스트 일치 통과 / 불일치 거부 / null·blank 거부 / scheme·host 다른 경우 거부
- [ ] 통합 테스트(존재 시) 갱신 — 허용된 URI 만 통과
- [ ] `:apps:auth-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/oauth-social-login/*` (있으면)

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- `specs/contracts/api/auth-service/oauth.md` 또는 동등 — `redirectUri` 파라미터 검증 정책 명시 필요 시 갱신

---

# Target Service

- `auth-service`

---

# Architecture

Follow `specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- 비교는 정확 일치(exact string match) 권장 — prefix match 는 공격 벡터 (e.g., `https://example.com.attacker.com`).
- URI 정규화는 신중하게: 트레일링 슬래시 차이를 흡수하면 우회 위험 — 가능하면 정규화 없이 정확 일치.
- 에러 메시지는 어떤 URI 가 거부됐는지 노출하지 않도록 generic 하게 (정보 누출 방지).

---

# Edge Cases

- 동일 provider 에 환경별(local/staging/prod) 다른 redirect URI — `allowedRedirectUris: List<String>` 으로 다중 허용.
- 기존 단일 `redirectUri` 설정과의 호환: `allowedRedirectUris` 미설정 시 단일 값을 1-element list 로 사용.

---

# Failure Scenarios

- 정규화 로직 도입 시 우회 패턴 발생 — 정확 일치 유지.
- localhost 등 dev URI 가 prod allowlist 에 들어가면 dev/staging 토큰을 prod 가 받음 — env 별 분리 강조.

---

# Test Requirements

- `OAuthLoginUseCaseTest` 에 redirectUri validation 시나리오 추가 (4개 이상).
- 화이트리스트 미설정 시 명확한 에러 또는 fail-closed 동작.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] application.yml allowedRedirectUris 설정 추가
- [ ] Ready for review
