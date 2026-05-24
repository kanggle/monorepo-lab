# Task ID

TASK-BE-314

# Title

admin-service `AdminActionAuditor` 681-line god-class split

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Goal

`AdminActionAuditor.java` (currently **681 lines, 41 method declarations**) is a single class carrying four distinct responsibilities. Refactor it into 3–4 single-responsibility classes within `application/` while preserving every externally observable behavior — audit row shape, outbox event payload, DB-trigger invariant, REQUIRES_NEW transactional boundary, meter counter increments, meta-audit fallback path.

After this task:

- No class in the audit subsystem exceeds ~250 LOC.
- Each new class has one clear responsibility (write / records / permission registry / facade — or whichever split the implementer chooses after re-reading the source).
- `specs/services/admin-service/architecture.md` § application/ (L76 / L109 / L127–132) reflects the new layout.
- All existing audit-related unit + integration tests pass without their test logic changing.

---

# Scope

## In Scope

- `apps/admin-service/src/main/java/com/example/admin/application/AdminActionAuditor.java` (the split target).
- Any nested types declared inside `AdminActionAuditor` (e.g. `OperatorResolved`, `StartRecord`, `CompletionRecord`).
- The static `ACTION_TARGET_TYPE` map + the `PERMISSION_*` / `REASON_*` synthetic constants.
- Call sites within admin-service that inject or call the auditor (controllers + use-cases) — *only* to update DI fields if the facade is removed; method signatures on the new classes should preserve the existing call shape where possible.
- `specs/services/admin-service/architecture.md` updates to reflect the new class layout.
- Unit + integration tests for the audit subsystem (renaming the existing `AdminActionAuditorTest` cohort to mirror the new classes, or splitting the cohort, with no test-logic changes).

## Out of Scope

- DB schema or Flyway migrations for `admin_actions` table.
- DB trigger `trg_admin_actions_finalize_only` (V0010).
- Outbox event contract for `admin.action.*` events.
- Other admin-service classes (controllers / use-cases) beyond their DI fields.
- `OperatorEndpointAccessResolver` / `PermissionEvaluator` work (separate concern).
- Cross-service work (auth-service, account-service, security-service).

---

# Acceptance Criteria

