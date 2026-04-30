# Task ID

TASK-BE-228

# Title

account-service — `tenant_id` 스키마 마이그레이션 + `domain/tenant/` 도입

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`specs/features/multi-tenancy.md`에 정의된 row-level isolation을 account-service 전반에 도입하기 위한 **첫 번째** 단계.

- account-service의 도메인 테이블에 `tenant_id` 컬럼을 추가하고 unique key를 `(tenant_id, email)` 등 복합 형태로 재구성한다.
- `Tenant` 엔터티(`domain/tenant/`)와 `TenantRepository`/`TenantJpaEntity`/`TenantJpaRepository`를 도입한다.
- `AccountRepository`의 모든 메서드 시그니처에 `TenantId tenantId`를 첫 인자로 추가하여 cross-tenant leak을 컴파일 시점부터 차단한다.
- 기존 단일 테넌트 데이터는 `tenant_id='fan-platform'` 기본값으로 백필하여 호환성을 유지한다.

본 태스크는 후속 태스크(TASK-BE-229 auth-service JWT, TASK-BE-230 gateway 헤더 전파, TASK-BE-231 internal provisioning API)의 토대이다.

# Scope

## In Scope

- **Flyway 마이그레이션 (account-service)**:
  - `tenants` 테이블 신규 생성 (`tenant_id PK`, `display_name`, `tenant_type`, `status`, `created_at`, `updated_at`)
  - `tenants` 시드 row 1건: `('fan-platform', 'Fan Platform', 'B2C_CONSUMER', 'ACTIVE')`
  - `accounts`, `profiles`, `account_status_history`, `outbox_events` 테이블에 `tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform'` 컬럼 추가
  - 기존 row 백필 후 DEFAULT 제거 (NOT NULL은 유지)
  - `accounts` 기존 unique index `(email)` 제거 → `(tenant_id, email)` 복합 unique index 재생성
  - 필요 시 외래키도 `(tenant_id, account_id)` 복합 형태 검토
- **도메인 클래스 (`domain/tenant/`)**:
  - `Tenant.java` (aggregate root, 불변 식별자)
  - `TenantId.java` (값 객체, 정규식 `^[a-z][a-z0-9-]{1,31}$` 검증)
  - `TenantType.java` (enum: `B2C_CONSUMER`, `B2B_ENTERPRISE`)
  - `TenantStatus.java` (enum: `ACTIVE`, `SUSPENDED`)
  - `TenantRepository.java` (포트 인터페이스, `findById`/`existsActive` 등)
- **인프라 어댑터**:
  - `TenantJpaEntity.java`
  - `TenantJpaRepository.java` (Spring Data JPA)
  - `TenantRepository` 구현체(또는 `TenantJpaRepository`가 직접 구현)
- **AccountRepository 시그니처 변경**:
  - 모든 조회/저장 메서드 첫 인자에 `TenantId tenantId` 추가 (`findByEmail(TenantId, Email)`, `findById(TenantId, AccountId)`, `existsByEmail(TenantId, Email)` 등)
  - `tenant_id` 없이 PK만으로 조회 가능한 메서드는 **제거 또는 internal-only**로 표시
- **`AccountJpaEntity`** 등 도메인 JPA 엔터티에 `tenant_id` 컬럼 매핑 추가 (NOT NULL)
- **application/use-case 호출부 수정**: 기존 `SignupUseCase`, `UpdateProfileUseCase`, `ChangeAccountStatusUseCase`, `DeleteAccountUseCase` 등 모든 use-case가 현재 컨텍스트의 `tenant_id`(임시로 `fan-platform` 상수 또는 향후 SecurityContext에서 주입)를 repository에 전달하도록 수정. 본 태스크에서는 **고정값 `fan-platform`** 으로 통과시켜도 무방 (auth-service의 JWT claim 도입은 TASK-BE-229).
- **outbox 이벤트 페이로드**: `account.created`, `account.status.changed`, `account.locked`, `account.unlocked`, `account.deleted` 페이로드에 `tenant_id` 필드 추가 (값은 현재 `fan-platform` 고정)
- **테스트**:
  - 기존 통합 테스트가 `tenant_id='fan-platform'` 고정값으로 통과
  - `AccountJpaRepositoryTest`에 cross-tenant leak 회귀 테스트 추가 (동일 이메일을 두 `tenant_id`에 저장 후 한쪽 조회 시 다른 쪽 row 미포함)
  - `TenantJpaRepositoryTest` 신규 (CRUD 및 정규식 검증)

## Out of Scope

