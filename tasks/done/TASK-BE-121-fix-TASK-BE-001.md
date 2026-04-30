# Task ID

TASK-BE-121

# Title

Fix issues found in TASK-BE-001 — role-resolver duplication and missing @DisplayName in operator use case tests

# Status

ready

# Owner

backend

# Task Tags

- code

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

Fix two issues found during review of TASK-BE-001 (OperatorAdminUseCase CQRS split):

1. **Code duplication**: The private helpers `resolveRoles`, `resolveActorInternalId`, and `normalizeReason` are now duplicated verbatim in both `CreateOperatorUseCase` and `PatchOperatorRoleUseCase`. Extract these into a shared internal utility to eliminate the duplication.

2. **Missing `@DisplayName`**: All 4 new test classes (`CreateOperatorUseCaseTest`, `PatchOperatorRoleUseCaseTest`, `PatchOperatorStatusUseCaseTest`, `OperatorQueryServiceTest`) omit the `@DisplayName` Korean description annotations required by `platform/testing-strategy.md`.

---

# Scope

## In Scope

- Extract `resolveRoles`, `resolveActorInternalId`, and `normalizeReason` duplication between `CreateOperatorUseCase` and `PatchOperatorRoleUseCase` into a shared helper (e.g., a package-private utility class in `application/` or a common static helper used by both)
- Add `@DisplayName` Korean annotations to all test methods in:
  - `CreateOperatorUseCaseTest`
  - `PatchOperatorRoleUseCaseTest`
  - `PatchOperatorStatusUseCaseTest`
  - `OperatorQueryServiceTest`

## Out of Scope

- API contract changes
- Architecture changes
- Changes outside the 4 operator use case classes and their tests

---

# Acceptance Criteria

- [ ] `resolveRoles`, `resolveActorInternalId`, `normalizeReason` are not duplicated across `CreateOperatorUseCase` and `PatchOperatorRoleUseCase`
- [ ] Each test method in all 4 new test classes has a `@DisplayName` with Korean description
- [ ] All existing tests continue to pass
- [ ] No behavioral change — refactoring only

---

# Related Specs

- `specs/services/admin-service/architecture.md`
- `platform/coding-rules.md` (No duplicate code)
- `platform/testing-strategy.md` (@DisplayName Korean descriptions)

# Related Contracts

없음 — API 계약 변경 없음

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `specs/services/admin-service/architecture.md`
- Thin Layered (Command Gateway): presentation / application / infrastructure

---

# Implementation Notes

- The shared helper should stay within the `application/` layer (e.g., `OperatorRoleResolver` or a static utility). Do not move to `infrastructure/` or `libs/`.
- `normalizeReason` can also be extracted to the same helper if appropriate, but a single static method on either use case is acceptable as long as there is no duplication.
- Keep the shared utility package-private unless there is a reason to expose it more broadly.

---

# Edge Cases

- Extracting `resolveRoles` must not change behavior: blank/null role names are still skipped, unknown roles still throw `RoleNotFoundException`.
- `resolveActorInternalId` with null actor or null operatorId must still return null without throwing.

---

# Failure Scenarios

- Refactoring introduces behavioral change → unit tests catch this immediately.
- Missing `@DisplayName` on any new test method → review checklist fails on the next review cycle.

---

# Test Requirements

- All existing unit tests for the 4 classes must continue to pass with no behavioral change.
- New `@DisplayName` values must be in Korean and describe the scenario being tested.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests pass
- [ ] Contracts updated if needed
- [ ] Specs updated if required
- [ ] Ready for review
