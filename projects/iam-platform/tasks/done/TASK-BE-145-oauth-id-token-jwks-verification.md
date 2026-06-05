# Task ID

TASK-BE-145

# Title

auth-service — Google/Microsoft id_token JWKS 서명 검증 추가 (High H-3)

# Status

done

# Owner

backend

# Task Tags

- security
- high

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

2026-04-27 보안 리뷰(H-3, High): `GoogleOAuthClient` / `MicrosoftOAuthClient` 가 id_token 의 두 번째 segment 를 Base64 디코드해 claim 만 추출, **서명을 검증하지 않음**.

- `apps/auth-service/.../oauth/GoogleOAuthClient.java:58-65`
- `apps/auth-service/.../oauth/MicrosoftOAuthClient.java:63-68`

영향: MITM 또는 프로바이더 응답 위조 시 공격자가 임의 `sub`/`email` 을 가진 id_token 을 공급해 피해자 소셜 ID 와 연결 → 계정 탈취. TLS 만으로는 불충분 — OIDC 스펙은 id_token 의 서버측 서명 검증을 의무화.

본 태스크는 Google/Microsoft 의 published JWKS 로 id_token 을 검증하고 `iss`/`aud`/`exp` claim 을 강제한다.

---

# Scope

## In Scope

- JWKS-backed JWT verifier 추가 (`libs/java-security` 또는 auth-service infra)
  - JWKS endpoint 캐싱 (TTL, kid rotation 지원)
  - JJWT + nimbusds-jose-jwt 조합 또는 spring-security-oauth2-jose 활용
- `GoogleOAuthClient` 가 id_token 을 위 verifier 로 검증 후 claim 추출
- `MicrosoftOAuthClient` 동일
- `iss` 검증: Google `https://accounts.google.com`, Microsoft `https://login.microsoftonline.com/{tenant}/v2.0` 등
- `aud` 검증: 설정된 clientId 와 일치
- `exp` 검증

## Out of Scope

- Kakao 는 별도 userinfo endpoint 호출 → id_token 미사용 → 본 태스크 out
- 다른 OIDC provider 추가는 별도 태스크
- 메인 플랫폼 JWT iss/aud 는 별도(TASK-BE-143)

---

# Acceptance Criteria

- [ ] JWKS 기반 id_token verifier 추가, kid 기반 키 선택 + 캐싱
- [ ] GoogleOAuthClient 가 id_token 서명 + iss + aud + exp 검증
- [ ] MicrosoftOAuthClient 동일 검증
- [ ] 서명 실패 / iss 불일치 / aud 불일치 / 만료 → `OAuthProviderException` (또는 dedicated 예외)
- [ ] 단위 테스트: 정상 토큰 통과 / 위조 서명 거부 / iss 불일치 거부 / aud 불일치 거부 / 만료 거부
- [ ] 통합 테스트: WireMock 등으로 JWKS 모킹 후 시나리오 검증
- [ ] `:apps:auth-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/oauth-social-login/*` (있으면)

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- 없음 (외부 OIDC 프로토콜 준수, 내부 계약 변경 없음)

---

# Target Service

- `auth-service` (+ `libs/java-security` 가능)

---

# Architecture

Follow `specs/services/auth-service/architecture.md`. infrastructure 레이어의 외부 클라이언트.

---

# Implementation Notes

- 추천 라이브러리: `com.nimbusds:nimbus-jose-jwt` (JWKS 캐시 + key selector 내장) 또는 `spring-security-oauth2-jose` 의 `NimbusJwtDecoder.withJwkSetUri()`.
- JWKS endpoint:
  - Google: `https://www.googleapis.com/oauth2/v3/certs`
  - Microsoft: `https://login.microsoftonline.com/common/discovery/v2.0/keys` (tenant 별 다름)
- 캐싱: 정상 응답 5–10분, 실패 1분 fallback. kid mismatch 시 캐시 즉시 갱신.
- `aud` 검증은 토큰의 aud 가 multi-value 일 수 있음 — clientId 가 포함됐는지로 검증.
- Microsoft 의 issuer 값은 tenant 별 다름 — multi-tenant 고려 시 `https://login.microsoftonline.com/{tenant}/v2.0` 패턴 매칭.
- `azp` claim 도 검증 권장 (Google 의 경우).

---

# Edge Cases

- JWKS endpoint 일시 장애 → 기존 캐시 사용 + 알람. 캐시 만료 + 갱신 실패 시 fail-closed (id_token 거부).
- 키 회전 직후 — kid 못찾으면 한 번 강제 refresh 후 재시도.
- Google 테스트 환경 등 다른 issuer 사용 시 application.yml 에서 분리 설정.

---

# Failure Scenarios

- 검증 누락된 채 배포 시 H-3 그대로 잔존 — 통합 테스트로 위조 토큰 거부 확인 의무.
- aud 검증 누락 → 다른 client 의 토큰을 우리 client 가 수용하는 OIDC token confusion.
- exp 검증 누락 → 만료된 id_token 으로 가입/로그인 가능.

---

# Test Requirements

- `GoogleOAuthClientTest` / `MicrosoftOAuthClientTest`:
  - JWKS 모킹 후 정상 토큰 통과
  - 잘못 서명된 토큰 거부
  - iss 불일치 거부
  - aud 불일치 거부 (다른 client_id 로 발급)
  - 만료 거부
- 캐시 동작 검증 (kid mismatch → JWKS refresh).

---

# Definition of Done

- [ ] JWKS verifier 구현
- [ ] Google/Microsoft 클라이언트 통합
- [ ] 단위/통합 테스트 통과
- [ ] application.yml JWKS endpoint / issuer 설정 추가
- [ ] Ready for review