- auth-service의 `tenant_id` 컬럼/JWT claim 추가 (→ TASK-BE-229)
- gateway-service의 헤더 전파/검증 (→ TASK-BE-230)
- internal `/internal/tenants/{tenantId}/accounts` provisioning 컨트롤러 (→ TASK-BE-231)
- admin-service / security-service / community-service / membership-service 의 `tenant_id` 도입 (별도 태스크)
- WMS 등 추가 테넌트 시드 row 삽입 (운영자 명령으로 별도 등록)
- SUPER_ADMIN platform-scope role의 cross-tenant 동작 (admin-service 후속 스펙)
- `TenantRoleCatalog` 등 역할 카탈로그 도메인 (별도 태스크)

# Acceptance Criteria

- [ ] Flyway migration이 dev/test 환경에서 무중단으로 적용되며 기존 row가 `tenant_id='fan-platform'` 으로 백필된다
- [ ] `tenants` 테이블에 `('fan-platform', 'Fan Platform', 'B2C_CONSUMER', 'ACTIVE')` row가 시드된다
- [ ] `accounts.email`은 더 이상 단독 unique가 아니며 `(tenant_id, email)` 이 unique이다
- [ ] `domain/tenant/` 디렉터리에 `Tenant`, `TenantId`, `TenantType`, `TenantStatus`, `TenantRepository`가 존재하고 빌드된다
- [ ] `TenantId`는 정규식 `^[a-z][a-z0-9-]{1,31}$` 위반 시 `IllegalArgumentException`을 발생시킨다
- [ ] `AccountRepository`의 모든 public 메서드는 `TenantId`를 첫 인자로 가진다 (`findByEmail`, `findById`, `existsByEmail`, `save` 등)
- [ ] `tenant_id` 없는 조회 경로(예: `findById(AccountId)`)는 제거되거나 deprecated/internal-only로 표시된다
- [ ] 기존 통합 테스트(`SignupIntegrationTest` 등)는 코드 수정 후 `tenant_id='fan-platform'` 고정으로 모두 통과한다
- [ ] cross-tenant leak 회귀 테스트가 추가되어, 동일 이메일을 두 `tenant_id`에 저장한 뒤 한쪽 `tenant_id`로 조회 시 다른 테넌트 row가 결과에 포함되지 않음을 검증한다
- [ ] outbox 이벤트 페이로드에 `tenant_id` 필드가 직렬화된다
- [ ] `application/` 레이어에서 `tenant_id` 파라미터 누락 시 컴파일 에러가 발생한다 (signature enforcement)

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/features/multi-tenancy.md`
- `specs/services/account-service/architecture.md`
- `rules/traits/multi-tenant.md` (declared in `PROJECT.md`)
- `rules/traits/transactional.md` (마이그레이션·outbox 일관성)
- `rules/traits/regulated.md` (PII 컬럼 보존·삭제 제약)
- `rules/traits/audit-heavy.md` (이벤트 페이로드 변경)
- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/INDEX.md` (해당 도메인/서비스 스킬 매칭 결과 적용)
- 도메인/특성 스킬 번들이 `multi-tenant`/`saas` 활성화 결과로 매칭되면 그 가이드를 우선 적용

# Related Contracts

- `specs/contracts/events/account-events.md` — 페이로드에 `tenant_id` 필드 추가 (스키마 버전 +1)
- `specs/contracts/http/account-api.md` — signup/me 응답에 `tenant_id` 노출 검토 (해당 PR에서 함께 갱신)

# Target Service

- account-service

# Architecture

Follow:

- `specs/services/account-service/architecture.md`

상위 원칙:

- Layered (`presentation / application / domain / infrastructure`)
- `domain/tenant/`는 다른 도메인(`account`, `profile`, `status`)과 동일 레이어. 의존 방향은 `application → domain` 만 허용
- `infrastructure/persistence`는 도메인 포트(`TenantRepository`, `AccountRepository`)를 구현
- `tenant_id` 없이 도메인 쿼리를 노출하는 메서드 신설 금지 (`Forbidden Dependencies` 참조)

# Implementation Notes

- **순서 의존성**: 본 태스크는 멀티테넌시 시리즈의 **첫 번째**. TASK-BE-229(auth) → TASK-BE-230(gateway) → TASK-BE-231(provisioning) 순서로 진행되며 본 태스크가 모든 후속의 전제다.
- **마이그레이션 안전성**:
  1. `tenants` 테이블 + 시드 row 먼저 삽입
  2. 도메인 테이블에 `tenant_id` 컬럼 추가 (DEFAULT `'fan-platform'`)
  3. 백필 (`UPDATE ... SET tenant_id='fan-platform' WHERE tenant_id IS NULL`은 DEFAULT가 처리)
  4. DEFAULT 제거 + NOT NULL 유지
  5. 기존 unique index DROP → 복합 unique index ADD
  6. 외래키가 있다면 `(tenant_id, target_id)` 복합으로 재구성 검토 (운영 영향 시 ADR)
