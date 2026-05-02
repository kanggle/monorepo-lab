# Task ID

TASK-BE-267

# Title

Follow-up to TASK-BE-265: ProvisionStatusChangeRequest.operatorId missing @Size(max=36) + ProvisionAccountRequest validation test gap

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

TASK-BE-265에서 발견된 두 가지 결함을 보정한다:

1. **`ProvisionStatusChangeRequest.operatorId` — `@Size(max=36)` 누락 (spec ↔ 구현 불일치)**
   TASK-BE-265 commit(d0dd5da1)은 `account-internal-provisioning.md`의 PATCH `/status` endpoint `operatorId` 제약을 `≤ 36 chars`로 업데이트했다. 그러나 대응하는 DTO `ProvisionStatusChangeRequest`에 `@Size(max=36)` annotation이 추가되지 않았다. 결과적으로 37자 이상의 `operatorId`는 Bean Validation을 통과하여 `account_status_history.actor_id VARCHAR(36)` 컬럼에 도달하면 DB 절단(data truncation) 오류 또는 묵시적 절단으로 500이 발생한다. spec(priority 6)이 구현(priority 14)보다 우선하므로 이는 Critical.

2. **`ProvisionAccountRequest` operatorId 경계값 단위 테스트 누락**
   Task-BE-265 Test Requirements에 `AssignRolesRequest: role_name 65자 → 400`와 별도로 `ProvisionAccountRequest`의 operatorId 37자 → 위반 검증이 암묵적으로 포함되어야 했다(operatorId `@Size(max=36)`은 세 DTO 모두에 추가됨). `AssignRolesRequestValidationTest`는 `AssignRolesRequest.operatorId`만 검증하고 `ProvisionAccountRequest.operatorId`의 독립 경계값 테스트가 없다.

원본 태스크: `tasks/done/TASK-BE-265-fix-TASK-BE-255.md`.

---

# Scope

## In Scope

- `ProvisionStatusChangeRequest.operatorId`: `@Size(max = 36, message = "operatorId must be at most 36 characters")` 추가
- `ProvisionStatusChangeRequestValidationTest` 신규 추가 (operatorId 37자 → 위반, 36자 → 통과, null → 통과)
- `ProvisionAccountRequestValidationTest` 신규 추가 (operatorId 37자 → 위반, 36자 → 통과, null → 통과; role_name 64자 → 통과, 65자 → 위반)
- `AccountRoleControllerTest` 또는 슬라이스 테스트에 PATCH `/status` operatorId 37자 → 400 슬라이스 케이스 추가

## Out of Scope

- `ProvisionStatusChangeRequest.reason` 필드 validation 강화 — 별도 태스크
- `account_status_history.actor_id` 컬럼 길이 변경 — DDL 변경 없음
- `GlobalExceptionHandler` 수정 — TASK-BE-250 user WIP 보호 유지

---

# Acceptance Criteria

- [ ] `ProvisionStatusChangeRequest.operatorId`: `@Size(max = 36)` 적용 확인
- [ ] `ProvisionStatusChangeRequest`: 37자 이상 `operatorId` 전달 시 400 `VALIDATION_ERROR` 반환
- [ ] `ProvisionAccountRequestValidationTest`: operatorId 37자 → 위반, 36자 → 통과, null → 통과
- [ ] `ProvisionStatusChangeRequestValidationTest`: operatorId 37자 → 위반, 36자 → 통과, null → 통과
- [ ] `./gradlew :projects:global-account-platform:apps:account-service:check` PASS

---

# Related Specs

- `specs/contracts/http/internal/account-internal-provisioning.md` — PATCH `/status` endpoint operatorId `≤ 36 chars` (이미 spec 업데이트 완료, 구현만 누락)
- `specs/services/account-service/data-model.md` § account_status_history (`actor_id VARCHAR(36)`)

---

# Related Contracts

- `specs/contracts/http/internal/account-internal-provisioning.md`
  - PATCH `/status` endpoint: `operatorId ≤ 36 chars` (spec 이미 반영됨, 구현 보정만 필요)

---

# Target Service

- `account-service`

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md`

---

# Implementation Notes

- `ProvisionStatusChangeRequest`에 `@Size(max = 36)` 추가 — `SingleRoleMutationRequest` / `AssignRolesRequest` / `ProvisionAccountRequest`의 패턴을 그대로 따른다.
- annotation 메시지: `"operatorId must be at most 36 characters"` (일관성 유지).
- Bean Validation 단위 테스트는 `Validation.buildDefaultValidatorFactory()` 패턴으로 작성 (기존 `AssignRolesRequestValidationTest` 참조).
- `GlobalExceptionHandler`는 TASK-BE-250 WIP 보호를 위해 수정하지 않는다.

---

# Edge Cases

- **36자 operatorId (경계값)**: PATCH `/status`, `ProvisionAccountRequest` 모두 통과해야 함
- **null operatorId**: 선택값이므로 통과 (기존 동작 유지)
- **빈 문자열 operatorId**: `@Size`는 빈 문자열에 위반을 발생시키지 않음 — 기존 동작 유지

---

# Failure Scenarios

- **`@Size` 누락 시 37자 operatorId 전달**: DB `actor_id VARCHAR(36)` 컬럼에서 절단 오류 또는 묵시적 절단 → 500 Internal Server Error (수정 전 현 상태)
- **잘못된 annotation 메시지**: 메시지가 기존 3개 DTO와 다르면 일관성 경고 — 동일 메시지 포맷 사용

---

# Test Requirements

- 단위 테스트:
  - `ProvisionStatusChangeRequestValidationTest`: operatorId 37자 → 위반, 36자 → 통과, null → 통과
  - `ProvisionAccountRequestValidationTest`: operatorId 37자 → 위반, 36자 → 통과, null → 통과; role_name 64자 → 통과, 65자 → 위반
- 슬라이스 테스트:
  - `AccountRoleControllerTest` 또는 `AccountControllerTest`: PATCH `/status` operatorId 37자 → 400 `VALIDATION_ERROR`

---

# Definition of Done

- [ ] Implementation completed
- [ ] Unit + slice tests added
- [ ] Tests passing (`:account-service:check` green)
- [ ] Ready for review
