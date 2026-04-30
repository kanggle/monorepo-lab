# Service Architecture — account-service

## Service

`account-service`

## Service Type

`rest-api` — 계정·프로필 전담 서비스. 회원가입, 프로필 CRUD, 계정 상태(active / locked / dormant / deleted) 상태 기계.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Layered Architecture + 명시적 상태 기계** — `presentation / application / domain / infrastructure` 4계층에 `domain/account-status/`가 상태 기계를 명시적으로 보유.

## Why This Architecture

- **상태 전이가 핵심 비즈니스 규칙**: 계정 상태는 여러 축(사용자 요청, 운영자 명령, 자동 탐지, 휴면 처리, GDPR 삭제 유예)에서 변경될 수 있고, 모든 전이에는 명시적 허용/불허 규칙이 있음. 이를 직접 `UPDATE`로 처리하면 `rules/traits/transactional.md` T4 위반.
- **credentials와의 물리적 분리**: [rules/domains/saas.md](../../../rules/domains/saas.md) S1에 따라 Profile(여기)과 Credentials(auth-service)는 물리 분리. 같은 프로세스에 두는 것은 금지.
- **감사 로그 결합**: 모든 상태 변경이 `account_status_history` 불변 테이블과 이벤트로 영속화 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1·A3·A7 교차).
- **다운스트림 통합의 허브**: 상태 변경은 `account.status.changed` 이벤트로 발행되어 auth-service의 세션 무효화, security-service의 이력 갱신 등 다운스트림 흐름의 source of truth가 됨.
- **테넌트 메타의 소유자**: account-service는 계정 소유 서비스이므로 [specs/features/multi-tenancy.md](../../features/multi-tenancy.md)에 정의된 `Tenant` 엔터티(테넌트 등록·상태)도 본 서비스가 소유한다. 모든 도메인 테이블은 `tenant_id` NOT NULL 컬럼을 보유하며 unique key는 `(tenant_id, email)` 등 복합 형태로 구성. cross-tenant leak 방지가 격리 회귀 테스트의 필수 항목.

## Internal Structure Rule

```
apps/account-service/src/main/java/com/example/account/
├── AccountApplication.java
├── presentation/
│   ├── SignupController.java
│   ├── ProfileController.java
│   ├── AccountStatusController.java
│   ├── internal/                        ← 내부 HTTP 엔드포인트 (공개 X)
│   │   ├── CredentialLookupController.java
│   │   ├── AccountStatusQueryController.java
│   │   ├── AccountLockController.java   ← security-service / admin-service 호출
│   │   └── TenantProvisioningController.java  ← /internal/tenants/{tenantId}/accounts (WMS 등 enterprise 소비자)
│   ├── dto/
│   └── exception/
├── application/
│   ├── SignupUseCase.java
│   ├── UpdateProfileUseCase.java
│   ├── ChangeAccountStatusUseCase.java
│   ├── DeleteAccountUseCase.java        ← 유예 + 익명화 경로
│   ├── ProvisionTenantAccountUseCase.java  ← 내부 provisioning (역할 부여 포함)
│   └── event/
│       └── AccountEventPublisher.java
├── domain/
│   ├── account/
│   │   ├── Account.java                 ← 엔터티 (aggregate root). tenant_id 필수
│   │   ├── AccountId.java
│   │   └── Email.java                   ← 값 객체, 유효성 검증
│   ├── tenant/
│   │   ├── Tenant.java                  ← 엔터티: tenant_id, display_name, tenant_type, status
│   │   ├── TenantId.java                ← 값 객체 (slug 정규식 검증)
│   │   ├── TenantType.java              ← enum: B2C_CONSUMER / B2B_ENTERPRISE
│   │   ├── TenantStatus.java            ← enum: ACTIVE / SUSPENDED
│   │   └── TenantRoleCatalog.java       ← (tenant_id, role_name) 등록 도메인
│   ├── profile/
│   │   ├── Profile.java
│   │   ├── DisplayName.java
│   │   └── PhoneNumber.java
│   ├── status/
│   │   ├── AccountStatus.java           ← enum: ACTIVE / LOCKED / DORMANT / DELETED
│   │   ├── AccountStatusMachine.java    ← 전이 규칙 정의
│   │   ├── StatusTransition.java        ← 값 객체
│   │   └── StatusChangeReason.java      ← enum
│   ├── history/
│   │   └── AccountStatusHistoryEntry.java
│   └── repository/
│       ├── AccountRepository.java       ← 모든 메서드 첫 인자 tenant_id 필수
│       ├── ProfileRepository.java
│       ├── TenantRepository.java
│       └── AccountStatusHistoryRepository.java
└── infrastructure/
    ├── persistence/
    │   ├── AccountJpaEntity.java         ← tenant_id 컬럼 NOT NULL, unique (tenant_id, email)
    │   ├── ProfileJpaEntity.java
    │   ├── TenantJpaEntity.java
    │   ├── AccountStatusHistoryJpaEntity.java
    │   └── *JpaRepository.java
    ├── kafka/
    │   └── AccountKafkaProducer.java     ← 모든 이벤트 페이로드에 tenant_id 포함
    ├── anonymizer/
    │   └── PiiAnonymizer.java            ← R7 익명화 로직
    └── config/
```

