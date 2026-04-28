# Task ID

TASK-BE-028

# Title

inventory-service — move RESERVED-bucket admin guard from controller into application layer

# Status

review

# Owner

backend

# Task Tags

- code
- api
- test

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

Close the single blocker raised in the TASK-BE-024 review (verdict 2026-04-28
FIX NEEDED).

`AdjustmentController` currently enforces the rule "an adjustment that
targets the `RESERVED` bucket requires the `INVENTORY_ADMIN` role" by
inspecting the raw JWT claim (`jwt.getClaim("role")`) inside the controller
method. Two problems:

1. **Architecture.md §Security explicitly states** authorization belongs in
   the **application layer**, not in controllers. The override hierarchy in
   CLAUDE.md ranks `architecture.md` (priority 7) above implementation code,
   so this is a genuine spec violation, not a stylistic disagreement.
2. **Raw claim parsing is fragile.** Spring Security has already decoded the
   JWT into `Authentication.getAuthorities()` by the time the controller
   runs. Re-parsing the raw claim duplicates work and breaks if the JWT
   issuer's claim shape changes (e.g., `roles` vs `role` vs `realm_access.
   roles`). The authority model is the right surface to query.

---

# Scope

## In Scope

- Remove the `jwt.getClaim("role")` inspection from `AdjustmentController`
  (the `POST /adjustments` handler).
- Move the RESERVED-bucket admin-only check into `AdjustStockService` (or a
  dedicated `AdjustStockAuthorizer` collaborator if the service grows
  unwieldy). Two acceptable patterns — pick whichever the project prefers:
  - **Pattern A — `SecurityContextHolder` lookup inside the service.** The
    service queries `SecurityContextHolder.getContext().getAuthentication()
    .getAuthorities()` and checks for `ROLE_INVENTORY_ADMIN`. Simple; the
    domain stays clean because the call sits in the application layer, not
    the domain.
  - **Pattern B — pass roles via the command.** Extend `AdjustStockCommand`
    with a `Set<String> callerRoles` field. The controller populates it
    from `Authentication.getAuthorities()` before invoking the use-case.
    The service decides on the bucket guard based on the command's roles,
    not on a side-channel context. This is the cleaner Hexagonal pattern
    (no static framework lookups in the application layer) and is the
    recommended choice unless project convention dictates otherwise.
- Throw a typed exception (`AdjustReservedBucketForbiddenException` or
  reuse an existing `AccessDeniedException`-mapped error code) so the
  `GlobalExceptionHandler` can map it to **403** with a stable error code.
  Verify the existing 403 path in `GlobalExceptionHandler` does this
  correctly; add a handler if needed.
- Update the controller test:
  - non-admin caller, RESERVED bucket → 403
  - admin caller, RESERVED bucket → 200
  - non-admin caller, AVAILABLE bucket → 200
- Update the service-level unit test to cover the same matrix at the
  command/service boundary (independent of controller wiring).

## Out of Scope

- The `INVENTORY_ADMIN` role definition or the JWT issuer setup.
- The mark-damaged / write-off-damaged endpoints (already correct: the
  ADMIN requirement on `write-off-damaged` is enforced at the route via
  `@PreAuthorize` per the controller — keep that pattern; this task only
  fixes the **bucket-conditional** check that doesn't fit `@PreAuthorize`
  cleanly).
- Any change to `TransferStockService` / `TransferController`.
- Any change to the API contract (HTTP status, error code, response body
  remain identical).

---

# Acceptance Criteria

- [ ] `AdjustmentController.POST /adjustments` no longer inspects the raw
      JWT claim or contains a `hasAdmin(jwt)` helper.
- [ ] The RESERVED-bucket admin guard runs inside `AdjustStockService` (or
      a collaborator instantiated by the service).
- [ ] Non-admin caller targeting `RESERVED` bucket → HTTP 403 with the
      stable error code (verify against the API contract; if the contract
      did not list this scenario, add it as a non-breaking documentation
      change).
- [ ] Admin caller targeting `RESERVED` bucket → HTTP 200.
- [ ] Existing tests covering AVAILABLE / DAMAGED bucket adjustments are
      unaffected.
- [ ] New service-level unit test pins the guard at the application
      boundary.
- [ ] `./gradlew :apps:inventory-service:check` is green.
- [ ] `architecture.md §Security` line "authorization in application layer,
      not controllers" is satisfied — the controller no longer enforces
      authorization decisions.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `platform/error-handling.md` — for the 403 error code mapping
- `specs/services/inventory-service/architecture.md` — §Security (line ~396)
- `rules/traits/transactional.md`

# Related Skills

- `.claude/skills/backend/security/spring-method-security`
- `.claude/skills/backend/architecture/hexagonal`

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` — the `POST /adjustments`
  error matrix; if the 403 case is not listed, add it.

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md` — Hexagonal layer rules

The fix moves an authorization decision from `adapter/in/web` to
`application/service`. No domain change.

---

# Implementation Notes

- Pattern B (command-carries-roles) is recommended. It keeps
  `AdjustStockService` framework-agnostic and avoids `SecurityContextHolder`
  inside the application layer.
- If Pattern A is chosen, document the trade-off in the service's
  class-level Javadoc (relies on Spring's request-scoped security context;
  not callable from a non-Spring context).
- `GlobalExceptionHandler` already maps `AccessDeniedException` to 403
  (per BE-018 cleanup); reusing it avoids adding a new exception type.
- Cross-check `TransferController` and `AdjustmentController.markDamaged` /
  `writeOffDamaged` for the same anti-pattern; if found, include them in
  this task. The BE-024 review only flagged the RESERVED-bucket path, so
  do not assume others exist — verify first.

---

# Edge Cases

- Caller has both `INVENTORY_WRITE` and `INVENTORY_ADMIN`: succeed (the
  ADMIN check passes regardless of bucket).
- Caller has neither role: rejected at the route via existing
  `@PreAuthorize("hasRole('INVENTORY_WRITE') or hasRole('INVENTORY_ADMIN')")`
  → 403 before reaching the service. No regression.
- Anonymous caller: rejected by `SecurityFilterChain` → 401. No regression.

---

# Failure Scenarios

- `SecurityContextHolder` is empty in a non-Spring context (Pattern A only):
  service throws `IllegalStateException` rather than silently allowing.
  Mitigation: prefer Pattern B.
- API contract does not document the 403 scenario for RESERVED bucket:
  add a row to the error matrix in this PR.

---

# Test Requirements

- Controller test (existing matrix updated; controller no longer asserts
  the bucket-role pairing — that moves to service tests).
- Service unit test covering the 4-cell matrix (admin × bucket).
- Integration test optional; the existing
  `AdjustmentTransferIntegrationTest` already covers the happy path —
  extend only if Pattern B requires command-shape changes that affect
  end-to-end serialisation.

---

# Definition of Done

- [ ] Implementation completed (Pattern A or B, documented)
- [ ] Controller no longer inspects raw JWT claims
- [ ] Tests updated/added per AC
- [ ] Tests passing
- [ ] API contract updated if the 403 case was undocumented
- [ ] PR description references TASK-BE-024 review verdict
- [ ] Ready for review
