# Task ID

TASK-BE-143

# Title

libs/java-security — Rs256JwtVerifier iss/aud claim 강제 검증 (High H-1)

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

2026-04-27 보안 리뷰(H-1, High): 메인 플랫폼 JWT 검증 경로(`gateway-service`, `community-service`)가 `iss`/`aud` claim 을 검증하지 않음.

- `libs/java-security/src/main/java/com/gap/security/jwt/Rs256JwtVerifier.java:33-38`
- `apps/gateway-service/src/main/java/com/example/gateway/security/TokenValidator.java:80-85`
- `apps/community-service/.../JwtConfig.java`
- 참고: `admin-service` 는 `IssuerEnforcingJwtVerifier` 로 올바르게 wrap 되어 있음 (모범 사례)

서명만 검증하면 동일 RS256 키로 서명된 어떤 토큰이든 통과 — 다른 환경(test/staging) 키가 잘못 신뢰되거나 community-service 토큰이 account-service 에 replay 되는 token confusion 가능.

본 태스크는 `Rs256JwtVerifier` 에 `expectedIssuer` 옵션을 추가하거나 `IssuerEnforcingJwtVerifier` 패턴을 메인 경로에도 일관 적용한다.

---

# Scope

## In Scope

- `Rs256JwtVerifier` 에 issuer/audience 검증 옵션 추가 (생성자 주입 or builder)
- `gateway-service.TokenValidator` 가 expected issuer 를 주입받아 검증
- `community-service.JwtConfig` 도 동일 적용
- 다른 서비스에서 `Rs256JwtVerifier` 를 직접 사용하면 동일 적용
- `application.yml` 에 `auth.jwt.expected-issuer` (필수), `auth.jwt.expected-audience` (서비스별, 선택) 추가

## Out of Scope

- 기존 `admin-service` 의 `IssuerEnforcingJwtVerifier` 는 그대로 유지 (이미 올바름)
- JWKS 도입(별도 태스크 H-3 참고)
- Token type claim(`type`) 검증은 별도 태스크(L-3)

---

# Acceptance Criteria

- [ ] `Rs256JwtVerifier` 가 expectedIssuer 일치하지 않는 토큰을 거부 (`InvalidJwtException` 또는 동등)
- [ ] expectedAudience 가 설정된 경우 일치하지 않는 토큰을 거부
- [ ] 만료, 서명 검증은 기존과 동일하게 동작
- [ ] gateway, community-service 가 application.yml 의 expected-issuer 를 주입받아 사용
- [ ] `auth-service` 토큰 발급 시 동일 issuer 값 사용 (이미 동일하지 않다면 정렬)
- [ ] 단위 테스트: issuer 일치/불일치/누락, audience 일치/불일치/누락
- [ ] 통합 테스트: 잘못된 issuer 토큰으로 gateway 호출 → 401
- [ ] `:libs:java-security:test`, `:apps:gateway-service:test`, `:apps:community-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md` (token issuer 정의)
- `specs/services/gateway-service/architecture.md`
- `specs/services/community-service/architecture.md`

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- `specs/contracts/api/auth-service/jwt.md` (있으면) — issuer/audience claim 정의 명시

---

# Target Service

- `libs/java-security` + `gateway-service` + `community-service`

---

# Architecture

Follow `platform/shared-library-policy.md` for libs change. Service 적용은 각 service architecture 따름.

---

# Implementation Notes

- `Rs256JwtVerifier` 시그니처 예시:
  ```java
  public Rs256JwtVerifier(PublicKey publicKey, String expectedIssuer) {
      this(publicKey, expectedIssuer, null);
  }
  public Rs256JwtVerifier(PublicKey publicKey, String expectedIssuer, String expectedAudience) {
      ...
  }
  ```
- JJWT 0.12.x 기준:
  ```java
  Jwts.parser()
      .verifyWith(publicKey)
      .requireIssuer(expectedIssuer)
      .build()
      .parseSignedClaims(token)
  ```
  audience 는 직접 비교하거나 `requireAudience` 사용.
- 기존 `admin-service` 의 `IssuerEnforcingJwtVerifier` 는 wrapper 로 유지하거나 새 verifier 로 합쳐 단일화.

---

# Edge Cases

- auth-service 가 발급한 기존 토큰의 issuer 값과 신규 검증 기댓값이 다르면 즉시 모든 사용자 401 — 발급/검증 양쪽 동시 배포 또는 grace period 필요. 구현 노트에 마이그레이션 절차 명시.
- audience 미설정 시 검증 skip — 모든 서비스에 audience 설정 권장하되 단계적 도입.

---

# Failure Scenarios

- 검증 추가 후 발급 측 issuer 가 다르면 production outage — 사전에 발급 토큰 sample 의 issuer 클레임 확인.
- 다국어/환경 분리(예: prod 와 staging) 의 issuer 가 다르면 환경 간 토큰 격리 효과 — 의도된 동작.

---

# Test Requirements

- `Rs256JwtVerifierTest`:
  - issuer 일치 → 통과
  - issuer 불일치 → 예외
  - issuer 누락 → 예외
  - audience 미설정 + 토큰에 audience 있어도 → 통과
  - audience 설정 + 토큰 audience 일치/불일치
- gateway/community 통합 테스트: 잘못된 issuer 토큰으로 호출 → 401.

---

# Definition of Done

- [ ] Rs256JwtVerifier 변경 + 테스트
- [ ] gateway/community 적용 + 테스트
- [ ] 발급/검증 issuer 정렬 확인
- [ ] Ready for review
