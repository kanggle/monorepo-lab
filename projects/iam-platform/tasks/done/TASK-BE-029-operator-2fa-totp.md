# Task ID

TASK-BE-029

# Title

admin-service — operator 2FA (TOTP) enrollment 및 로그인 강제

# Status

backlog

# Owner

backend

# Task Tags

- code
- api
- db

# depends_on

- TASK-BE-028
- TASK-BE-029a-admin-idp-boundary-and-totp-kms

---

# Goal

admin operator 로그인에 TOTP 2FA를 추가한다. role 단위로 enforcement 플래그를 두어 SUPER_ADMIN, SECURITY_ANALYST는 필수로 한다.

---

# Scope

## In Scope

- Flyway 마이그레이션: `admin_operator_totp` (operator_id, secret_encrypted, **secret_key_id VARCHAR(64) NOT NULL DEFAULT 'v1'**, recovery_codes_hashed, enrolled_at, last_used_at), `admin_roles.require_2fa` boolean
  - `secret_encrypted` 컬럼 layout은 `[12-byte IV][ciphertext][16-byte auth tag]` 단일 BYTEA/VARBINARY. 세부 규약은 [specs/services/admin-service/security.md](../../specs/services/admin-service/security.md) "TOTP Secret Encryption" 참조
  - `recovery_codes_hashed`는 **Argon2id via `libs/java-security.PasswordHasher`** hash array. BCrypt 등 다른 해시 사용 금지 ([specs/services/admin-service/rbac.md](../../specs/services/admin-service/rbac.md) "Recovery Codes Hashing Policy", regulated R2)
- 엔드포인트:
  - `POST /api/admin/auth/2fa/enroll` — secret 생성, QR URI 반환
  - `POST /api/admin/auth/2fa/verify` — enrollment 확정
  - `POST /api/admin/auth/login` 확장 — role에 require_2fa=true면 TOTP 코드 필수
- recovery codes 10개, one-time-use, **Argon2id (libs/java-security.PasswordHasher)** hashed 저장. BCrypt 등 대체 해시 금지 (regulated R2)
- `admin_actions`에 `2fa_used` 기록

## Out of Scope

- WebAuthn / passkey
- SMS OTP
- 공개 사용자 2FA

---

# Acceptance Criteria

- [ ] SUPER_ADMIN이 2FA 미등록 상태로 로그인 시도 → enrollment 강제 플로우
- [ ] enrollment 후 TOTP 코드 없이 로그인 → 401
- [ ] 유효 TOTP로 로그인 → 성공, `admin_actions.2fa_used=TRUE`
- [ ] recovery code 1회 사용 후 재사용 → 401

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/architecture.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- clock skew ±30s 허용
- secret은 AES-GCM 256-bit + per-write random 12-byte IV + 128-bit auth tag + `operator_id` AAD 바인딩으로 저장. 현 단계 키는 `admin.totp.encryption-key` application property placeholder, 프로덕션은 KMS/Vault (상세 규약은 [specs/services/admin-service/security.md](../../specs/services/admin-service/security.md) "TOTP Secret Encryption")
- `secret_key_id` 컬럼으로 key rotation 지원. rotate 시 **lazy re-encrypt on next access** 방식으로 전환

---

# Failure Scenarios

- recovery code DB 무결성 손상 → 관리자 개입 필요, 자동 복구 없음

---

# Test Requirements

- Unit: TOTP 검증 (TimeBasedOneTimePasswordUtil 계열)
- Integration: enrollment + login + recovery flow

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Ready for review
