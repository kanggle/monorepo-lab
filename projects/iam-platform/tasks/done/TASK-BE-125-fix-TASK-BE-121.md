# Task ID

TASK-BE-125

# Title

Fix issues found in TASK-BE-121 — architecture spec naming conflict for OperatorRoleResolver and test helper duplication

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

Fix two issues found during review of TASK-BE-121 (OperatorRoleResolver extraction):

1. **Architecture spec naming conflict**: `specs/services/admin-service/architecture.md` (line 64) declares `OperatorRoleResolver` in `infrastructure/security/` (described as role-based endpoint access control). TASK-BE-121 introduced a different class with the same name in `application/` (a use-case role-resolution helper). The architecture spec must be updated to disambiguate the two by either:
   - Renaming the spec's `infrastructure/security/OperatorRoleResolver` entry to reflect its endpoint-RBAC purpose (e.g., `OperatorEndpointAccessResolver`), or
   - Adding a note clarifying that the `application/OperatorRoleResolver` is the TASK-BE-121 extraction and the `infrastructure/security/` entry is a placeholder for a future endpoint-RBAC class.

2. **Test helper duplication**: `newResolver()`, `setField()`, `findField()`, `operator()`, and `role()` reflective utility methods are duplicated verbatim in both `CreateOperatorUseCaseTest` and `PatchOperatorRoleUseCaseTest`. Extract these into a shared package-private test base class or utility within the same test package.

---

# Scope

## In Scope

- Update `specs/services/admin-service/architecture.md` internal structure diagram to clarify the `infrastructure/security/OperatorRoleResolver` entry vs the new `application/OperatorRoleResolver` (TASK-BE-121). The update must resolve the naming ambiguity — either rename the planned `infrastructure/security/` class or annotate both entries with their distinct responsibilities.
- Extract duplicated test helpers (`newResolver`, `setField`, `findField`, `operator`, `role`) from `CreateOperatorUseCaseTest` and `PatchOperatorRoleUseCaseTest` into a shared abstract or utility class within the same test package (e.g., `OperatorUseCaseTestSupport`).

## Out of Scope

- Moving `OperatorRoleResolver` out of `application/` — placement was explicitly authorized by TASK-BE-121 implementation notes and is compliant with the allowed `application → infrastructure/persistence` dependency in the architecture spec.
- Changing production behavior of any use case.
- API contract changes.

---

# Acceptance Criteria

- [ ] `specs/services/admin-service/architecture.md` no longer has an ambiguous duplicate `OperatorRoleResolver` name — both the `application/` helper and the (planned) `infrastructure/security/` endpoint-RBAC class are clearly distinguishable in the spec.
- [ ] `newResolver()`, `setField()`, `findField()`, `operator()`, and `role()` helper methods exist in exactly one place for the two operator use case test classes.
- [ ] All existing tests continue to pass with no behavioral change.

---

# Related Specs

- `specs/services/admin-service/architecture.md` (Internal Structure Rule — the primary artifact to update)
- `platform/coding-rules.md` (No duplicate code)
- `platform/testing-strategy.md` (@DisplayName Korean descriptions — no change needed)

# Related Contracts

없음 — 계약 변경 없음

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

- The simplest spec fix is to rename the planned `infrastructure/security/` class in the diagram to `OperatorEndpointAccessResolver` (or similar) with a comment indicating it is not yet implemented, while adding `OperatorRoleResolver` to the `application/` section with a note "(TASK-BE-121 — use-case role-name→entity resolver)".
- The shared test helper class should be in `src/test/java/com/example/admin/application/` with package-private access.

---

# Edge Cases

- Spec update must not alter the actual package or behavior of `application/OperatorRoleResolver` — this is a documentation-only fix for the architecture spec.
- Test helper extraction must use the identical reflective logic already present — no behavioral change allowed.

---

# Failure Scenarios

- Spec update introduces incorrect dependency direction in diagram → review checklist fails in next review cycle.
- Test helper extraction changes reflective behavior (e.g., wrong constructor signature) → unit tests fail immediately.

---

# Test Requirements

- All existing unit tests for the 4 operator use case classes must pass after refactoring test helpers.
- No new tests required — this is a documentation + test-structure refactoring task only.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests pass
- [ ] Contracts updated if needed
- [ ] Specs updated if required
- [ ] Ready for review
