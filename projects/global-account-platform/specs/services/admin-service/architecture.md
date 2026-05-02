# Service Architecture — admin-service

## Service

`admin-service`

## Service Type

`rest-api` — 운영자 전용 관리 서비스. lock/unlock, 강제 로그아웃, 감사 조회 프록시. **일반 사용자 트래픽과 완전히 분리된 인증 경계** 뒤에 배치.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Thin Layered (Command Gateway)** — `presentation / application / infrastructure` 3계층. 자체 도메인 데이터를 거의 보유하지 않는 **명령 오케스트레이션 서비스**. 도메인 상태는 downstream(auth / account / security)이 소유하고, admin-service는 다음의 흐름만 담당:

1. 운영자 인증 + 권한 검사
2. 명령 파라미터 검증
3. 다운스트림 서비스에 내부 HTTP 호출
4. 모든 행위를 감사 로그 + `admin.action.performed` 이벤트로 기록

## Why This Architecture

- **도메인 상태가 없음**: 운영자 작업의 "대상"은 모두 다운스트림(auth의 세션, account의 계정 상태, security의 감사 로그). admin-service 자체에 aggregate가 없으므로 `domain/` 레이어가 불필요.
- **격리가 핵심 속성**: [rules/domains/saas.md](../../../rules/domains/saas.md) S5는 admin 경로가 별도 인증 경계 + 감사 로그 필수. 아키텍처적으로 thin하게 유지해서 attack surface 최소화.
- **변경 사유 = 새 운영 명령 추가**: admin-service가 바뀌는 패턴은 "새 운영 기능" 또는 "새 감사 조회 요구"가 대부분. command-oriented 구조가 이 변경 축과 잘 정렬됨.
- **감사가 first-class**: 모든 admin command는 [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A5의 auditable action이며, 본 서비스가 감사 기록의 **issuer**이자 **gateway**.

## Internal Structure Rule

```
apps/admin-service/src/main/java/com/example/admin/
├── AdminApplication.java
│
├── presentation/
│   ├── AccountAdminController.java      ← lock/unlock/delete
│   ├── SessionAdminController.java      ← force-logout/session revoke
│   ├── AuditController.java             ← 감사 조회 프록시
│   ├── dto/
│   │   ├── LockAccountRequest.java
│   │   ├── UnlockAccountRequest.java
│   │   ├── RevokeSessionRequest.java
│   │   └── AuditQueryParams.java
│   └── exception/
│       └── AdminExceptionHandler.java
│
├── application/                         ← 명령 오케스트레이션
│   ├── LockAccountCommand.java
│   ├── UnlockAccountCommand.java
│   ├── RevokeSessionCommand.java
│   ├── QueryAuditCommand.java
│   ├── OperatorContext.java             ← 현재 실행 중인 운영자 + 사유 + 티켓 ID
│   ├── OperatorRoleResolver.java        ← (TASK-BE-121) use-case role-name → JPA entity 리졸버 + actor internal id 헬퍼. 패키지-사적, application 전용.
│   ├── AdminActionAuditor.java          ← 모든 command 전·후 감사 기록
│   └── event/
│       └── AdminEventPublisher.java     ← admin.action.performed outbox
│
└── infrastructure/
    ├── client/                          ← 내부 HTTP 클라이언트
    │   ├── AuthServiceClient.java       ← 강제 로그아웃 호출
    │   ├── AccountServiceClient.java    ← lock/unlock/delete 호출
    │   └── SecurityServiceClient.java   ← 감사 조회 호출
    ├── security/
    │   ├── OperatorAuthenticationFilter.java   ← 별도 인증 경계
    │   └── OperatorEndpointAccessResolver.java ← (planned, 미구현) role 기반 엔드포인트 접근 제어. application/OperatorRoleResolver(use-case 헬퍼)와 다른 책임.
    ├── persistence/                     ← 최소한의 로컬 상태만
    │   ├── AdminActionJpaEntity.java    ← 감사 원장 (append-only)
    │   └── AdminActionJpaRepository.java
    └── config/
```

**`domain/` 레이어 없음**. 필요 시 단순 값 객체는 `application/` 안에 두고, 별도 패키지로 분리하지 않는다.

## Allowed Dependencies

```
presentation → application → infrastructure/client
                    ↓
          infrastructure/persistence (append-only audit)
                    ↓
            infrastructure/security (인증 필터)
```

- `application/AdminActionAuditor`는 모든 command의 **before/after**를 `AdminActionJpaEntity`로 기록 + outbox 이벤트 발행
- `infrastructure/client/*`는 다른 서비스의 내부 HTTP 엔드포인트만 호출 ([specs/contracts/http/internal/](../../contracts/http/internal/))

## Forbidden Dependencies

- ❌ 다른 서비스의 DB에 직접 접근 — 반드시 내부 HTTP 경유
- ❌ 공개 게이트웨이와 **같은 인증 경계** 사용 — admin 전용 operator 토큰은 별도 발급 경로 (별도 IdP, 또는 auth-service의 admin-scope 토큰)
- ❌ `AdminActionJpaEntity`의 UPDATE/DELETE 경로 존재 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A3)
- ❌ 도메인 로직 이식 (예: 계정 상태 기계를 admin-service 안에 복제) — 상태 기계는 account-service 단일 소유
- ❌ 감사 기록 없이 명령 실행 — fail-closed ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A10)

