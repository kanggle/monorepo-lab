# admin-service — Security Policies

본 문서는 admin-service의 **보안 정책 중 architecture.md·rbac.md에 담기 어려운 항목**(암호화 키 관리, 비밀 컬럼 인코딩, rotation 절차)을 선언한다. JWT 발급 경계와 RBAC 모델 자체는 각각 [architecture.md](./architecture.md)의 "Admin IdP Boundary" 섹션과 [rbac.md](./rbac.md)가 담당한다.

관련 규칙:
- [rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R2 (encryption at rest), R9 (secrets & rotation)
- [rules/traits/audit-heavy.md](../../../../../rules/traits/audit-heavy.md) A2 (audit field standards)

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

## IAM OIDC Subject-Token Validation (TASK-BE-298 / ADR-MONO-014)

`POST /api/admin/auth/token-exchange` ([admin-api.md](../../contracts/http/admin-api.md))
는 platform-console 이 보유한 **IAM OIDC `platform-console-web` access token**
(subject token)을 operator access token 으로 교환한다. 본 절은 그 subject
token 검증 정책을 규정한다. operator token **발급**은 기존 login-success
발급기를 재사용하며 새 서명 키를 도입하지 않는다 ([architecture.md
§Operator-Token Minting Paths](./architecture.md)).

### Trust Source

- subject token 은 **auth-service(SAS) 가 발급한 OIDC access token**이다.
  서명 검증 공개키는 **auth-service JWKS**(`GET {auth-service}/internal/auth/jwks`,
  [auth-service JwksController](../../../apps/auth-service/src/main/java/com/example/auth/presentation/JwksController.java))
  에서 취득한다. 이는 admin-service 자체 JWKS
  (`/.well-known/admin/jwks.json`)와 **다른 키 공간**이다 — operator 서명 키와
  OIDC 검증 키는 절대 혼용하지 않는다.
- auth-service JWKS 도달 불가 시 **fail-closed**: 토큰을 미검증 상태로
  신뢰하지 않고 `401`(operator token 미발급). cached key 로 무한정
  fallback 하지 않는다 ([platform/contracts/jwt-standard-claims.md](../../../../../platform/contracts/jwt-standard-claims.md)
  JWKS 가용성 원칙과 정렬).

### Required Validations (모두 통과해야 함 — 하나라도 실패 시 `401 TOKEN_INVALID`)

| # | 검증 | 실패 시 |
|---|---|---|
| 1 | **RS256 서명** — auth-service JWKS 공개키로 검증. `alg=none`·HS* 거부. | `401` |
| 2 | **`iss`** == IAM OIDC issuer (`admin.oidc.issuer`, 기본 auth-service `oidc.issuer-url`). | `401` |
| 3 | **`aud`** 가 `platform-console-web` 를 포함 (SAS 는 authorization_code grant access token 의 `aud` 를 등록 client_id 로 설정). 다른 client 에 발급된 토큰 거부 (audience check — task Edge Case). | `401` |
| 4 | **`exp`** 미만료, **`nbf`** 도래 (둘 다 아래 clock-skew 허용 범위 내). | `401` |
| 5 | **IAM OIDC access token 형태 확인** — `token_type` claim 부재. IAM OIDC access token 은 `token_type` 커스텀 claim 을 싣지 않는다 ([auth-api.md Token Claims](../../contracts/http/auth-api.md)); `token_type` 가 존재하면(=admin/admin_refresh/admin_bootstrap 등 admin-service 자체 발급 토큰) **거부** — operator/bootstrap 토큰을 subject token 으로 우회 제시하는 경로 차단. | `401` |
| 6 | **`sub`** 존재 (account_id UUID). 부재 시 거부. | `401` |

### Clock-Skew Tolerance

- auth-service ↔ admin-service 시계 비동기 완화: `exp`/`nbf` 검증에 **±60초**
  허용 (`admin.oidc.clock-skew-seconds`, 기본 60 — jwt-standard-claims.md
  "Clock Skew" 60초 권장과 정렬). 그 범위를 벗어난 만료/미도래는 `401`.

### OIDC Subject → Operator Resolution (fail-closed)

- 검증 통과한 subject token 의 `sub`(account_id UUID)를 **링크 키**로
  `admin_operators` row 를 해석한다. 링크 키 선택(provisioned
  `admin_operators.oidc_subject` 컬럼)과 근거는 [data-model.md §OIDC Subject
  ↔ Operator Link Key](./data-model.md) 가 canonical.
- 다음 중 하나라도 해당하면 **fail-closed `401 TOKEN_INVALID`**(기존
  `OperatorUnauthorizedException`) — operator token **미발급**:
  - `oidc_subject` 가 어떤 `admin_operators` row 와도 매칭되지 않음 (미매핑).
  - 매칭된 operator 의 `status != ACTIVE` (DISABLED/LOCKED — 비활성/잠금).
- operator 해석 성공 시 **tenant 스코프는 `admin_operators.tenant_id` 에서만**
  결정된다 (ADR-002 `'*'` SUPER_ADMIN platform sentinel 포함). subject token
  의 `tenant_id`/`tenant_type` 등 어떤 claim 도 스코프 결정에 사용하지 않으며,
  OIDC token 으로 스코프가 상승하는 경로는 존재하지 않는다 (task Failure
  Scenario "Scope leak").

### Replay / Lifetime

- 발급되는 operator token 은 단명(≤ operator access TTL,
  `admin.jwt.access-token-ttl-seconds`)이며 별도 operator-refresh state 를
  남기지 않는다. console 은 자신의 IAM refresh 로 rotate 한 access token 으로
  매번 재교환한다 (ADR-MONO-014 D2 — re-exchange 모델).

### Boundary Invariant (ADR-MONO-014 D1 — Option A 기각)

- `OperatorAuthenticationFilter` 는 본 검증 경로로 인해 **변경되지 않는다**.
  exchange 는 별도 발급(minting) 경로이며 검증(verification) 경계의 확장이
  아니다. raw IAM OIDC 토큰을 `/api/admin/**` 의 일반 엔드포인트에 제시하면
  여전히 `401` — 회귀 테스트가 이를 고정한다 ([architecture.md
  §Operator-Token Minting Paths](./architecture.md)).

---

## Operator Credential Convergence (TASK-BE-377 / ADR-MONO-035 § O2 — step 4c)

ADR-MONO-032 D5 step 4 는 운영자를 **통합 IAM OIDC credential** 로 수렴시킨다. 운영자의
**PRIMARY 로그인**은 OIDC base 로그인(`platform-console-web`) + 이미 배선된 ADR-MONO-014
`POST /api/admin/auth/token-exchange`(OIDC → operator token)이다. 로컬 비밀번호 로그인
(`POST /api/admin/auth/login`)은 **break-glass**(비상 로컬 로그인 — IdP/OIDC 경로 불가용 시)로만
잔존한다.

- **`admin_operators.password_hash` 는 NULLABLE 로 강등**(제거 아님 — O6 가용성 불변식; 완전
  제거는 OIDC-only admin 로그인 입증 후 후속). `NULL` = OIDC-only 운영자(로컬 비밀번호 없음).
- **break-glass 의미**: `AdminLoginService` 는 `password_hash == NULL` 인 운영자의 비밀번호 로그인을
  **fail-closed**(타이밍 완화 dummy verify 후 `401 INVALID_CREDENTIALS`)한다 — 그런 운영자는 OIDC
  로만 인증한다. `password_hash` 가 있는 운영자는 break-glass 비밀번호 로그인이 그대로 동작한다.
- **프로비저닝**: `POST /api/admin/operators` 의 `password` 는 **선택**이다 — 누락 시 OIDC-only
  운영자(`password_hash=NULL`), 제공 시 hash 되어 break-glass 로 잔존(제공 시 정책 강제).
- **OIDC↔operator 링크 키는 `oidc_subject` 불변**(data-model §OIDC Subject ↔ Operator Link Key);
  token-exchange 가 OIDC subject → operator 를 결정적·fail-closed 로 해석한다. 비밀번호 강등이 이
  해석 경로를 넓히지 않는다.
- **TOTP/2FA 는 admin-service-internal 불변**(`admin_operator_totp`; O4) — step 4 에서 OIDC base
  로그인에 접히지 않는다(OIDC-side step-up 은 ADR-032 D4-B deferred).

---

## Out of Scope

- password at-rest hashing(operator 로그인 비밀번호): `libs/java-security.PasswordHasher` canonical Argon2id 사용. rbac.md 및 regulated R2에서 이미 규정됨. **TASK-BE-377**: 비밀번호는 break-glass(nullable)로 강등됐으나 해싱 알고리즘 자체는 불변.
- WebAuthn/passkey, hardware token 관리
- KMS/Vault 구체 선정 및 연동 구현(Phase C 별도 ADR + 태스크)