## Allowed Dependencies

```
presentation → application → domain
                     ↓
              infrastructure → domain
```

- `presentation/internal/` 엔드포인트도 동일한 application use-case를 호출 — 별도 백도어 금지
- `application` → [libs/java-messaging](../../../libs/java-messaging) (outbox)
- `infrastructure/anonymizer` → `domain/profile` (PII 필드 식별)

## Forbidden Dependencies

- ❌ `presentation/` 공개 컨트롤러가 `internal/` 엔드포인트 로직을 노출 — 공개 API와 내부 API는 별개의 URL prefix (`/api/accounts/*` vs `/internal/accounts/*`)
- ❌ 계정 상태를 `UPDATE accounts SET status = ?`로 직접 변경 — 반드시 `AccountStatusMachine.transition()` 경유
- ❌ `Profile` 테이블에 `password_hash`·`2fa_secret`·`oauth_refresh_token` 저장 ([rules/domains/saas.md](../../../rules/domains/saas.md) S1)
- ❌ 삭제를 `DELETE FROM accounts WHERE id = ?`로 즉시 실행 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R7) — 유예 + 익명화 경로만
- ❌ 외부 요청이 `internal/` 경로에 도달 — gateway는 `/internal/*`을 공개 라우트로 열지 않음
- ❌ `tenant_id` 없이 `AccountRepository.findByEmail(email)` 같은 조회를 수행 — 모든 도메인 쿼리는 `tenant_id` 파라미터 필수 (cross-tenant leak 방지)
- ❌ `Account`/`Profile`/`AccountStatusHistory` 등 `tenant_id` NULL row 생성 — Flyway 마이그레이션에서 NOT NULL + 기본값 백필이 완료된 후 컬럼에 의존

## Boundary Rules

### presentation/
- 공개: `/api/accounts/signup`, `/api/accounts/me`, `/api/accounts/me/profile`, `/api/accounts/me/status`
- 내부(`presentation/internal/`): 다른 서비스 전용. 별도 인증 경계(mTLS 또는 내부 토큰). 게이트웨이 경유 금지
- 내부 provisioning: `/internal/tenants/{tenantId}/accounts` (POST/GET), `/internal/tenants/{tenantId}/accounts/{accountId}/roles|status|password-reset` — WMS 등 enterprise 소비자가 사용. path `{tenantId}`와 호출 주체의 tenant scope 불일치 시 403 `TENANT_SCOPE_DENIED`. 상세는 [specs/features/multi-tenancy.md](../../features/multi-tenancy.md)
- 응답에서 `password_hash`·`deleted_at` 같은 민감 필드 **절대 제외** ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4)

### application/
- `SignupUseCase`: 이메일 중복 검사 → Account + Profile 생성 → `account.created` 이벤트 (outbox)
- `ChangeAccountStatusUseCase`: 입력(operator, reason, target status) → `AccountStatusMachine.transition()` → status_history 저장 → `account.status.changed` 이벤트. **같은 트랜잭션 내에서 모두 커밋** (T3·A7)
- `DeleteAccountUseCase`: 즉시 삭제 금지. 상태를 `DELETED`(pending)로 전이 + 유예 기간 만료 후 `PiiAnonymizer`가 PII 필드 NULL/해시 처리

