# Task ID

TASK-BE-265

# Title

Follow-up to TASK-BE-255: role_name length inconsistency + addIfAbsent concurrent-duplicate 500 + operatorId DB bound

# Status

ready

# Owner

backend

# Task Tags

- code
- api

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

TASK-BE-255에서 발견된 세 가지 결함을 보정한다:

1. **`AssignRolesRequest` / `ProvisionAccountRequest`의 role_name max 50 → 64 불일치**
   V0013 DDL이 `role_name VARCHAR(64)`, `AccountRoleName` validator가 ≤ 64로 정의되었으나, 기존 두 Request DTO(`AssignRolesRequest`, `ProvisionAccountRequest`)는 `@Size(max = 50)`을 그대로 유지한다. 51~64자 role 이름을 `/roles:add`로 삽입한 뒤 `/roles` (replace-all)로 동일 이름을 다시 지정하면 Bean Validation 400이 발생하는 기능 회귀가 존재한다. 또한 POST 엔드포인트 contract spec(`account-internal-provisioning.md`)은 `each role ≤ 50 chars`라고 기술하고 있으나 PATCH roles는 `≤ 64 chars`라고 기술하여 spec 자체가 불일치한다.

2. **`addIfAbsent` 동시 중복 삽입 시 500 Internal Server Error**
   `AccountRoleRepositoryAdapter.addIfAbsent`는 `findBy... + save` 형태의 check-then-act 패턴으로 구현되어 있다. MySQL READ COMMITTED isolation에서 두 concurrent 요청이 동일한 `(tenant_id, account_id, role_name)`에 대해 각각 `findBy`를 통과한 뒤 동시에 `save`를 시도하면, 두 번째 삽입은 PK 제약 위반(중복 PK)으로 `DataIntegrityViolationException`을 던진다. 이 예외를 처리하는 핸들러가 없어 `handleGeneral → 500`으로 응답한다. idempotent 200 계약이 깨진다.

3. **`operatorId` DTO에 최대 길이 제약 없음 — DB 컬럼 `granted_by VARCHAR(36)` 초과 시 DB 오류**
   `SingleRoleMutationRequest.operatorId`에 `@Size` 제약이 없어, 36자 초과 operatorId를 전달하면 MySQL이 Data truncation 오류를 던진다. `AssignRolesRequest.operatorId`, `ProvisionAccountRequest.operatorId`도 동일 문제를 공유한다.

원본 태스크: `tasks/done/TASK-BE-255-account-roles-schema.md` (review → done).

---

# Scope

## In Scope

- `AssignRolesRequest`: `@Size(max = 50)` → `@Size(max = 64)` 수정 (role_name)
- `ProvisionAccountRequest`: 동일 수정
- `account-internal-provisioning.md` POST 엔드포인트 role_name 제약을 `≤ 64 chars`로 통일 (spec 불일치 해소)
- `AccountRoleRepositoryAdapter.addIfAbsent`: 동시 삽입 시 `DataIntegrityViolationException`을 catch → `false` 반환 (이미 존재 → no-op)하거나, 직접 INSERT IGNORE / ON DUPLICATE KEY UPDATE 쿼리로 대체 — 결정 방식은 구현 PR에 명시
- `GlobalExceptionHandler` 또는 `AccountRoleRepositoryAdapter`에 `DataIntegrityViolationException` 처리 추가
- `SingleRoleMutationRequest.operatorId`: `@Size(max = 36)` 추가 (DB 컬럼 bound 동기화)
- `AssignRolesRequest.operatorId`, `ProvisionAccountRequest.operatorId`: 동일

## Out of Scope

- TOCTOU 해결을 위한 SELECT FOR UPDATE 또는 낙관적 락 도입 — replace-all은 별도 Account 엔티티의 버전 컬럼 사용 검토가 필요하나 TASK-BE-255 scope 외이므로 별도 ADR 필요
- `tenant_role_definitions` 테이블(role 카탈로그) — 이후 별도 태스크
- 기존 통합 테스트의 operatorId 길이 픽스처 변경이 필요한 경우 포함 (30자 이하 픽스처이면 영향 없음)

