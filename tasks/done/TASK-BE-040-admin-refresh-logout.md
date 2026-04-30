# Task ID

TASK-BE-040-admin-refresh-logout

# Title

admin-service — operator refresh token rotation + logout + jti blacklist (세션 수명 관리)

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- db

# depends_on

- TASK-BE-029-3-admin-login-with-2fa

---

# Goal

admin operator의 session lifecycle 공백(자체 logout 없음, access 3600초 고정, jti 단위 revocation 없음)을 메운다. auth-service의 공개 사용자 refresh 구조를 재사용하되 operator 스케일과 audit-heavy 요건에 맞춘 최소 구현을 제공한다.

---

# Scope

## In Scope

### 1) Flyway V0015 — operator refresh token 저장소
- 테이블 `admin_operator_refresh_tokens`:
  - `jti CHAR(36) PRIMARY KEY`
  - `operator_id BIGINT NOT NULL REFERENCES admin_operators(id)`
  - `issued_at TIMESTAMP(6) NOT NULL`
  - `expires_at TIMESTAMP(6) NOT NULL`
  - `rotated_from CHAR(36) NULL` — 이전 jti
  - `revoked_at TIMESTAMP(6) NULL`
  - `revoke_reason VARCHAR(64) NULL` — LOGOUT / ROTATED / REUSE_DETECTED / FORCE_LOGOUT
- INDEX `(operator_id, issued_at DESC)` — 사용자당 토큰 조회용
- refresh token 원본은 저장하지 않음 (JWT 자체가 self-contained, jti만 트래킹)

### 2) JwtSigner 확장 — refresh token type
- 기존 `JwtSigner`로 두 종류 JWT 발급:
  - access: `token_type=admin`, TTL 3600초 (기존)
  - refresh: `token_type=admin_refresh`, TTL 30일, 별도 `admin.jwt.refresh-token-ttl-seconds` property
- refresh JWT claims: `{sub, iss=admin-service, jti, iat, exp, token_type=admin_refresh}`
- `IssuerEnforcingJwtVerifier`에서 두 token_type 모두 허용하되, 각 엔드포인트가 요구 type 검증

### 3) Login 응답 확장
- 기존 `POST /api/admin/auth/login` 성공 응답에 `refreshToken: string` + `refreshExpiresIn: int` 추가
- Login 성공 시 새 jti로 `admin_operator_refresh_tokens` insert

### 4) POST /api/admin/auth/refresh (신규 unauth sub-tree)
- body: `{refreshToken: string}`
- 검증:
  1. JWT 서명/exp 검증, `token_type=admin_refresh` 확인
  2. `admin_operator_refresh_tokens`에서 jti 조회
     - 미존재 또는 revoked → 401 `INVALID_REFRESH_TOKEN`
     - **rotated_from 체인에서 이미 revoked된 jti가 나타남 → 재사용 탐지** → operator 전체 refresh token revoke (reason=REUSE_DETECTED) + 401 `REFRESH_TOKEN_REUSE_DETECTED` + `admin.token.reuse.detected` Kafka 이벤트 발행 (기존 `auth.token.reuse.detected` 토픽 재사용 또는 `admin.security.events` 신규 토픽 판단 — 권장: admin-service 내부 감사만, security-service로의 이벤트 발행은 별도 태스크)
  3. 정상: 기존 jti revoke (reason=ROTATED), 새 access + 새 refresh 발급, 새 jti insert (rotated_from=기존 jti)
- 응답: `{accessToken, expiresIn, refreshToken, refreshExpiresIn}`
- 감사 row: `ActionCode.OPERATOR_REFRESH` (신규), outcome SUCCESS/FAILURE/REUSE_DETECTED

### 5) POST /api/admin/auth/logout (신규)
- **인증**: operator JWT(access token) 필수 — 029-1 bypass 목록에는 **추가하지 않음** (본인 확인 위해 인증 요구)
- body: `{refreshToken?: string}` (선택)
- 동작:
  - access JWT의 jti를 Redis blacklist `admin:jti:blacklist:{jti}` SETEX (TTL = access-token TTL 잔여 시간)
  - body.refreshToken 제공 시 해당 jti revoke (reason=LOGOUT)
  - 응답 204 No Content
- 감사 row: `ActionCode.OPERATOR_LOGOUT`, outcome SUCCESS

### 6) OperatorAuthenticationFilter — jti blacklist 체크
- access JWT 검증 통과 후 jti가 Redis blacklist에 있으면 401 `TOKEN_REVOKED`
- Redis 다운 시 fail-closed (401) — audit-heavy 원칙
- bypass 목록에 `POST /api/admin/auth/refresh` 추가 (refresh는 access 불필요)