- [ ] After the split, no class in `apps/admin-service/src/main/java/com/example/admin/application/` related to audit exceeds **250 LOC**.
- [ ] Existing public method signatures called from outside `application/` remain callable with the same arguments — either through a thin residual facade or through directly injected new classes (implementer chooses).
- [ ] All existing `AdminActionAuditorTest` cases pass with the same input/output expectations; tests may be reorganized into per-class cohorts, but assertion logic must not change.
- [ ] DB trigger invariant (only IN_PROGRESS rows are UPDATE-able; only `outcome`, `downstream_detail`, `completed_at`, `operator_id`, `permission_used` columns are mutable) continues to hold — verified by an integration test that attempts an illegal UPDATE and asserts the trigger fires.
- [ ] REQUIRES_NEW propagation is preserved on every write path (recordStart, recordCompletion, recordDenied, meta-audit fallback) — verified by an integration test that the outer transaction rollback does NOT roll back the audit row.
- [ ] `MeterRegistry` counter increments (success / failure / denied / meta-audit-fallback) occur at the same call sites as before.
- [ ] Outbox event publication (`AdminEventPublisher.publish*`) occurs at the same call sites and with the same payload as before.
- [ ] `specs/services/admin-service/architecture.md` is updated in the same PR: L76 (single-line description), L109 (subsystem summary), and L127–132 (application/ subsection) reference the new classes by name.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/saas.md` and `rules/traits/{transactional,regulated,audit-heavy,integration-heavy,multi-tenant}.md` matching the declared classification.

- `platform/refactoring-policy.md` — Allowed categories (Extract Class is the primary one here), Mandatory rules ("No behavior change", "Architecture direction only").
- `platform/coding-rules.md` — Java 21 records, constructor injection only, no field injection, no swallowed exceptions, no commented-out code.
- `platform/naming-conventions.md` — class naming (PascalCase + role suffix), package layers.
- `platform/service-types/rest-api.md` — admin-service is a `rest-api` Service Type (command-gateway flavor).
- `projects/global-account-platform/specs/services/admin-service/architecture.md` — declared Layered Architecture, no domain layer, application/ command-orchestration responsibility, `AdminActionAuditor` mention on L76 / L109 / L127–132.
- `rules/traits/audit-heavy.md` — A1 (every command produces an immutable audit row), A3 (audit rows are append-only — UPDATE only the finalize columns), A7 (audit row carries actor identity).
- `rules/traits/transactional.md` — T3 (audit + outbox in one transaction), T4 (state-transitioning operations must record the transition).

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Class section, Worktree Dispatch Verification, Spring AOP self-invocation guidance, "Why unit-only baseline is insufficient" caveat (template-method extraction with @Transactional has bitten this repo before).
- `.claude/skills/backend/architecture/layered/SKILL.md` — Layered architecture per-package responsibility.
- `.claude/skills/backend/transaction-handling/SKILL.md` — REQUIRES_NEW propagation patterns.
- `.claude/skills/backend/audit-logging/SKILL.md` — audit entity + REQUIRES_NEW pattern.

---

# Related Contracts

- `specs/contracts/events/` — admin action outbox events (search for `admin.action.*` topics; payload shape must be byte-equal across refactor).

---

# Target Service

- `admin-service`

---

# Architecture

Follow:

- `projects/global-account-platform/specs/services/admin-service/architecture.md` — declared **Layered Architecture, 3-layer (no domain layer)**.

The new classes all live under `apps/admin-service/src/main/java/com/example/admin/application/` (or a new `application/audit/` sub-package — implementer's call).

---

# Implementation Notes

## Recommended split (final decision deferred to implementer after re-reading the 681-line source)

| Class | Responsibility | Notes |
|---|---|---|
| `AdminActionAuditWriter` | The actual INSERT (IN_PROGRESS / DENIED) and UPDATE (SUCCESS / FAILURE) of `admin_actions` + outbox publication. REQUIRES_NEW transaction methods live here. | This is the "hot" path. Must remain Spring-managed `@Component`. |
| `AdminActionPermissionRegistry` | The `ACTION_TARGET_TYPE` static map (20+ entries) + every `PERMISSION_*` / `REASON_*` synthetic constant. Pure data holder. | Likely a `@Component` (Spring resolves it as a singleton) or a `final class` with `private` ctor and static accessor — implementer chooses. |
| `AdminActionAuditRecords` (or per-record split) | The nested `record` types: `StartRecord`, `CompletionRecord`, `OperatorResolved`, etc. | Either a single records-bag file or one-record-per-file under `application/audit/`. |
| `AdminActionAuditor` (residual facade) | **Optional.** If callers cannot be moved to the new classes in this PR, keep `AdminActionAuditor` as a thin delegator that holds references to the 3 classes above and forwards each method. Internal LOC drops from 681 to ~120. | If callers ARE moved, delete this file entirely. |

The implementer should examine call sites first (`OperatorAdminController`, `AdminAuthController`, the various `AdminLockAccount*UseCase` / `AdminUnlock*UseCase` / `OperatorLogin*UseCase` / etc.) and decide between the facade-retained and facade-deleted variants based on call-site count and DI footprint.

## Hard constraints

- **REQUIRES_NEW** on every write path. The current source declares `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `recordStart`, `recordCompletion`, `recordDenied`, and the meta-audit fallback. Spring AOP applies the proxy only on the **first cross-bean call** (see `backend/refactoring/SKILL.md` § "Why unit-only baseline is insufficient"). If `AdminActionAuditor` delegates to `AdminActionAuditWriter` via constructor-injected field, the cross-bean boundary is preserved and REQUIRES_NEW activates. If the writer is split inside the same class via a nested class, AOP self-invocation fires and REQUIRES_NEW silently degrades — **avoid this**.
- **meta-audit fallback** (the `try { … } catch { auditWriter.safeRecord*(...) }` outermost wrapper that emits a `META_AUDIT_*` row when the primary audit row fails) must remain functional. The pattern is referenced in `architecture.md` L109.
- **MeterRegistry counter** sites must increment in identical conditions. Implementer should map each `Counter.increment()` call to its new home and verify the metric name + tags are byte-equal.

## Tests to write or move (not changed in logic)

- `AdminActionAuditorTest` — existing unit cohort. Split into:
  - `AdminActionAuditWriterTest` — covers the 3 write paths + meta-audit fallback.
  - `AdminActionPermissionRegistryTest` — covers `ACTION_TARGET_TYPE` lookup (per action code) + every `PERMISSION_*` / `REASON_*` constant equality.
