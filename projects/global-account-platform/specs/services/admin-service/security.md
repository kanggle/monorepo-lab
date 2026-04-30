# admin-service — Security Policies

본 문서는 admin-service의 **보안 정책 중 architecture.md·rbac.md에 담기 어려운 항목**(암호화 키 관리, 비밀 컬럼 인코딩, rotation 절차)을 선언한다. JWT 발급 경계와 RBAC 모델 자체는 각각 [architecture.md](./architecture.md)의 "Admin IdP Boundary" 섹션과 [rbac.md](./rbac.md)가 담당한다.

관련 규칙:
- [rules/traits/regulated.md](../../../rules/traits/regulated.md) R2 (encryption at rest), R9 (secrets & rotation)
- [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A2 (audit field standards)

---

## TOTP Secret Encryption

admin operator의 TOTP shared secret(`admin_operator_totp.secret_encrypted`)은 평문으로 저장하지 않는다. 본 절은 암호화 방식, 키 보관, key id 관리, rotation 절차를 규정한다.

### Algorithm

- **Cipher**: AES-GCM (authenticated encryption). CBC/ECB 금지.
- **Key size**: 256-bit.
- **IV**: **매 write마다 random 12-byte** 생성. IV 재사용 금지.
- **Auth tag**: 128-bit, ciphertext에 append.
- **Storage layout (single bytea column)**:
  ```
  [ 12-byte IV ][ ciphertext ][ 16-byte auth tag ]
  ```
  단일 `BYTEA`/`VARBINARY` 컬럼에 concatenated 저장. 컬럼 분리 금지(부분 손상 방지).
- **AAD**: `operator_id`(내부 BIGINT PK)의 big-endian 8-byte 표현을 AAD로 사용 — row swap 공격 방어.

### Encryption Key Storage

| 단계 | 보관 위치 | 로딩 방식 |
|---|---|---|
| 현 단계(포트폴리오/dev) | `admin.totp.encryption-key` application property placeholder (256-bit base64) | `@ConfigurationProperties` + `@NotBlank` fail-fast. 애플리케이션 부팅 시 길이/인코딩 검증, 불합격 시 start-up abort |
| 프로덕션(향후) | AWS KMS (envelope encryption) 또는 HashiCorp Vault Transit | 별도 ADR 링크 placeholder — Phase C 마이그레이션 태스크(TBD)에서 연결 |

소스 코드·깃 저장소·Dockerfile에 키 리터럴 포함 금지 (regulated R9). dev 환경은 `.env`/`application-local.yml` + gitignore.

### Key ID & Schema

`admin_operator_totp` 테이블에 **`secret_key_id VARCHAR(64) NOT NULL DEFAULT 'v1'`** 컬럼을 추가한다. 의미:

- 이 row의 `secret_encrypted`를 **어떤 key로 암호화했는지** 식별.
- row를 읽을 때 `secret_key_id`로 복호화 키를 선택. 여러 key가 공존 가능(rotation grace period).
- 값 규약: `v1`, `v2`, ... (순차). KMS 전환 시 `kms:alias/admin-totp-v1` 형식 허용(naming convention은 migration 태스크에서 확정).

> 이 컬럼 추가 지시는 TASK-BE-029의 V0012 마이그레이션 명세에 반영된다(아래 "Migration Note" 참조).

### Rotation Procedure

1. **신규 key 등록**: 새 `secret_key_id`(예: `v2`) 를 key store에 등록. application property/KMS alias 활성화.
2. **dual-read**: application은 `v1`/`v2` 두 key를 모두 로드하며, row의 `secret_key_id`에 따라 복호화 key를 선택한다.
3. **Lazy re-encrypt on next access**: operator가 다음 번 2FA verify를 수행할 때(= secret을 decrypt해야 하는 시점), 성공 후 **새 key로 re-encrypt 하고 `secret_key_id = 'v2'`로 update**한다. enrollment 신규 row는 항상 최신 key로 암호화.
4. **Eager 마이그레이션 허용**: 보안 사고 대응 시, 관리자용 별도 job을 실행해 미전환 row를 일괄 re-encrypt 가능(본 태스크 out of scope).
5. **Retire old key**: 모든 row가 새 key id로 전환된 것을 확인한 뒤 이전 key를 store에서 제거.

rotation 트리거: 정기(연 1회 권장) 또는 사고 대응(key compromise 의심 시 즉시).

### Compliance Mapping

- **regulated R2 (Encryption at rest)**: `confidential` 이상 등급인 TOTP secret에 대해 대칭 암호화 후 저장. 본 섹션의 AES-GCM + per-write random IV + AAD binding이 R2를 충족.
- **regulated R9 (Secrets management & rotation)**: 영구 키 금지 — `secret_key_id` 컬럼과 lazy re-encrypt 절차가 **rotation path가 설계되어 있음**의 증빙.
- **audit-heavy A2**: TOTP secret 암호화/복호화 자체는 감사 이벤트 발생 경로가 아니지만, 2FA verify 결과(`2fa_used=TRUE/FALSE`)는 `admin_actions` 표준 필드에 기록된다(TASK-BE-029 범위).

### Migration Note (for TASK-BE-029)

V0012 마이그레이션(`admin_operator_totp` 생성)은 본 절의 정책을 반영하여 다음 컬럼을 포함한다:

- `secret_encrypted` — BYTEA/VARBINARY, NOT NULL (저장 layout은 위 "Algorithm" 참조)
- `secret_key_id` — VARCHAR(64), NOT NULL, DEFAULT `'v1'`
- `recovery_codes_hashed` — Argon2id hash array ([rbac.md](./rbac.md) Recovery Codes Hashing Policy)
- `enrolled_at`, `last_used_at` — TIMESTAMP

---

## Bootstrap Token (2FA Enrollment/Verify Flow)

`/api/admin/auth/2fa/enroll`·`/api/admin/auth/2fa/verify`는 정식 operator JWT를 아직 발급받기 전의 운영자도 호출할 수 있어야 한다([admin-api.md](../../contracts/http/admin-api.md) Authentication Exceptions 참조). 이 구간을 보호하기 위한 **bootstrap token** 초안:

- **발급 시점**: `POST /api/admin/auth/login`에서 operator의 password verify가 성공했으나 2FA가 아직 완료되지 않은 상태에서 응답 body로 반환.
- **Scope**: 오직 `/api/admin/auth/2fa/enroll` 및 `/api/admin/auth/2fa/verify` 두 엔드포인트에서만 유효. 다른 경로에서 사용 시 401.
- **TTL**: 10분(enrollment UX 여유 + exfiltration window 최소화).
- **Claims 필수**:
  - `sub` = `operator_id` (canonical)
  - `token_type` = `"admin_bootstrap"` (정식 `"admin"`과 구분)
  - `scope` = `["2fa_enroll", "2fa_verify"]`
  - `jti` — 서버가 한 번 consume한 뒤 replay 차단에 사용(Redis key `admin:bootstrap:jti:{jti}` TTL = token 잔여시간)
  - `iss` = `"admin-service"`, `exp`, `iat`
- **서명 키**: 정식 operator JWT와 **동일한 signing key**로 서명(kid rotation 혜택 공유). token_type으로만 구분.
- **X-Operator-Reason 요구 없음**: 이 서브트리는 운영 행위(감사 대상 명령)가 아니라 **자신의 enrollment** 수행이므로 사유 헤더 미요구. `admin_actions`에는 `action_code = OPERATOR_2FA_ENROLL|OPERATOR_2FA_VERIFY`, `target_type = OPERATOR`, `target_id = operator_id` 로 기록되며 reason은 `"<self_enrollment>"` 상수.

상세 구현(bootstrap token 생성 API 위치, verify 실패 시 token 무효화 등)은 TASK-BE-029 범위.

---

## Session Lifecycle (TASK-BE-040)

본 절은 operator JWT의 발급·회전·취소(blacklist) 정책을 규정한다. 발급 자체는 [architecture.md §Admin IdP Boundary](./architecture.md)가 소유하고, 본 절은 lifecycle 전이만 다룬다.

### Refresh Token Registry

- 테이블: `admin_operator_refresh_tokens(jti PK, operator_id FK, issued_at, expires_at, rotated_from, revoked_at, revoke_reason)`.
- refresh JWT는 self-contained — 본문은 저장하지 않는다. registry는 `jti`만으로 revocation/reuse-detection을 가능하게 한다.
- 인덱스 `(operator_id, issued_at DESC)` — 사용자당 토큰 일괄 revoke 및 최신 발급 조회.

### Rotation

- `POST /api/admin/auth/refresh` 호출 시 기존 jti는 `revoke_reason=ROTATED`로 revoke 되고 새 jti가 `rotated_from`=기존jti로 INSERT 된다.
- JPA insert + JWT 발급은 단일 트랜잭션 — partial state 금지.

### Reuse Detection

- 이미 revoked된 jti가 다시 refresh 엔드포인트에 제시되면 chain 전체가 compromise 되었다고 간주.
- 해당 operator의 모든 `revoked_at IS NULL` refresh token을 `revoke_reason=REUSE_DETECTED`로 일괄 revoke.
- 클라이언트에는 401 `REFRESH_TOKEN_REUSE_DETECTED` 반환.

### Logout & jti Blacklist

- `POST /api/admin/auth/logout`(인증 필요)은 access JWT의 jti를 Redis 키 `admin:jti:blacklist:{jti}`에 SETEX 한다 (TTL = access exp - now).
- `OperatorAuthenticationFilter`는 access JWT 검증 후 매 요청마다 이 키 존재 여부를 확인. hit → 401 `TOKEN_REVOKED`.
- **Fail-closed**: Redis 호출 실패 시 `isBlacklisted`는 `true`를 반환(audit-heavy A10). 이는 알 수 없는 revocation 상태의 토큰을 통과시키지 않기 위함이다.
- body에 `refreshToken`이 함께 제시되면 그 jti도 `revoke_reason=LOGOUT`으로 revoke (best-effort).

### Out-of-scope (별도 태스크)

- 다른 operator를 강제 logout 시키는 force-logout 내부 API
- `admin_operator_refresh_tokens`의 만료된 row 주기적 cleanup cron
- security-service로 `admin.token.reuse.detected` Kafka 이벤트 전파

---

## Out of Scope

- password at-rest hashing(operator 로그인 비밀번호): `libs/java-security.PasswordHasher` canonical Argon2id 사용. rbac.md 및 regulated R2에서 이미 규정됨.
- WebAuthn/passkey, hardware token 관리
- KMS/Vault 구체 선정 및 연동 구현(Phase C 별도 ADR + 태스크)