## Boundary Rules

### presentation/
- 엔드포인트 prefix: `/api/admin/*`. 게이트웨이 라우트에서 **별도 인증 필터 체인** 적용
- 모든 요청은 `X-Operator-Reason` 헤더 또는 body 필드 필수 — 사유 없는 운영 작업 거부
- DTO에 운영자 ID는 주입되지 않음 — `OperatorAuthenticationFilter`가 JWT claim에서 추출하여 `OperatorContext`에 담음

### application/
- `LockAccountCommand`:
  1. `OperatorContext`로부터 operator id/role 검증
  2. `AdminActionAuditor.begin(action=ACCOUNT_LOCK, target=accountId, reason=...)`
  3. `AccountServiceClient.lock(accountId, reason, idempotencyKey=requestId)` 호출
  4. 결과에 따라 `AdminActionAuditor.complete(outcome=SUCCESS|FAILURE, detail=...)`
  5. 전체 흐름은 **단일 트랜잭션**: audit row + outbox event가 함께 커밋
  6. 실패 시 audit row는 `outcome=FAILURE`로 남김 (감사 누락 금지)
- `QueryAuditCommand`: 읽기 전용이지만 **조회 액션 자체도 감사 기록** (meta-audit)

### infrastructure/client/
- 내부 HTTP 호출에 반드시 `Idempotency-Key` 헤더 전달 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T1)
- 타임아웃·재시도·circuit breaker는 [rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I1-I3 준수
- 응답은 내부 도메인 모델로 번역 (I8) — 다운스트림 DTO를 그대로 presentation에 노출 금지

### infrastructure/security/
- `OperatorAuthenticationFilter`: admin-scope JWT 검증 + 2FA 확인 (선택)
- `OperatorEndpointAccessResolver` (planned, 미구현): role 기반 권한 (SUPER_ADMIN / ACCOUNT_ADMIN / AUDITOR 등) → 엔드포인트 접근 제어. **주의**: 이 클래스는 `application/OperatorRoleResolver`(TASK-BE-121에서 추가된 use-case role-name → JPA entity 리졸버)와 책임이 완전히 다르다. 이름 충돌을 피하기 위해 본 placeholder는 구현 시 `OperatorEndpointAccessResolver`(또는 동등한 RBAC 의도가 드러나는 이름)으로 명명한다.

### infrastructure/persistence/
- `admin_actions` 테이블은 **append-only** (DB 트리거 또는 권한 제한으로 UPDATE/DELETE 차단)
- 조회는 `AuditController`만 가능 (다른 서비스 불가)

## Admin IdP Boundary

admin-service는 일반 사용자 JWT와 **물리적으로 분리된 자체 IdP(Identity Provider)**이다. 즉 operator JWT 발급·서명·검증 전 과정을 admin-service가 소유하며, auth-service의 user JWT 발급 경로를 공유하지 않는다.

### Decision

- **Role**: admin-service = **self-issuing operator IdP**. 외부 IdP의 토큰을 수용하지 않는다.
- **Rationale**:
  - gateway/auth-service의 user JWT kid rotation 주기와 독립적 운영(operator 서명 키 compromise 시 user 토큰 revocation 영향 없음).
  - 2FA(TOTP) 플로우가 operator JWT 발급 시점의 `totp_verified_at` 클레임과 강결합 — 별도 IdP 경로에서 주입 단순화.
  - `token_type = "admin"` + 별도 issuer로 user 토큰과의 혼용을 **서명 수준**에서 차단 (rbac.md D4).
  - 독립적 kid rotation과 감사 경계(regulated R9) 준수.

### JwtSigner Bean Requirements

- **Algorithm**: RS256 (RSA 2048-bit 이상). HS* 대칭 키 금지 (regulated R9 영구 키 금지 원칙 + JWKS 공개 운영 용이성).
- **Kid rotation**: 서명 키는 `kid` 포함. 현재 kid 1개 + 이전 kid N개 병행 검증(grace period). rotation 주기는 운영 ADR에서 확정(초안 90일).
- **JWKS endpoint**: admin-service가 자체 노출 — `GET /.well-known/admin/jwks.json` (auth-service의 `/.well-known/jwks.json`과 **다른 경로**. 다운스트림 서비스가 operator 토큰을 검증해야 하는 경우 이 경로에서 public key를 취득한다).
- **Issuer claim**: `iss = "admin-service"` (rbac.md D4와 일치).
- **Token type claim**: `token_type = "admin"` 필수. 누락/불일치 시 admin-service 자체에서 401.

### Signing Key Storage

| 단계 | 보관 위치 | 로딩 방식 |
|---|---|---|
| 현 단계(포트폴리오/dev) | `admin.jwt.signing-key-pem` application property placeholder (private key PEM) | `@ConfigurationProperties` + `@NotBlank` fail-fast. 로컬/dev는 `application-local.yml`, CI/staging은 환경변수 |
| 프로덕션(향후) | AWS KMS / Vault (asymmetric sign operation 또는 unwrap 경로) | 별도 ADR 링크 placeholder — 본 섹션 Migration Trigger 참조 |

### JWKS Exposure Policy

- `/.well-known/admin/jwks.json`은 **무인증 GET 허용**. public key만 노출하므로 `X-Operator-Reason` 헤더도 요구하지 않는다. 이 경로는 [specs/contracts/http/admin-api.md](../../contracts/http/admin-api.md)의 Exceptions 서브트리에 포함된다.
- JWKS 응답은 현재 활성 kid + grace period 중인 직전 kid를 모두 포함.
- cache-control: `public, max-age=300` 권장 (rotation 시 stale window 최대 5분).

### Session Lifecycle (TASK-BE-040)

operator session 수명은 다음 두 토큰으로 구성된다:

- **access JWT** — `token_type=admin`, TTL 1시간 (`admin.jwt.access-token-ttl-seconds`).
- **refresh JWT** — `token_type=admin_refresh`, TTL 30일 (`admin.jwt.refresh-token-ttl-seconds`). 발급 시 `admin_operator_refresh_tokens(jti)` row가 함께 INSERT 된다(같은 트랜잭션). refresh JWT 본문은 self-contained 하므로 서버는 `jti`만 트래킹.

`POST /api/admin/auth/refresh`는 기존 refresh를 회전한다 — 기존 jti는 `revoke_reason=ROTATED`로 revoke되고 `rotated_from` 필드가 새 row에 기록된다. 이미 revoked된 jti가 다시 제시되면 (재사용 탐지) 해당 operator의 모든 미revoked refresh token이 `REUSE_DETECTED`로 일괄 revoke 되며 401 `REFRESH_TOKEN_REUSE_DETECTED`가 반환된다.

`POST /api/admin/auth/logout`은 access JWT의 jti를 Redis blacklist(`admin:jti:blacklist:{jti}`)에 잔여 TTL 만큼 등록한다. `OperatorAuthenticationFilter`가 매 요청마다 이 키를 확인하여 hit 시 401 `TOKEN_REVOKED`를 반환한다. Redis 다운 시 fail-closed (audit-heavy A10).

이 절은 [security.md §Session Lifecycle](./security.md)에서 더 상세히 규정된다.

### Migration Trigger (re-open conditions)

다음 조건 중 하나가 충족되면 본 섹션을 재작성:

- 외부 SaaS IdP(Okta/AzureAD 등) 도입 결정
- 키 보관이 KMS/Vault로 이전되는 시점(별도 ADR 생성 및 링크)
- operator JWT와 user JWT를 동일 issuer로 통합하는 결정

## Integration Rules

- **HTTP 컨트랙트 (외부)**: [specs/contracts/http/admin-api.md](../../contracts/http/) — admin 전용 엔드포인트
- **HTTP 컨트랙트 (out-going)**:
  - [specs/contracts/http/internal/admin-to-auth.md](../../contracts/http/internal/) — 강제 로그아웃
  - [specs/contracts/http/internal/admin-to-account.md](../../contracts/http/internal/) — lock/unlock/delete
  - security-service의 query 엔드포인트 사용 (내부)
- **이벤트 발행**: [specs/contracts/events/admin-events.md](../../contracts/events/) — `admin.action.performed` (항상 발행)
- **퍼시스턴스**: MySQL — `admin_actions` (append-only 감사 원장), `outbox_events`
- **Redis**: 필요 시 operator rate limit, 세션 nonce

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | Command 오케스트레이션 흐름, 실패 시 audit 기록 유지 | JUnit 5 + Mockito |
| Integration | 전체 lock 흐름 → AccountServiceClient mock → audit row + 이벤트 | Testcontainers + WireMock |
| Security | 비-admin 토큰으로 `/api/admin/*` 호출 시 403 | Spring Security 테스트 |
| Audit immutability | `admin_actions` UPDATE/DELETE 시도 → 거부 | DB trigger test |
| Fail-closed | 감사 기록 실패 시 command 전체 실패 | Integration |

**필수 시나리오**: operator 없이 호출 → 401 / role 부족 → 403 / reason 없음 → 400 / 다운스트림 5xx → command 실패 + audit row는 FAILURE로 기록 / 중복 요청 Idempotency-Key 재사용 → 다운스트림이 멱등 응답 / meta-audit: 감사 조회도 `admin_actions`에 row.

## Change Rule

1. 새 운영 명령 추가는 [specs/features/admin-operations.md](../../features/) + 해당 내부 컨트랙트 ([specs/contracts/http/internal/](../../contracts/http/internal/)) 업데이트 선행
2. 권한 모델(role) 추가·변경은 [specs/features/admin-operations.md](../../features/)의 role matrix 갱신
3. 감사 스키마 변경은 [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A2 표준 필드 준수 확인 + Flyway migration
4. 이 서비스는 **도메인 로직을 수용하지 않는다**. 운영 명령이 복잡해지면 해당 도메인 소유 서비스(auth / account / security)로 로직을 이동하고 admin-service는 호출만 유지

## Tenant Scope Enforcement (TASK-BE-249)

admin-service의 모든 운영 행위는 **테넌트 스코프**를 기준으로 격리된다.

### Sentinel Value

- `tenant_id = '*'` — **플랫폼 스코프 센티넬**. SUPER_ADMIN 역할 보유 운영자에게 부여되며 모든 테넌트에 걸쳐 명령/조회 권한을 가진다.
- 모든 일반 운영자는 특정 `tenant_id`(예: `"fan-platform"`) 에 속하며, 자신의 테넌트 범위 안에서만 행위할 수 있다.

### DB 스키마 변경 (V0025)

| 테이블 | 추가 컬럼 | 비고 |
|---|---|---|
| `admin_operators` | `tenant_id VARCHAR(32) NOT NULL` | 기존 고유 인덱스 `uk_admin_operators_email` → 복합 인덱스 `(tenant_id, email)` |
| `admin_operator_roles` | `tenant_id VARCHAR(32) NOT NULL` | 역할 바인딩도 테넌트 소속 명시 |
| `admin_actions` | `tenant_id VARCHAR(32) NOT NULL`, `target_tenant_id VARCHAR(32)` | 감사 행이 어떤 테넌트에서 어떤 테넌트를 대상으로 했는지 기록 |

### 감사 행 구성 규칙

- **일반 운영자** 행위: `tenant_id = operator.tenantId`, `target_tenant_id = operator.tenantId`
- **SUPER_ADMIN** 행위: `tenant_id = '*'`, `target_tenant_id = <대상 테넌트 ID>`
- `OPERATOR_DENY` 행: `tenant_id = operator.tenantId`, `target_tenant_id = operator.tenantId` (크로스 테넌트 거부도 자기 테넌트 기록)

### `PermissionEvaluator.isTenantAllowed` 기본 규칙

```
isTenantAllowed(operator, targetTenantId):
  if operator == null → false
  if operator.isPlatformScope() → true   (SUPER_ADMIN: always pass)
  if targetTenantId == null → true       (null compat: 자기 테넌트)
  return operator.tenantId == targetTenantId
```

### 예외 매핑

| 예외 클래스 | HTTP 상태 | 에러 코드 |
|---|---|---|
| `TenantScopeDeniedException` | 403 | `TENANT_SCOPE_DENIED` |

### 감사 쿼리 라우팅

| 운영자 유형 | `tenantId` 파라미터 | 사용 Repository 메서드 |
|---|---|---|
| 일반 운영자 | 자기 테넌트 또는 null | `findByTenantId(operatorTenantId, ...)` |
| SUPER_ADMIN | `*` | `findByTenantId('*', ...)` (플랫폼 행만) |
| SUPER_ADMIN | 특정 테넌트 | `searchCrossTenant(targetTenantId, ...)` |
| 일반 운영자 | 다른 테넌트 | → `TenantScopeDeniedException` |

ADR: [docs/adr/ADR-002-admin-tenant-scope-sentinel.md](../../../../docs/adr/ADR-002-admin-tenant-scope-sentinel.md)

---

## Overrides

- **rule**: rules/traits/audit-heavy.md#A10
- **reason**: login failure audit error는 원래 401 에러를 500으로 변형시키지 않아야 한다 (관측성·UX). 성공 경로만 fail-closed 유지.
- **scope**: admin-service login controller 실패 경로 (AdminAuthController.safeRecordLogin FAILURE 분기)
- **expiry**: permanent

- **rule**: rules/traits/audit-heavy.md#A2
- **reason**: `POST /api/admin/auth/refresh`의 `InvalidRefreshTokenException` 경로(서명 검증 실패 / jti 미등록 / sub·jti 누락)에서는 operator id를 **신뢰할 수 있는 소스**(검증된 레지스트리 row)에서 얻을 수 없다. TASK-BE-040-fix 이후 컨트롤러는 미검증 JWT payload의 Base64 디코딩을 금지하므로, 이 경로의 감사 row는 `operator_id=null`(A2 표준 필드 중 actor 식별자 공백)로 기록되거나, `admin_operators` FK 해석 실패로 {@code AuditFailureException}이 던져지면 FAILURE 분기의 `safeRecordSession`이 이를 swallow하여 감사가 생략된다. 401 응답의 가시성은 그대로 유지된다. 성공 경로(`RefreshResult.operatorId`)와 재사용 탐지 경로(`RefreshTokenReuseDetectedException.operatorId`)는 검증된 UUID를 전달하므로 A2 준수.
- **scope**: AdminAuthController.refresh — `InvalidRefreshTokenException` catch 블록 한정
- **expiry**: permanent
