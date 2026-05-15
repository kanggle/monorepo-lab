# Task ID

TASK-BE-289-fix-TASK-BE-288

# Title

TASK-BE-288 review fix — PatchOperatorRoleUseCase tenant_id behavior deviation + architecture.md OperatorRoleResolver dead reference

# Status

ready

# Owner

backend

# Task Tags

- code
- adr

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

Resolve two issues found while reviewing **TASK-BE-288** (admin-service operator/totp
port extraction, PR #555). TASK-BE-288 declared an absolute *behavior-0 /
byte-identical* contract; the review confirmed 8 of 9 application files and both
new ports/adapters preserve behavior exactly, but found one **persisted-state
behavior deviation** and one **spec→deleted-code dead reference** that must be
closed for the refactor to honor its own scope and this monorepo's spec-drift
policy.

---

# Scope

## In Scope

### Finding 1 (BLOCKING — behavior deviation, gates PR #555 merge)

`PatchOperatorRoleUseCase` role-binding `tenant_id` changed semantics during the
TASK-BE-288 port migration:

- **Before (origin/main):** `AdminOperatorRoleJpaEntity.create(entity.getId(),
  role.getId(), now, actorInternalId)` — the **legacy 4-arg overload**, which
  resolves `tenant_id = "fan-platform"` (hardcoded; see
  `AdminOperatorRoleJpaEntity.java` L43-47, *"Legacy call sites that predate
  TASK-BE-249 — tenantId resolved to 'fan-platform'."*).
- **After (TASK-BE-288):** `new AdminOperatorPort.NewRoleBinding(operator.internalId(),
  role.id(), now, actorInternalId, operator.tenantId())` → `JpaAdminOperatorAdapter.saveOperatorRoles`
  → **5-arg overload** with `tenant_id = operator.tenantId()` (the target
  operator's actual tenant).

For any target operator whose `tenant_id != "fan-platform"` (WMS-tenant
operators; SUPER_ADMIN patching roles of a cross-tenant operator), the persisted
`admin_operator_roles.tenant_id` value **changes**. This is observable in
tenant-scoped audit/role queries and contradicts TASK-BE-288's explicit
*"HTTP API 응답 / Kafka outbox envelope / audit row 컬럼 값 byte-identical"* and
*"production behavior 0 변경"* guarantee — in a `multi-tenant` + `regulated` +
`audit-heavy` service whose `PROJECT.md` declares tenant isolation a first-class
invariant requiring mandatory regression tests. (`CreateOperatorUseCase` already
used the 5-arg+tenantId path on `origin/main`, so the refactor *aligned*
patch-roles to create — most likely an intentional latent-bug fix that was not
declared in TASK-BE-288 scope.)

Pick **one** resolution and record the rationale in this task's done-note:

- **Option A (default — scope-honest):** Restore exact legacy semantics in
  `PatchOperatorRoleUseCase` so TASK-BE-288 is provably behavior-neutral —
  construct the `NewRoleBinding` with the constant `"fan-platform"` (matching
  the legacy 4-arg overload), OR have `JpaAdminOperatorAdapter` expose a
  binding-construction path that mirrors the legacy 4-arg overload. Then file a
  **separate, deliberate** task for the `admin_operator_roles.tenant_id`
  correctness fix (with its own migration, see Option B b1/b2).
- **Option B (ratify as intentional bug-fix):** Keep `operator.tenantId()`
  (aligns code with the declared `admin_operator_roles.tenant_id` intent —
  *"tenant_id mirrors the operator's tenantId"*, `AdminOperatorRoleJpaEntity.java`
  L38-39). Then ALL of the following are mandatory:
  - **b1.** Flyway data migration backfilling existing `admin_operator_roles`
    rows that were written with the wrong hardcoded `"fan-platform"`
    `tenant_id` for non-fan-platform operators (otherwise old + new rows for
    the same operator carry inconsistent tenant scope).
  - **b2.** Tenant-isolation regression test: patch-roles on a non-fan-platform
    target operator → asserts persisted binding `tenant_id == target operator
    tenant` (PROJECT.md *"격리 회귀 방지 테스트가 필수"*).
  - **b3.** Amend the TASK-BE-288 done-note + this task to record that the
    patch-roles path was **not** strictly behavior-neutral and why the
    deviation is accepted.
  - **b4.** Confirm against `specs/services/admin-service/data-model.md` +
    ADR-002 + TASK-BE-249 that the corrected semantics match the declared
    contract.

### Finding 2 (minor — spec dead reference)

`application/OperatorRoleResolver.java` was deleted by TASK-BE-288 (folded into
`AdminOperatorPort` for role/actor resolution + `application/AuditReasons` for
reason normalization). `specs/services/admin-service/architecture.md` still
names it:

- **L75** — Internal Structure Rule tree:
  `│   ├── OperatorRoleResolver.java        ← (TASK-BE-121) use-case role-name → JPA entity 리졸버 + actor internal id 헬퍼. 패키지-사적, application 전용.`
- **L87** — `infrastructure/security` tree note referencing
  `application/OperatorRoleResolver(use-case 헬퍼)`.
- **L141** — normative Boundary Rule paragraph whose entire purpose is to
  disambiguate the planned `OperatorEndpointAccessResolver` from
  `application/OperatorRoleResolver`.

Update `architecture.md` so the spec describes the post-refactor shape:

- Replace the L75 tree entry with the new components (`application/port/AdminOperatorPort.java`,
  `application/port/AdminOperatorTotpPort.java`, `application/AuditReasons.java`,
  and the `infrastructure/persistence/rbac/JpaAdminOperatorAdapter.java` /
  `infrastructure/persistence/JpaAdminOperatorTotpAdapter.java` adapters),
  consistent with how the existing 5 ports are represented.
- Fix the L87 cross-reference.
- Rewrite or remove the L141 naming-collision caveat — the collision target
  (`application/OperatorRoleResolver`) no longer exists, so the planned
  `OperatorEndpointAccessResolver` name is unconstrained; the caveat is now
  stale, not merely informational.

## Out of Scope

- Re-porting `CachingPermissionEvaluator` / `AdminActionJpaEntity` / TOTP crypto
  utilities (explicitly Out of Scope in TASK-BE-288, unchanged here).
- Any contract change to `admin-api.md` / `admin-events.md` (Finding 1 changes
  persisted state, not the HTTP/event envelope shape).
- LOC: TASK-BE-288's "net 감소" AC is net ~flat on total (existing-file −554 vs
  ~+583 port/adapter scaffolding) — accepted per that AC's own parenthetical
  ("port 추가 LOC ≤ ..."), not re-litigated here.

---

# Acceptance Criteria

- [ ] **Finding 1** resolved via Option A or Option B with the chosen option +
      rationale recorded in this task's done-note.
  - [ ] If Option A: `git diff origin/main` on `PatchOperatorRoleUseCase` +
        adapter shows the persisted `admin_operator_roles.tenant_id` for the
        patch-roles path is byte-identical to `origin/main` for **all** operator
        tenants (including a non-fan-platform fixture).
  - [ ] If Option B: b1 migration + b2 regression test + b3 done-note amendment
        + b4 contract confirmation all present and green.
- [ ] **Finding 2**: `specs/services/admin-service/architecture.md` L75/L87/L141
      no longer reference the deleted `OperatorRoleResolver`; the new
      port/adapter/`AuditReasons` shape is documented; the L141 caveat is
      rewritten or removed.
- [ ] No `OperatorRoleResolver` string remains in `specs/` or `apps/admin-service`
      production code (grep = 0, excluding historical task/ADR records).
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:test`
      GREEN (CI authoritative for Testcontainers IT per
      `project_testcontainers_docker_desktop_blocker`).
- [ ] HTTP API responses / Kafka outbox envelope / audit row column values
      remain byte-identical to `origin/main` for the fan-platform tenant on the
      patch-roles path (the common case must be unchanged under either option).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy,
> integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`,
> 5 trait files.

- `specs/services/admin-service/architecture.md` — Internal Structure Rule
  (L52-94), Boundary Rules (L141), Tenant Scope Enforcement (TASK-BE-249,
  L232-280). **This task edits L75/L87/L141** (Finding 2).
- `specs/services/admin-service/data-model.md` — `admin_operator_roles.tenant_id
  VARCHAR(32) NOT NULL` semantics (Finding 1 Option B b4).
- `docs/adr/ADR-002-admin-tenant-scope-sentinel.md` — tenant-scope sentinel +
  per-tenant role binding intent.
- `rules/traits/multi-tenant.md` — row-level isolation, mandatory isolation
  regression test.
- `rules/traits/audit-heavy.md` A2/A3 — audit/role binding tenant fields.
- `rules/traits/transactional.md` T5 — optimistic lock (unchanged by this fix;
  context only).

# Related Skills

- `.claude/skills/review-checklist/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md` — behavior-preservation rules.

---

# Related Contracts

- `specs/contracts/http/admin-api.md` — `PATCH /api/admin/operators/{id}/roles`
  response shape (unchanged — Finding 1 is persisted-state, not envelope).
- `specs/contracts/events/admin-events.md` — `admin.action.performed` envelope
  (unchanged).

---

# Target Service

- `admin-service`

---

# Architecture

Follow `specs/services/admin-service/architecture.md` — Thin Layered (Command
Gateway). This fix does not change architecture style; Finding 2 *corrects* the
architecture doc to match the TASK-BE-288 port shape.

---

# Implementation Notes

1. Finding 1 is the merge gate for PR #555: shipping a tenant-scoped
   persisted-state change into `main` under a "behavior 0" refactor banner is
   the deviation. Resolve it **in PR #555 before merge** (Option A keeps #555
   strictly behavior-neutral; Option B turns #555 into a declared bug-fix with
   migration + test). Do not fix-forward Finding 1 after #555 merges.
2. Finding 2 (doc drift) may be fix-forward (separate spec-polish commit) — it
   is project-internal spec polish, ADR-MONO-003a §D1.1 / D4 override precedent
   (same class as BE-283/BE-284 dead-ref closures), and is exactly the
   spec→deleted-code drift ADR-MONO-012 / TASK-MONO-085/086 police.
3. The legacy 4-arg `AdminOperatorRoleJpaEntity.create` overload still exists
   and is now unused after TASK-BE-288 (only the 5-arg path is reached). If
   Option A is chosen, decide whether to keep the 4-arg overload (legacy
   parity) or remove it as dead code in the follow-up correctness task.

---

# Edge Cases

- SUPER_ADMIN (tenant_id `"*"`) patches roles of a `"fan-platform"` operator →
  binding tenant_id under Option A = `"fan-platform"` (legacy), Option B =
  target operator's `"fan-platform"` — identical here; the divergence is only
  for non-fan-platform targets.
- Target operator with `tenant_id = null` → `OperatorView.tenantId()` null →
  Option B would pass null into a `NOT NULL` column → must be guarded
  (verify `data-model.md` + entity behavior).
- Empty `roleNames` → no bindings persisted (adapter `saveOperatorRoles`
  empty-guard preserved) — unaffected by either option.

# Failure Scenarios

- Option B without b1 migration → same operator carries mixed `tenant_id`
  values across old (`"fan-platform"`) and new (actual tenant) role bindings →
  tenant-scoped audit/role queries return inconsistent sets.
- Finding 2 left unfixed → architecture.md remains a spec→deleted-code dead
  reference; a future implementer following L141 to name
  `OperatorEndpointAccessResolver` is mis-guided by a caveat about a class that
  no longer exists.
- Option A applied but the legacy 4-arg overload later removed without updating
  PatchOperatorRoleUseCase → compile break.

---

# Test Requirements

- Option A: unit test asserting `PatchOperatorRoleUseCase` produces a
  `NewRoleBinding` whose `tenantId` equals the legacy `"fan-platform"` for a
  non-fan-platform target operator (proves behavior-neutral).
- Option B: b2 isolation regression test (non-fan-platform target → binding
  tenant_id == target tenant) + b1 migration verification (existing rows
  backfilled).
- `architecture.md` edit: no test (doc) — verified by grep = 0
  `OperatorRoleResolver` in `specs/` + production.
- `./gradlew :projects:global-account-platform:apps:admin-service:test` GREEN
  (CI authoritative for Testcontainers IT).

---

# Definition of Done

- [ ] Finding 1 resolved (Option A or B, rationale recorded)
- [ ] Finding 2 resolved (architecture.md L75/L87/L141 updated)
- [ ] grep = 0 `OperatorRoleResolver` in specs + production code
- [ ] admin-service tests GREEN (CI)
- [ ] fan-platform-tenant patch-roles path byte-identical to origin/main
- [ ] Branch: `task/be-289-fix-be-288-...` (substring `master` 금지)
- [ ] Single PR, commit prefix `fix(gap-admin):` (Finding 1) +
      `docs(gap-admin):` or bundled
- [ ] Ready for review