- Existing IT (`AdminActionAuditIntegrationTest` if it exists, or equivalent under `src/test/java/com/example/admin/integration/`) — keep at facade level (or split if facade is deleted). The trigger-invariant test must be retained.

---

# Edge Cases

- **Concurrent recordStart + recordCompletion** on the same `admin_actions` row: DB trigger guarantees only IN_PROGRESS rows can be UPDATE-d. Split must not introduce a path that bypasses the trigger.
- **`operatorLookupPort` failure** mid-flow: meta-audit fallback writes a `META_AUDIT_OPERATOR_LOOKUP_FAILED` row in REQUIRES_NEW. Preserve.
- **Cross-tenant SUPER_ADMIN actions**: `tenant_id='*'` + `target_tenant_id=<specific>` stamping logic on every write path. Verify in unit tests for each new class.
- **Self-flow audit rows** (`<self_enrollment>` / `<self_login>` / `<self_refresh>` / `<self_logout>` / `<self_recovery_regenerate>` / `<self_profile_update>` family). The synthetic `PERMISSION_*` / `REASON_*` constants must remain reachable from the same call sites with the same string values.
- **Outbox publication ordering**: audit row INSERT/UPDATE must commit before the outbox event is dispatched (or in the same transaction, depending on the outbox impl). Refactor must preserve this ordering exactly.
- **DI cycle risk**: if `AdminActionAuditor` residual facade injects `AdminActionAuditWriter` and the writer in turn injects something that transitively imports the facade, a circular `@Autowired` chain emerges. Audit the new DI graph before opening the PR.

---

# Failure Scenarios

- **REQUIRES_NEW silently degrades** (Spring AOP self-invocation): outer use-case transaction rolls back, audit row also rolls back → A10 fail-closed violation. **Mitigation**: every write path must be on a *different* Spring bean than the caller. Verified by an IT that throws after `recordStart` and asserts the IN_PROGRESS row is still present.
- **Test logic accidentally changed during the split**: refactoring-policy.md "Do not change test assertions during refactoring". If a test's `when(...)` setup needs adjustment because the mock target moved from `AdminActionAuditor` to `AdminActionAuditWriter`, that's acceptable plumbing; if an `assertThat(...)` expectation changes, that's a behavior change disguised as refactoring → **revert**.
- **Outbox payload drift**: payload field added/removed/renamed during the split → consumer breakage in `security-service`. **Mitigation**: contract test against the byte-equal payload (existing `*EventContractTest` if present, or a new diff check between pre- and post-refactor JSON).
- **`MeterRegistry` counter regression**: counter name or tag changes → Prometheus query in oncall dashboards breaks. **Mitigation**: enumerate all `Counter.builder(...).register(...)` and `meterRegistry.counter(...)` sites pre- and post-refactor, assert identical names+tags.
- **Synthetic-permission constant string drift**: `"<self_login>"` becomes `"self_login"` accidentally → audit-table FK / API-level matching breaks. **Mitigation**: copy the constants byte-for-byte; unit-test the registry class against the existing string values.

---

# Test Requirements

- **Unit tests** for each new class. Move/rename existing `AdminActionAuditorTest` cases without changing their input/output expectations.
- **Integration test** for REQUIRES_NEW propagation: outer use-case throws → audit IN_PROGRESS row remains in DB.
- **Integration test** for DB trigger invariant: attempt to UPDATE a SUCCESS row → trigger raises.
- **Contract test** for outbox event payload byte-equality (or a snapshot test comparing pre/post payload JSON for each `ActionCode`).
- **Meter test** verifying counter name + tags are identical.

Test command (full check):

```
./gradlew :projects:global-account-platform:apps:admin-service:check
```

Note: per `backend/refactoring/SKILL.md` § Local environment caveat (Rancher Desktop cold-start limit), the IT cohort may need to be pushed to CI rather than run locally.

---

# Definition of Done

- [ ] Implementation completed (split into 3–4 classes)
- [ ] No audit-related class exceeds 250 LOC
- [ ] Tests added or moved (no test-logic changes)
- [ ] All unit + IT tests green: `./gradlew :projects:global-account-platform:apps:admin-service:check` BUILD SUCCESSFUL
- [ ] `specs/services/admin-service/architecture.md` updated (L76 / L109 / L127–132)
- [ ] DI graph audited for cycles
- [ ] Outbox payload byte-equality verified (snapshot or contract test)
- [ ] `MeterRegistry` counter names + tags verified identical
- [ ] Ready for review