- 본 태스크에서는 호출부에 **고정 상수 `TenantId.FAN_PLATFORM`** 또는 동등한 placeholder를 사용해도 무방. 실제 동적 컨텍스트 주입은 TASK-BE-229/230에서 도입.
- `Tenant` 엔터티는 본 태스크에서 read 위주. `Tenant` 생성/SUSPEND는 admin-service의 별도 태스크에서 처리.
- `community-service`/`membership-service`는 frozen이지만 account-service의 응답 DTO에 `tenant_id`가 추가되면 그쪽 클라이언트 코드가 깨지지 않는지 검증 (역호환성 필요 시 응답 필드는 추가만, 제거 금지).
- 기존 코드의 `findByEmail(Email)` 등 단일 인자 조회 메서드는 일괄 grep 후 모두 시그니처 변경. 컴파일 실패가 발생하는 호출부를 모두 수정해야 본 태스크 완료.

# Edge Cases

- `tenant_id` 컬럼 추가 직후 NULL row 잔존 가능성 → DEFAULT 값으로 같은 마이그레이션 내에서 백필 완료 후 NOT NULL 보장
- 동일 이메일이 단일 테넌트(`fan-platform`)에 두 번 등록 시도 → 기존 동작과 동일하게 409
- 동일 이메일을 향후 `wms` 테넌트에 추가 등록 시도 → `(tenant_id, email)` 복합 unique 덕에 허용 (테스트로 검증)
- `Email` 값 객체 비교에서 `equals` 시 `tenant_id` 가 빠져 있어 도메인 비교가 잘못될 가능성 → repository 시그니처에서 항상 두 인자 강제로 회피
- outbox 이벤트의 기존 컨슈머(security-service 등)가 `tenant_id` 필드 추가에 깨지지 않는지 검증 (additive change → 안전)
- `TenantId` 정규식이 `fan-platform`(11자, 하이픈 포함)을 허용하는지 회귀 테스트로 검증

# Failure Scenarios

- Flyway 마이그레이션 도중 백필 실패: 트랜잭션 롤백 후 운영자 개입. DEFAULT 절이 모든 기존 row를 채우므로 정상 환경에선 발생하지 않음
- 새로운 unique index 생성 중 중복 위반: 단일 테넌트 환경이므로 발생 불가. 발생 시 데이터 정합성 점검(중복 이메일 row 존재) 후 수동 정리
- `AccountRepository` 시그니처 변경으로 호출부 수십 곳에서 컴파일 실패: 일괄 grep + 수정으로 해결. 누락된 호출부가 있으면 컴파일 단계에서 즉시 발견 (의도된 fail-closed)
- `TenantId` 정규식이 너무 엄격하여 기존 `fan-platform` 시드가 거부되는 경우: 정규식 테스트에 `fan-platform` 케이스를 명시적으로 포함하여 사전 차단
- outbox 이벤트 직렬화에 `tenant_id` 필드가 누락되어 다운스트림 컨슈머가 `null`을 받는 경우: producer side 단위 테스트에서 페이로드 JSON 검증

# Test Requirements

- **Unit**:
  - `TenantId` 정규식 (허용/거부 케이스 다수)
  - `Tenant` 엔터티 불변성
- **Repository slice (`@DataJpaTest` + Testcontainers MySQL)**:
  - `TenantJpaRepositoryTest`: CRUD + 시드 row 존재 검증
  - `AccountJpaRepositoryTest`: cross-tenant leak 회귀 (동일 이메일을 `fan-platform`/`wms` 양쪽에 저장 후 한쪽으로 조회, 결과 격리)
- **Application integration (Testcontainers)**:
  - 기존 `SignupUseCase` 통합 테스트가 `tenant_id='fan-platform'` 고정으로 통과
  - outbox `account.created` 페이로드에 `tenant_id='fan-platform'` 포함
- **Migration test**:
  - 기존 데이터 백필 후 새 unique index가 정상 적용되는지 (Testcontainers에서 마이그레이션 적용 검증)
- 새로 추가/수정한 테스트는 `@EnabledIf` 없이 항상 실행 (TASK-BE-201/202 정책)

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (cross-tenant leak 회귀 포함)
- [ ] Tests passing (Testcontainers 포함 전체 그린)
- [ ] Contracts updated (`account-events.md` 페이로드, 필요 시 `account-api.md`)
- [ ] Specs updated first if required (현재 specs는 본 태스크가 따르는 형태로 이미 갱신됨 — 추가 갱신 불요. 운영 중 새로운 결정이 필요하면 specs 선행)
- [ ] Ready for review
