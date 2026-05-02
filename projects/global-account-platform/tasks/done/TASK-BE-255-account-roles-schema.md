# Task ID

TASK-BE-255

# Title

`account_roles` 테이블 스키마 명시 + 마이그레이션 + provisioning API 정합

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`specs/features/multi-tenancy.md` 가 "테넌트별 역할은 `(tenant_id, role_name)` 복합키로 관리" 라고 선언했으나, `specs/services/account-service/data-model.md` 에 해당 테이블 스키마가 없다. provisioning API (`PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles`) 는 `roles` 배열을 받지만 어디에 어떻게 저장되는지 specs에서 추적 불가능한 갭이다.

본 태스크는 그 갭을 닫는다.

완료 시점:

1. `account_roles` 테이블 스키마가 `data-model.md`에 정의됨.
2. Flyway 마이그레이션으로 테이블 생성 + 인덱스 + FK.
3. JPA 엔티티 + Repository + service 추가.
4. 기존 provisioning API 엔드포인트 (`PATCH /roles`) 가 본 테이블에 read/write.
5. 기본 역할셋 정책 결정: B2B tenant에 대해 admin이 role을 사전 등록하지 않은 경우의 fallback (none vs 자동 `MEMBER` 등)을 spec에 명시.

---

# Scope

## In Scope

- `data-model.md` 갱신: 신규 `account_roles` 테이블 정의 추가.
  - `account_roles` (`tenant_id`, `account_id`, `role_name`, `granted_by` (operator_id nullable for system grants), `granted_at`, PK = `(tenant_id, account_id, role_name)`, FK `(tenant_id, account_id) → accounts(tenant_id, id)` ON DELETE CASCADE)
  - 인덱스: `(tenant_id, role_name)` (role 단위 조회용)
- Flyway 마이그레이션 추가 (account-service).
- JPA 엔티티 `AccountRole` + `AccountRoleRepository` + `AccountRoleService`.
- `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles` 갱신:
  - 현재 `replace all` semantics 유지 (전체 교체)
  - 추가로 `PATCH .../roles:add` 와 `.../roles:remove` 단건 연산 추가 (TOCTOU 방지)
- `account.roles.changed` outbox 이벤트 페이로드에 `before_roles`, `after_roles` 모두 포함 (downstream consumer 의 차분 계산 편의).
- `multi-tenancy.md` § "테넌트별 역할" 갱신: 기본 역할셋 정책 명시 (권장: admin이 사전 등록한 역할만 허용; 등록되지 않은 역할 부여 시 400).

## Out of Scope

- `tenant_role_definitions` 테이블 (테넌트별 허용 role 메타) — 별도 태스크로 분리 가능. 본 태스크에서는 일단 `account_roles.role_name` 자유 문자열로 시작하고, 검증은 application 단 placeholder.
- B2B tenant의 RBAC enforcement (특정 role 만 특정 endpoint 호출 가능) — 본 태스크 범위 외. 본 태스크는 데이터 모델 + provisioning API 정합만.
- 기존 `accounts` 테이블의 `role` 단일 컬럼이 있는 경우 — 검증 후 마이그레이션 절차 추가 (현재 specs에는 단일 컬럼 없는 것으로 보임).

---

# Acceptance Criteria

- [ ] `specs/services/account-service/data-model.md` 에 `account_roles` 테이블 정의 추가 (컬럼·PK·FK·인덱스).
- [ ] Flyway 마이그레이션 적용으로 테이블 생성, FK / 인덱스 검증.
- [ ] JPA `AccountRole` 엔티티 + Repository 구현, `AccountRoleService` 가 add/remove/replaceAll 3가지 연산 제공.
- [ ] `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/roles` 가 본 테이블에 정합되게 동작 (replace all).
- [ ] `PATCH .../roles:add`, `PATCH .../roles:remove` 신규 엔드포인트 추가 + 단위·통합 테스트.
- [ ] `account.roles.changed` 이벤트 페이로드에 `before_roles`, `after_roles`, `changed_by` 포함.
- [ ] cross-tenant 회귀: tenantA에 등록된 role을 tenantB account에 부여 시도 → 400.
- [ ] `account-internal-provisioning.md` 갱신 — 신규 add/remove 엔드포인트 명세.
- [ ] `multi-tenancy.md` § "테넌트별 역할" 갱신 — 기본 정책 1줄 명시.
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:check` + `:integrationTest` PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `specs/features/multi-tenancy.md` § "테넌트별 역할"
- `specs/services/account-service/data-model.md` (확장 대상)
- `specs/contracts/http/internal/account-internal-provisioning.md` (확장 대상)
- `specs/contracts/events/account-events.md` (`account.roles.changed` payload 확장)

# Related Skills

- `.claude/skills/backend/` 데이터 모델 / 마이그레이션 / event-driven 관련

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md`
- `specs/contracts/events/account-events.md`

