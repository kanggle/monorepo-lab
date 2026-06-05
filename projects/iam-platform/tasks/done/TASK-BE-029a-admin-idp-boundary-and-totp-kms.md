# Task ID

TASK-BE-029a-admin-idp-boundary-and-totp-kms

# Title

admin-service — admin IdP 경계·TOTP KMS·hashing 정책·unauthenticated sub-tree specs-only 합의

# Status

ready

# Owner

backend

# Task Tags

- adr
- spec

# depends_on

- (없음)

---

# Goal

TASK-BE-029(operator 2FA TOTP)가 구현 단계에서 4건의 Hard Stop 블로커에 걸렸다. 이 태스크는 구현 없이 **specs/ADR만 갱신**하여 029 구현이 재개 가능한 상태로 만든다. 각 결정은 추후 변경을 최소화하도록 명시적으로 기록된다.

---

# Scope

## In Scope

### 결정 1 — admin IdP 경계
- `specs/services/admin-service/architecture.md`에 "Admin IdP Boundary" 섹션 추가.
- 질문 결정: admin-service가 **자체 JWT 발급자(IdP)**인가, 외부 IdP가 발급한 토큰만 **검증**하는가?
- 권장 기본: admin-service가 자체 발급 (IdP 역할 겸함). 근거: gateway/auth-service와 쿠폰 공유 없이 독립 signing key 운영 용이, 2FA 흐름과의 결합.
- 결과로 필요한 내용:
  - `JwtSigner` bean 요구사항 (RSA256 권장, kid rotation)
  - signing key 보관 위치 (현 단계 application property placeholder → 프로덕션은 KMS/Vault)
  - JWKS 노출 경로 (자체 `/.well-known/jwks.json` 또는 auth-service 공유 결정)
- `specs/services/admin-service/rbac.md` D4의 issuer 정의 업데이트.

### 결정 2 — hashing 정책 정렬
- 전 플랫폼 canonical hasher: `libs/java-security`의 Argon2id (regulated R2).
- TASK-BE-029 원 태스크의 "recovery codes BCrypt" 표현은 정정.
- `specs/services/admin-service/rbac.md` 또는 새 `specs/services/admin-service/2fa.md` 초안에 "recovery codes hashed via Argon2id (libs/java-security)"로 명시.
- 이유: R2 "비밀 값 hashing은 Argon2id canonical, 서비스 단위 우회 금지" 원칙 준수.

### 결정 3 — TOTP 비밀 AES-GCM key 관리 경로
- `specs/services/admin-service/security.md` (없으면 신설)에 "TOTP Secret Encryption" 섹션 추가.
- 단계별 정책:
  - **현 단계(포트폴리오/dev)**: `admin.totp.encryption-key` application property placeholder (256-bit base64). `@ConfigurationProperties` + `@NotBlank` fail-fast.
  - **프로덕션 마이그레이션**: AWS KMS / Vault 경로 ADR 링크 + key rotation 절차 요약 (rotate → re-encrypt lazy on next access).
- key id 컬럼 (`secret_key_id`) 추가 여부 결정. 권장: V0012 마이그레이션에 `secret_key_id VARCHAR(64) NOT NULL DEFAULT 'v1'` 포함.
- regulated R9 준수 증빙.

### 결정 4 — admin-api.md unauthenticated sub-tree 예외
- `specs/contracts/http/admin-api.md` 상단 "Authentication" 섹션에 예외 서브트리 선언 추가:
  ```
  Exceptions (no operator JWT required):
  - POST /api/admin/auth/login
  - POST /api/admin/auth/2fa/enroll  (but still gated by a temporary bootstrap token)
  - POST /api/admin/auth/2fa/verify  (same bootstrap token)
  ```
- `OperatorAuthenticationFilter.shouldNotFilter` 규칙 완화 범위 명시.
- `X-Operator-Reason` 헤더가 이 서브트리에 요구되지 않음을 명시.
- bootstrap token 설계 (엔롤먼트 플로우 시 임시 발급, short TTL, jti 캡처) 간단 초안.

## Out of Scope

- 실제 구현 (029 본 태스크에서)
- WebAuthn / passkey
- 외부 SaaS IdP 연동

---

# Acceptance Criteria

- [ ] `specs/services/admin-service/architecture.md`에 Admin IdP Boundary 섹션 존재
- [ ] `specs/services/admin-service/rbac.md` D4 issuer 업데이트
- [ ] hashing 정책이 Argon2id로 통일됨을 2fa 관련 스펙에 명시
- [ ] AES-GCM key 경로 + `secret_key_id` 정책 specs에 기록
- [ ] `specs/contracts/http/admin-api.md`에 unauthenticated sub-tree 선언
- [ ] TASK-BE-029 본문에 "depends_on: TASK-BE-029a" 추가 + 원 태스크에서 BCrypt 문구 제거 참조
- [ ] 리뷰 시 4개 블로커가 해소되어 구현 재개 가능

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/data-model.md`
- `specs/contracts/http/admin-api.md`
- `rules/traits/regulated.md` R2, R9
- `rules/traits/audit-heavy.md` A2

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `specs/` 및 `platform/` (spec-only)
- 후속 구현 영향: `apps/admin-service`

---

# Edge Cases

- 외부 IdP 도입 결정 시 IdP 경계 섹션 재작성 필요 — 재작성 트리거 조건 명시
- recovery code의 Argon2id 파라미터: 로그인 크리티컬 경로는 아니지만 동일 정책 적용
- bootstrap token 탈취 시 영향 범위: enrollment 한정, 짧은 TTL로 완화

---

# Failure Scenarios

- 4개 결정 중 하나라도 누락 → 029 재개 시 동일 Hard Stop 재발

---

# Test Requirements

- 없음 (spec-only)

---

# Definition of Done

- [ ] 4개 결정 모두 specs에 반영
- [ ] TASK-BE-029 태스크 파일 업데이트 (depends_on + 문구 정정)
- [ ] Ready for review
