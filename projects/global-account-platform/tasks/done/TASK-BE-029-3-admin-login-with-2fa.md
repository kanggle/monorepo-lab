# Task ID

TASK-BE-029-3-admin-login-with-2fa

# Title

admin-service — /auth/login + require_2fa 강제 + recovery code one-time-use + twofa_used 감사

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-029-2-admin-totp-enrollment

---

# Goal

operator 로그인 엔드포인트를 완성하고, role 기반 2FA 강제 및 recovery code one-time-use를 구현하며, `admin_actions.twofa_used` 감사를 기록한다.

---

# Scope

## In Scope

### 1) POST /api/admin/auth/login
- body: `{operatorId, password, totpCode?, recoveryCode?}`
- 단계:
  1. operator 조회 + `PasswordHasher` (libs/java-security Argon2id) 검증
  2. 실패 → 401 `INVALID_CREDENTIALS` + 실패 audit row (outcome=FAILURE)
  3. operator role 중 `require_2fa=TRUE`가 하나라도 존재하면 2FA 필수:
     - enrollment 미완료 (`admin_operator_totp` row 없음) → 401 `ENROLLMENT_REQUIRED` + **bootstrap token 반환** (029-2 BootstrapTokenService 재사용)
     - `totpCode` 제공: TotpGenerator 검증. 성공 시 `last_used_at=now`, `twofa_used=TRUE`. 실패 시 401 `INVALID_2FA_CODE`
     - `recoveryCode` 제공: 저장된 hash 배열과 Argon2id `matches` 순회. 일치 hash 제거 후 row update → `twofa_used=TRUE`. 실패 시 401 `INVALID_RECOVERY_CODE`
     - 둘 다 제공 또는 둘 다 미제공 → 400 `BAD_REQUEST`
  4. require_2fa=FALSE (NONE) 이면 2FA 건너뜀
- 성공 시 JwtSigner로 operator JWT 발급: claims `{sub=operator_uuid, iss="admin-service", jti, iat, exp, token_type=admin}`. 응답 `{accessToken, expiresIn}`
- 감사 row: `admin_actions (action=OPERATOR_LOGIN, outcome=SUCCESS|FAILURE|DENIED, twofa_used=boolean)`, audit envelope canonical 준수 (028b2)

### 2) Recovery code consumption 일관성
- `matches` 경로에서 hash 리스트를 직렬화된 JSON column으로 읽어 일치 시 해당 항목 제거 후 전체 배열 update. 동시성 보호를 위해 `@Transactional` + optimistic lock on `admin_operator_totp` row.

### 3) AdminActionAuditor 확장
- `ActionCode.OPERATOR_LOGIN` 추가
- `recordStart`/`recordDenied` context에 `twofa_used: boolean` 반영 (기존 canonical envelope에 `meta.twofa_used` 필드로 추가하거나 action-level 컬럼 반영. envelope 변화 없이 column만 갱신 권장)

### 4) AdminExceptionHandler
- 401 `INVALID_CREDENTIALS`, 401 `ENROLLMENT_REQUIRED` (bootstrap token body 포함), 401 `INVALID_RECOVERY_CODE`, 400 `BAD_REQUEST`

### 5) platform/error-handling.md
- 위 새 에러 코드 등록 (`[domain: saas]` Admin Operations)

### 6) admin-api.md 계약
- `/api/admin/auth/login` 엔드포인트 상세 (request/response schema, 각 에러 케이스)

### 7) 테스트
- Slice `AdminLoginControllerTest`:
  - SUPER_ADMIN 미등록 → 401 ENROLLMENT_REQUIRED + bootstrap token 반환
  - enrollment 완료 후 totpCode 없이 → 401 INVALID_2FA_CODE (or BAD_REQUEST when missing)
  - 유효 totpCode → 200 + JWS + `admin_actions.twofa_used=TRUE`
  - recovery code 사용 후 같은 code 재사용 → 401 INVALID_RECOVERY_CODE
  - require_2fa=FALSE operator → totpCode 없이 200
  - password 실패 → 401 INVALID_CREDENTIALS + FAILURE audit row

## Out of Scope

- enrollment/verify endpoints (029-2)
- JwtSigner/JWKS (029-1)
- KMS 프로덕션 마이그레이션 (후속)

---

# Acceptance Criteria

- [ ] SUPER_ADMIN 미등록 로그인 → 401 ENROLLMENT_REQUIRED + bootstrap token
- [ ] 2FA 미제출 로그인 → 401
- [ ] 유효 TOTP 로그인 → 200 + JWT, `twofa_used=TRUE` audit row
- [ ] recovery code 1회 사용 후 재사용 → 401
- [ ] require_2fa=FALSE → TOTP 없이 200
- [ ] password 실패 → 401 + FAILURE audit row
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/rbac.md`
- `specs/services/admin-service/security.md`
- `specs/contracts/http/admin-api.md`
- `rules/traits/audit-heavy.md` A2
- `rules/traits/regulated.md` R2

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 패스워드 검증 전에 operatorId 열람 미스 → 타이밍 공격 완화 위해 constant-time comparison 근사
- `totpCode`, `recoveryCode` 둘 다 제공 → 400 (정책상 택일)
- 동시 로그인으로 같은 recovery code 경쟁 → optimistic lock으로 하나만 성공, 다른 하나 retry 후 INVALID

---

# Failure Scenarios

- Argon2id 검증 중 IO 실패 → 500, 명확한 에러 코드 없이 로그 기록

---

# Test Requirements

- 위 열거

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review

---

# Acceptance Criteria (Revision, post-review)

본 AC는 구현·계약(`specs/contracts/http/admin-api.md`) 및 후속 리뷰 결과와 정렬한다.

- enrollment 완료 상태(`admin_operator_totp` row 존재) + `require_2fa=TRUE` operator에 대해 `totpCode`·`recoveryCode`가 **둘 다 미제공**이면 **400 `BAD_REQUEST`** 를 반환한다 (`AdminLoginService`가 `InvalidLoginRequestException` 발생). 기존 원 AC의 "2FA 미제출 → 401" 표현은 enrollment 미완료(→ 401 `ENROLLMENT_REQUIRED`) 케이스와 혼동을 유발하므로 본 Revision을 정본으로 삼는다.
- 둘 다 제공되는 경우에도 동일하게 400 `BAD_REQUEST` (택일 정책).
- 잘못된 TOTP/Recovery 값 → 401 `INVALID_2FA_CODE` / `INVALID_RECOVERY_CODE` (원 AC 유지).

상세 후속: `TASK-BE-029-3-fix-audit-failclosed-ac-align-seed-guard`.