---

# Target Service

- `account-service`

---

# Architecture

- `domain/role/`: `AccountRole` value object, `AccountRoleService` (domain service)
- `infrastructure/persistence/role/`: `AccountRoleEntity`, `AccountRoleRepository`
- `interfaces/internal/`: `AccountRoleController` (add/remove/replaceAll endpoint)

---

# Implementation Notes

- **PK 선택**: `(tenant_id, account_id, role_name)` 복합 PK. row 자체가 fact (이 사용자에게 이 role이 부여되었다)이므로 surrogate ID 불필요.
- **`role_name` 길이/형식**: VARCHAR(64), 정규식 `^[A-Z][A-Z0-9_]*$` 권장 (예: `ADMIN`, `WAREHOUSE_OPERATOR`). 검증은 application 단.
- **Outbox 이벤트 재발행**: 기존 `account.roles.changed` 페이로드 변경 = 호환성 영향 → schema version bump 또는 새 필드 추가 (기본값 nullable). 현재 소비자 (security-service만)와 협의.
- **TOCTOU 회피**: replace-all 호출은 `Etag` / `If-Match` 또는 optimistic lock (account 의 `version` 컬럼)으로 보호. add/remove 는 멱등 (이미 존재하는 role 재추가는 no-op).
- **CASCADE delete**: account 삭제 시 role 자동 삭제. 그러나 `account.deleted` 이벤트의 페이로드에 last roles 포함 필요 — 이벤트는 삭제 직전 컴파일.

---

# Edge Cases

- **빈 roles 배열로 replaceAll**: 모든 role 삭제. 정상 동작.
- **존재하지 않는 account에 role 부여**: 404 (path validation 단계).
- **다른 tenant의 account에 role 부여**: path `tenant_id` ≠ account의 `tenant_id` → 404 (cross-tenant 차단).
- **동일 role 중복 부여**: PK 중복 → no-op (멱등). 이벤트는 발행 안 함 (변경 없음).
- **대량 roles 부여 (수백 개)**: bulk insert로 처리. 단일 트랜잭션 내. 1만 개 이상은 batch + chunked 권장.

---

# Failure Scenarios

- **마이그레이션 중 FK 위반**: 기존 `accounts` 테이블의 row 가 `(tenant_id=NULL)` 인 legacy 데이터 존재 시 FK 생성 실패. 사전 backfill 절차 필요 (TASK-BE-248 시리즈에서 이미 처리 가정).
- **Replace-all 동시성**: 두 운영자가 동시에 다른 role set으로 PUT → 마지막 승. version-based optimistic lock 권장.
- **Outbox 이벤트 schema mismatch**: 기존 consumer (security-service) 가 신규 필드 미인식 → 무시 (forward-compat). 필드 제거가 아닌 추가이므로 안전.

---

# Test Requirements

- 단위 테스트:
  - `AccountRoleService.add/remove/replaceAll` 3가지 연산 동작.
  - `role_name` 형식 검증 (정규식 위반 → IllegalArgumentException).
  - cross-tenant: tenantA scope에서 tenantB account 조회 시 404.
- 통합 테스트 (`@Tag("integration")`):
  - 마이그레이션 적용 후 schema 검증.
  - `PATCH /roles` (replace all) → DB 상태 검증.
  - `PATCH /roles:add`, `:remove` 동작.
  - account 삭제 시 role CASCADE.
  - `account.roles.changed` outbox 이벤트 페이로드 검증.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added and passing
- [ ] `data-model.md`, `account-internal-provisioning.md`, `multi-tenancy.md`, `account-events.md` 갱신
- [ ] CI green
- [ ] Ready for review