### domain/status/
- `AccountStatusMachine.transition(current, target, reason)` → 허용되지 않으면 `STATE_TRANSITION_INVALID` 예외
- 허용되는 전이 (예시):
  - `ACTIVE → LOCKED` (reason: admin, auto_detect, password_failure_threshold)
  - `LOCKED → ACTIVE` (reason: admin, user_recovery)
  - `ACTIVE → DORMANT` (reason: 365일 미접속)
  - `DORMANT → ACTIVE` (reason: user_login)
  - `ACTIVE/LOCKED/DORMANT → DELETED` (reason: user_request, admin, regulated_deletion)
  - `DELETED → ACTIVE` (reason: within_grace_period, admin_only)
- 금지 전이는 명시적으로 리스트 (예: `DELETED → LOCKED` 같은 sideways transition)

### infrastructure/anonymizer
- `PiiAnonymizer.anonymize(profile)` → email/phone/display_name 등 PII 필드를 `anon_{hash(original)}` 형태로 교체
- 삭제 유예 기간(예: 30일) 만료 후 배치 또는 이벤트 드리븐으로 실행
- 원복 불가능성 보장

## Integration Rules

- **HTTP 컨트랙트 (외부)**: [specs/contracts/http/account-api.md](../../contracts/http/)
- **HTTP 컨트랙트 (내부)**:
  - [specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) (credential lookup 요청 수신, `tenant_id` 파라미터 포함)
  - [specs/contracts/http/internal/security-to-account.md](../../contracts/http/internal/) (자동 잠금 명령 수신)
  - [specs/contracts/http/internal/admin-to-account.md](../../contracts/http/internal/) (관리자 상태 변경 수신)
  - `account-internal-provisioning.md` (신규, `/internal/tenants/{tenantId}/accounts` — WMS 등 enterprise 소비자 대상)
- **이벤트 발행**: [specs/contracts/events/account-events.md](../../contracts/events/) — `account.created`, `account.status.changed`, `account.locked`, `account.unlocked`, `account.deleted`. 모두 outbox 경유. **모든 페이로드에 `tenant_id` 필수**
- **퍼시스턴스**: MySQL — `tenants`, `accounts`, `profiles`, `account_status_history`, `outbox_events`. 도메인 테이블의 unique index는 `(tenant_id, email)` 등 복합 형태
- **Redis**: 가입 이메일 검증 코드 TTL, 중복 가입 요청 dedupe (key 패턴에 `tenant_id` 포함)

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | `AccountStatusMachine` 전이 규칙 (허용/불허), 값 객체 검증 | JUnit 5 |
| Repository slice | JPA 쿼리, 낙관적 락 | `@DataJpaTest` + Testcontainers (MySQL) |
| Application integration | use-case 트랜잭션 · outbox · 이벤트 | Testcontainers + Kafka |
| Controller slice | 공개/내부 컨트롤러 분리 · DTO validation | `@WebMvcTest` |
| Security | 내부 엔드포인트에 외부 요청이 도달하지 못하는지 | Spring Security 테스트 |

**필수 시나리오**: 중복 이메일 가입 409 / 상태 기계의 불허 전이 400 / 삭제 요청 후 유예 기간 중 복구 가능 / 유예 만료 후 PII 익명화 / 모든 상태 변경이 `account_status_history`에 row로 기록 / **cross-tenant leak 회귀**: 동일 이메일을 두 테넌트에 등록 후 한쪽 조회 시 다른 테넌트 row가 결과에 포함되지 않음 / **provisioning scope**: WMS 시스템 토큰이 다른 `tenantId` path로 호출 시 403.

## Change Rule

1. 상태 전이 규칙 추가·변경은 [specs/features/account-lifecycle.md](../../features/) 업데이트 선행
2. 프로필 필드 추가는 [specs/services/account-service/data-model.md](./data-model.md) + 데이터 분류 등급 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R1) 재검토
3. PII 익명화 대상·보존 기간 변경은 [specs/services/account-service/retention.md](./retention.md) 업데이트 (신규 파일)
4. 내부/외부 API 경계 변경은 gateway의 라우트와 내부 컨트랙트 파일을 같은 PR에서 갱신
5. 테넌트 모델·격리 수준·provisioning API 변경은 [specs/features/multi-tenancy.md](../../features/multi-tenancy.md) 업데이트 선행. 격리 수준 승격(row→schema/DB)은 ADR 필수
