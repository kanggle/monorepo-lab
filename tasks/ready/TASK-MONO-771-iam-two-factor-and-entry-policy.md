# Task ID

TASK-MONO-771

# Title

`ADR-MONO-080` 단계 2 (D4 · R2 · R3) — **IAM 로그인에 2단계 인증(TOTP)** + 운영자 토큰 교환 · 회사 테넌트 진입에서 그 사실을 본다

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- platform-console
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (인증 경로 · 토큰 클레임 계약)

---

# Dependency Markers

- **선행**: `TASK-MONO-770` `done/` (ADR-080 D2 — 앞 단계 없이 뒤 단계가 나가지 않는다).
- **후속**: `TASK-MONO-772`.

# Goal

지금은 **플랫폼 관리자조차 주 경로로는 2단계 인증 없이** 콘솔에 들어온다 — 토큰 교환이 «운영자 행 + `ACTIVE`» 만 보고(`TokenExchangeService.java:72-106`), `require_2fa` 는 break-glass 로컬 로그인(`AdminLoginService.java:103-135`)에서만 문다. IAM(OIDC) 로그인에 TOTP 를 두고, 토큰에 «2단계를 거쳤다» 를 싣고, 진입 지점이 그것을 정책과 비교한다.

라이더(기본값): R2 — 회사 정책 = **테넌트 단위 플래그**(«이 테넌트 진입은 2단계 필수»), 역할 플래그 `require_2fa` 는 플랫폼 관리자용으로 남겨 **주 경로에서도** 물게 · R3 — 수단 = **TOTP**(admin-service 기존 구현을 계정 평면으로).

# Scope

## In Scope

- 계약 먼저: `platform/contracts/jwt-standard-claims.md` 에 `amr`(RFC 8176) — 첫 일
- auth-service: 계정 TOTP 등록 · 검증 · 복구 코드 · 로그인 흐름에 2단계 · `amr` 발급
- admin-service: 운영자 토큰 교환이 `require_2fa` 역할 보유자에게 `amr` 의 2단계를 요구 · assume-tenant 가 테넌트 정책 플래그와 비교
- 테넌트 정책 플래그(account-service `tenants` 또는 admin 평면 — 착수 시 결정, 계약 먼저) + 콘솔에서 켜고 끄는 자리
- 기존 운영자 전이: 정책을 켜는 순간 잠기지 않게 유예 기간 또는 등록 유도(ADR-080 § 새로 생기는 위험)

## Out of Scope

- 패스키 · SMS(R3 밖) · 회사 SSO(ADR-080 D4 범위 밖)

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: `anyRoleRequires2fa` 호출자 = `AdminLoginService` 하나 · `require_2fa = TRUE` 역할(`SUPER_ADMIN` · `SECURITY_ANALYST`) · auth-service 의 TOTP 참조 0. 정책 플래그의 집을 정하고 이유를 적는다.
- [ ] **AC-1** — 🔴 «전» 상태를 먼저 단언하는 시험: `require_2fa` 역할 운영자가 OIDC 토큰 교환으로 2단계 없이 운영자 토큰을 **받는다**(현재) → 구현 뒤 **받지 못한다**.
- [ ] **AC-2** — «2단계 필수» 테넌트로 assume 할 때 `amr` 에 2단계가 없으면 거절 · 다른 테넌트는 무영향(대조군).
- [ ] **AC-3** — 소비자 로그인은 TOTP 를 등록하지 않으면 지금과 같다.
- [ ] **AC-4** — 기존 운영자 전이 경로(유예 또는 등록 유도)가 시험 또는 라이브로 확인된다.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D1(분리 ≠ 2FA 면제) · D4 · R2 · R3
- `docs/adr/ADR-MONO-020`(assume-tenant) · `docs/adr/ADR-MONO-014`(토큰 교환)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (`amr`) · `projects/iam-platform/specs/contracts/http/admin-api.md`(토큰 교환 · assume-tenant 거절 코드)

# Edge Cases

- break-glass 로컬 로그인의 기존 TOTP — 계정 평면 TOTP 와 둘이 되지 않게 이전 또는 연결 방침을 정한다.
- TOTP 기기 분실 — 복구 코드 · 관리자 리셋 경로.

# Failure Scenarios

1. `amr` 없이 정책 검사만 넣는다 — 모든 진입이 거절된다.
2. 정책을 켜는 순간 기존 운영자가 전부 잠긴다(전이 경로 없음).
