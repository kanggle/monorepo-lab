# Task ID

TASK-BE-029-2-admin-totp-enrollment

# Title

admin-service — TOTP enrollment: V0013 + TotpGenerator + TotpSecretCipher(AES-GCM) + Bootstrap token + /2fa/enroll|verify

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- db

# depends_on

- TASK-BE-029-1-admin-jwt-signer-jwks-auth-bypass

---

# Goal

TOTP enrollment 하프 사이클을 완결한다. DB 스키마, TOTP 기본 primitives(생성·검증·AES-GCM 암복호), bootstrap token, enroll/verify 엔드포인트까지. 로그인 플로우는 029-3이 담당.

---

# Scope

## In Scope

### 1) Flyway `V0013__operator_totp_and_require_2fa.sql`
- `admin_operator_totp` 테이블:
  - `operator_id BIGINT PK + FK → admin_operators.id`
  - `secret_encrypted VARBINARY(512) NOT NULL` (layout: `[12B IV][N ciphertext][16B tag]`)
  - `secret_key_id VARCHAR(64) NOT NULL DEFAULT 'v1'`
  - `recovery_codes_hashed TEXT NOT NULL` (JSON 배열, Argon2id 해시 리스트)
  - `enrolled_at TIMESTAMP NOT NULL`, `last_used_at TIMESTAMP NULL`
- `admin_actions.twofa_used BOOLEAN NOT NULL DEFAULT FALSE`
- 기존 `trg_admin_actions_finalize_only`에 `twofa_used` mutation guard 추가
- `admin_roles.require_2fa`는 이미 V0004에 존재하므로 seed UPDATE만: SUPER_ADMIN, SECURITY_ANALYST → TRUE

### 2) TotpGenerator
- RFC 6238 HMAC-SHA1, 6자리, 30s step, ±1 window
- 160-bit secret 생성(SecureRandom), Base32(RFC 4648) 인코딩, otpauth:// URI 빌더
- 단위 테스트: 표준 벡터(RFC 6238 test vector 일부), window 검증

### 3) TotpSecretCipher (AES-GCM 256)
- 12-byte random IV per write, 128-bit tag
- single-column layout `[IV(12)][ciphertext][tag(16)]`
- AAD = `operator_id`의 byte 표현
- `encryption-keys: Map<String, String>` (kid → base64 key) + `encryption-key-id: v1`
- rotation: 새 kid로 read 시 lazy re-encrypt(next write) — 이번 태스크는 read path에 기존 kid로 복호만 지원, lazy re-encrypt는 enrollment 재수행 시에만 발생
- `@ConfigurationProperties("admin.totp")` + `@Validated`

### 4) Bootstrap Token
- `BootstrapTokenService`
  - 발급: 029-1 `JwtSigner`로 서명, claims `{sub=operatorId, token_type=admin_bootstrap, jti, iat, exp}`, TTL 10분
  - 검증: `BootstrapTokenFilter` (`OperatorAuthenticationFilter`와 별개, `/api/admin/auth/2fa/**` 경로에만 적용). jti 1회 소비(Redis key `admin:bootstrap:jti:{jti}` TTL 15분, SETNX → replay 시 401)
- `BootstrapContext` 스레드로컬 또는 Request scope — verify 후 operatorId 추출

### 5) 엔드포인트
- `POST /api/admin/auth/2fa/enroll` (Bootstrap token 필수):
  - 서버에서 secret 생성(TotpGenerator) → 암호화 저장(Cipher) → otpauth URI 반환
  - recovery codes 10개 생성 (포맷 `XXXX-XXXX-XXXX`, 대문자+숫자) → 평문 1회 응답 + Argon2id 해시 저장
  - 재enroll 호출(같은 operatorId): 기존 row 덮어쓰기. enroll은 verify 완료 전까지 언제든 재수행 가능
  - 응답: `{otpauthUri, recoveryCodes: [...], enrolledAt}`