### 7) Force-logout 내부 API (선택, out of scope 보강)
- 다른 operator를 강제 logout 시키는 경로는 이 태스크 범위 밖 (별도 `TASK-BE-041` 후보)

### 8) admin-api.md 계약
- `/api/admin/auth/login` 응답에 refresh 필드 추가
- `/api/admin/auth/refresh` 신규 엔드포인트 상세
- `/api/admin/auth/logout` 신규 엔드포인트 상세
- `/api/admin/auth/refresh`를 Authentication Exceptions sub-tree에 추가

### 9) platform/error-handling.md
- `INVALID_REFRESH_TOKEN` 401, `REFRESH_TOKEN_REUSE_DETECTED` 401, `TOKEN_REVOKED` 401 등록 (saas Admin Operations)

### 10) AdminActionAuditor 확장
- `ActionCode.OPERATOR_REFRESH`, `OPERATOR_LOGOUT` 추가
- 재사용 탐지 시 outcome=FAILURE + downstreamDetail="REUSE_DETECTED"

### 11) 테스트
- `AdminRefreshControllerTest` (slice):
  - 정상 refresh → 200 + 새 access+refresh, 기존 jti revoked
  - 만료된 refresh → 401 INVALID_REFRESH_TOKEN
  - 이미 rotated(revoked)된 refresh 재사용 → 401 REFRESH_TOKEN_REUSE_DETECTED + operator 전체 토큰 revoked
- `AdminLogoutControllerTest` (slice):
  - access JWT 포함 요청 → 204 + jti Redis blacklist 등록
  - refreshToken 포함 시 해당 jti revoke
- `OperatorAuthenticationFilterBlacklistTest`:
  - blacklist jti 요청 → 401 TOKEN_REVOKED
  - Redis 다운 → 401 fail-closed
- 기존 `AdminLoginControllerTest` 확장: login 응답에 refreshToken 포함 검증

## Out of Scope

- force-logout 다른 operator (별도 태스크)
- refresh token 저장소의 주기적 cleanup (cron — 별도 운영 태스크)
- admin security events Kafka 발행 (security-service 통합 별도)
- Grafana 대시보드 (refresh rate, reuse detection 카운터)

---

# Acceptance Criteria

- [ ] Login 응답에 refreshToken 포함, `admin_operator_refresh_tokens`에 row 생성
- [ ] 정상 refresh → 200 + 새 토큰 쌍, 기존 jti revoked(reason=ROTATED)
- [ ] rotated jti 재사용 → 401 REFRESH_TOKEN_REUSE_DETECTED + 해당 operator 모든 refresh revoked
- [ ] Logout → 204 + access jti가 Redis blacklist에 추가, refresh jti revoked
- [ ] blacklist jti로 보호된 엔드포인트 접근 → 401 TOKEN_REVOKED
- [ ] Redis 다운 시 blacklist 체크 fail-closed
- [ ] `admin_actions`에 REFRESH/LOGOUT row 기록
- [ ] admin-api.md / error-handling.md 갱신
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md` (Admin IdP Boundary 확장 — refresh 경로)
- `specs/services/admin-service/security.md` (Session Lifecycle 섹션 신설 권장)
- `specs/contracts/http/admin-api.md`
- `rules/traits/audit-heavy.md` A2 (envelope)
- `rules/traits/regulated.md` R9
- `apps/auth-service/.../RefreshTokenUseCase.java` (재사용 탐지 패턴 참조, 복제 금지 — 컨셉만)

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- refresh와 login을 짧은 간격으로 둘 다 실행 시 서로 다른 체인 존재 허용 (토큰 체인은 device/세션 단위가 아닌 발급 단위)
- refresh token 만료 직후 사용 → JWT exp 검증으로 401 INVALID_REFRESH_TOKEN
- Redis ping 성공했으나 SETNX/GET 실패 → fail-closed 401
- clock skew ±30s 허용(JWT 표준)

---

# Failure Scenarios

- `admin_operator_refresh_tokens` insert 실패 → 로그인 500, access token은 발급되었으나 refresh 없음 → 트랜잭션으로 묶어 둘 다 rollback
- Redis blacklist write 실패 시 logout 500, access token은 블랙리스트에 없으나 응답 기록됨 → 클라이언트 재시도 유도

---

# Test Requirements

- 위 테스트 4개 클래스
- `DlqRoutingIntegrationTest` 패턴 참조 가능

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] 계약·에러 레지스트리 갱신
- [ ] architecture.md/security.md Session Lifecycle 업데이트
- [ ] Ready for review