---

# Acceptance Criteria

- [ ] `AssignRolesRequest`: role_name `@Size(max = 64)` 적용 확인
- [ ] `ProvisionAccountRequest`: role_name `@Size(max = 64)` 적용 확인
- [ ] `account-internal-provisioning.md` POST 엔드포인트 role_name 제약이 `≤ 64 chars`로 통일
- [ ] `/roles:add`로 64자 role 이름 추가 → `/roles` replace-all에서 동일 이름 전달 → 400 없이 성공
- [ ] `addIfAbsent` 동시 중복 삽입 시 500이 아닌 no-op (200) 반환 — 동시성 단위 테스트 또는 retry 시뮬레이션 테스트 추가
- [ ] `SingleRoleMutationRequest.operatorId`, `AssignRolesRequest.operatorId`, `ProvisionAccountRequest.operatorId`: 37자 이상 입력 시 400 `VALIDATION_ERROR`
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:check` PASS

---

# Related Specs

- `specs/contracts/http/internal/account-internal-provisioning.md` (POST role_name 제약 통일 대상)
- `specs/services/account-service/data-model.md` § account_roles (role_name VARCHAR(64), granted_by VARCHAR(36))
- `rules/traits/transactional.md` T4 (상태 변경 원자성 — 동시성 안전)
- 원본 태스크: `tasks/done/TASK-BE-255-account-roles-schema.md`

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md`
  - POST `roles` field 제약: `≤ 50` → `≤ 64` 수정
  - `operatorId` field 제약: `≤ 36 chars` 추가 (POST, PATCH roles, PATCH :add, PATCH :remove 모두)

---

# Edge Cases

- **64자 role 이름 + replace-all 경로**: 통합 테스트로 end-to-end 검증
- **37자 operatorId**: 400 VALIDATION_ERROR (before: DB 에러)
- **동시 `/roles:add` 두 요청 동일 role**: 한 건 성공(inserted), 나머지 한 건 no-op (200, changed=false)
- **빈 operatorId 문자열**: 기존 동작 유지 (null 처리와 동일하게 tenantId fallback)
- **role_name 51자 (경계값)**: 모든 세 엔드포인트(POST, PATCH roles, PATCH :add)에서 동일하게 통과

---

# Failure Scenarios

- **addIfAbsent catch 범위 과도**: `DataIntegrityViolationException`을 catch할 때 다른 컬럼(예: FK 위반 — account 없음)의 제약 위반과 구별해야 한다. PK 제약 위반만 no-op으로 처리하고 FK 위반은 재-throw. MySQL 에러 코드 1062 (ER_DUP_ENTRY)로 구분 가능.
- **INSERT IGNORE 방식 사용 시 granted_at 정보 손실**: INSERT IGNORE는 두 번째 삽입을 조용히 무시하므로 첫 번째 `granted_at` 값이 보존된다. 멱등 no-op의 의도와 일치함.

---

# Test Requirements

- 단위 테스트:
  - `AssignRolesRequest`: role_name 65자 → 400, 64자 → 통과
  - `SingleRoleMutationRequest`: operatorId 37자 → 400, 36자 → 통과
  - `AddAccountRoleUseCaseTest`: `addIfAbsent`가 `DataIntegrityViolationException` 던질 때 no-op 반환 검증 (mock)
- 슬라이스/통합 테스트:
  - `AccountRoleControllerTest`: operatorId 37자 → 400 VALIDATION_ERROR
  - 64자 role 이름 end-to-end: `/roles:add` → `/roles` replace-all 성공

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + integration tests added
- [ ] Tests passing (`:account-service:check` green)
- [ ] `account-internal-provisioning.md` 계약 role_name + operatorId 제약 통일
- [ ] Ready for review