- `POST /api/admin/auth/2fa/verify` (Bootstrap token 필수):
  - body `{totpCode}` → TotpGenerator 검증 → 성공 시 `last_used_at = now` set. 실패 시 401 `INVALID_2FA_CODE`
  - 응답: `{verified: true}`

### 6) AdminActionAuditor 확장
- 신규 `ActionCode.OPERATOR_2FA_ENROLL`, `OPERATOR_2FA_VERIFY`
- enroll/verify가 성공하면 `admin_actions` row 기록 (operator_id는 bootstrap token에서 해석). permission_used = `auth.2fa_enroll` / `auth.2fa_verify` (unauth 경로지만 감사 row는 남김)
- `twofa_used` 컬럼 매핑은 세팅되지만 enroll/verify 액션에서는 FALSE (로그인이 아님)
- `AdminActionAuditor` signature 변경 없이 context 확장(Start/Denied record에 twofa_used 필드 추가 가능)

### 7) AdminExceptionHandler
- 401 `INVALID_BOOTSTRAP_TOKEN`, 401 `INVALID_2FA_CODE`

### 8) platform/error-handling.md
- 위 두 코드 등록 (`[domain: saas]` Admin Operations 섹션 확장)

### 9) admin-api.md 계약
- 두 엔드포인트 상세 (request/response schema, 에러 표). `Exceptions` sub-tree 언급 갱신.

### 10) 테스트
- `TotpGeneratorTest` (RFC 6238 벡터 포함)
- `TotpSecretCipherTest` (encrypt→decrypt round-trip, IV randomness, AAD mismatch 시 실패)
- `BootstrapTokenServiceTest` (발급 + 검증 + jti 1회 소비)
- Slice: `Admin2FaEnrollControllerTest`, `Admin2FaVerifyControllerTest` — bootstrap token 요구, 재enroll, 잘못된 코드 401
- 감사 row 검증 (slice 내)

## Out of Scope

- `/api/admin/auth/login` 엔드포인트 (029-3)
- `require_2fa` 강제 로그인 분기 (029-3)
- `twofa_used=TRUE` 로그인 감사 경로 (029-3)

---

# Acceptance Criteria

- [ ] V0013 마이그레이션 적용 후 Flyway 상태 정상, 기존 테스트 회귀 없음
- [ ] `TotpGenerator` RFC 6238 표준 벡터 통과
- [ ] `TotpSecretCipher` encrypt/decrypt round-trip + AAD 변조 시 decryption 실패
- [ ] bootstrap token 발급·검증·jti 1회 소비 동작
- [ ] `/2fa/enroll` 호출 시 `admin_operator_totp` row + recovery_codes 해시 저장 + 평문 1회 응답
- [ ] `/2fa/verify` 유효 코드 → 200, 잘못된 코드 → 401 `INVALID_2FA_CODE`
- [ ] `admin_actions` 감사 row 생성
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/security.md`
- `specs/services/admin-service/data-model.md`
- `specs/contracts/http/admin-api.md`
- `rules/traits/regulated.md` R2/R9
- `rules/traits/audit-heavy.md` A2

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 동일 operator 재enroll: 기존 row UPDATE, recovery_codes 새로 생성(이전 codes 무효화)
- bootstrap token 만료 후 enroll 시도 → 401 `INVALID_BOOTSTRAP_TOKEN`
- bootstrap token 재사용(jti 소비됨) → 401
- AAD 변조 또는 secret_key_id 미존재 → 500 (operator에게 노출 X, 로그만)

---

# Failure Scenarios

- AES-GCM 키 누락 또는 길이 불일치 → `@PostConstruct` 시점 fail-fast
- Redis 다운 시 bootstrap jti 1회 소비 실패 → 안전하게 401(fail-closed) 또는 Redis degrade decision (현 단계 fail-closed)

---

# Test Requirements

- 위 열거

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review
