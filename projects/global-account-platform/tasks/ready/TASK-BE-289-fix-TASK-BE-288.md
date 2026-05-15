# Task ID

TASK-BE-289-fix-TASK-BE-288

# Title

TASK-BE-288 review follow-up — architecture.md OperatorRoleResolver dead reference (Finding 2) + deferred admin_operator_roles.tenant_id correctness

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

Close the two remaining items from the **TASK-BE-288** review (port extraction,
PR #555 — `/review-task TASK-BE-288` → `fix_needed`).

**Finding 1 is already resolved and merged** — not in scope here except as
context. Option A was applied in PR #555: `PatchOperatorRoleUseCase` role
bindings are pinned to the legacy hardcoded `tenant_id` via the documented
`LEGACY_BINDING_TENANT_ID = "fan-platform"` constant (commit `572e003c`,
squash-merged to `main` as `1ef970bf`; CI run `25919652146` 17/17 pass incl.
`Integration (global-account-platform, Testcontainers)`). TASK-BE-288 is
therefore **strictly behaviour-neutral vs origin/main**.

This task delivers the **two independent follow-ups** Option A deferred:

- **WI-1 — Finding 2 (doc, fix-forward).** `application/OperatorRoleResolver.java`
  was deleted by TASK-BE-288 (folded into `AdminOperatorPort` + `AuditReasons`),
  but `specs/services/admin-service/architecture.md` still names it. Spec→
  deleted-code dead reference — the exact drift class ADR-MONO-012 /
  TASK-MONO-085/086 police.
- **WI-2 — deferred `admin_operator_roles.tenant_id` correctness.** The
  legacy `"fan-platform"` pin is a known latent multi-tenancy bug (role
  bindings for non-fan-platform operators carry the wrong tenant scope,
  inconsistent with `CreateOperatorUseCase` which already stamps the real
  tenant). This is the deliberate, properly-scoped correctness fix Option A
  promised to file separately.

WI-1 and WI-2 are independent (doc-only vs migration+code+test) and **may ship
as separate PRs** (`feedback_pr_bundling` — case-by-case).

---

# Scope

## WI-1 — In Scope (Finding 2)

Update `specs/services/admin-service/architecture.md`:

- **L75** — Internal Structure Rule tree: remove the
  `OperatorRoleResolver.java ← (TASK-BE-121) …` entry; represent the
  post-refactor shape (`application/port/AdminOperatorPort.java`,
  `application/port/AdminOperatorTotpPort.java`, `application/AuditReasons.java`,
  and the `infrastructure/persistence/rbac/JpaAdminOperatorAdapter.java` /
  `infrastructure/persistence/JpaAdminOperatorTotpAdapter.java` adapters),
  consistent with how the existing 5 ports are represented.
- **L87** — fix the `infrastructure/security` tree cross-reference to
  `application/OperatorRoleResolver`.
- **L141** — rewrite or remove the normative naming-collision Boundary Rule
  paragraph: its disambiguation target (`application/OperatorRoleResolver`) no
  longer exists, so the planned `OperatorEndpointAccessResolver` name is now
  unconstrained; the caveat is stale, not merely informational.

## WI-2 — In Scope (deferred tenant_id correctness)

- Flip `PatchOperatorRoleUseCase` from `LEGACY_BINDING_TENANT_ID` to
  `operator.tenantId()` (aligns with `CreateOperatorUseCase`; matches the
  declared `admin_operator_roles.tenant_id` intent — *"tenant_id mirrors the
  operator's tenantId"*, `AdminOperatorRoleJpaEntity.java` L38-39). Remove the
  now-obsolete `LEGACY_BINDING_TENANT_ID` constant + its javadoc.
- **Flyway data migration** backfilling existing `admin_operator_roles` rows
  written with the wrong hardcoded `"fan-platform"` for non-fan-platform
  operators (otherwise old + new rows for the same operator carry inconsistent
  tenant scope). Backfill rule: `admin_operator_roles.tenant_id :=
  admin_operators.tenant_id` for the bound operator. Confirm join key
  (`admin_operator_roles.operator_id` → `admin_operators.id`).
- **Tenant-isolation regression test**: patch-roles on a non-fan-platform
  target operator → assert persisted binding `tenant_id == target operator
  tenant` (PROJECT.md *"격리 회귀 방지 테스트가 필수"*).
- Decide the now-unused legacy 4-arg `AdminOperatorRoleJpaEntity.create`
  overload: remove as dead code (no remaining callers after this flip) or keep
  with rationale.
- Confirm corrected semantics against `specs/services/admin-service/data-model.md`
  + `docs/adr/ADR-002-admin-tenant-scope-sentinel.md` + TASK-BE-249. If a spec
  needs to state the per-tenant binding rule explicitly, update it **before**
  the code (specs win over tasks).

## Out of Scope

- Re-porting `CachingPermissionEvaluator` / `AdminActionJpaEntity` / TOTP crypto
  (unchanged from TASK-BE-288 Out of Scope).
- Any HTTP `admin-api.md` / event `admin-events.md` envelope change (WI-2 is
  persisted-state correctness, not envelope shape).
- Re-litigating TASK-BE-288 LOC delta (accepted: existing-file −554 vs
  port/adapter scaffolding ~+583, per that task's AC parenthetical).

---

# Acceptance Criteria

### WI-1

- [ ] `architecture.md` L75/L87/L141 no longer reference the deleted
      `OperatorRoleResolver`; the port/adapter/`AuditReasons` shape is
      documented; the L141 caveat is rewritten or removed.
- [ ] grep = 0 `OperatorRoleResolver` in `specs/` and `apps/admin-service`
      production code (historical task/ADR/INDEX records excluded).

### WI-2

- [ ] `PatchOperatorRoleUseCase` binds `operator.tenantId()`;
      `LEGACY_BINDING_TENANT_ID` constant + javadoc removed.
- [ ] Flyway migration backfills mis-stamped `admin_operator_roles` rows; a
      verification query shows 0 rows where `aor.tenant_id != ao.tenant_id`
      after migration (for non-platform-sentinel operators).
- [ ] Tenant-isolation regression test added and green: non-fan-platform
      target operator → patch-roles binding `tenant_id == target tenant`.
- [ ] `CreateOperatorUseCase` ↔ `PatchOperatorRoleUseCase` now consistent
      (both stamp the operator's real tenant).
- [ ] `./gradlew :projects:global-account-platform:apps:admin-service:test`
      GREEN (CI authoritative for Testcontainers IT per
      `project_testcontainers_docker_desktop_blocker`).
- [ ] data-model.md / ADR-002 confirmation recorded; spec updated first if the
      per-tenant binding rule is not already explicit.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 —
> `PROJECT.md` (domain=saas, traits=[transactional, regulated, audit-heavy,
> integration-heavy, multi-tenant]), `rules/common.md`, `rules/domains/saas.md`,
> 5 trait files.

- `specs/services/admin-service/architecture.md` — Internal Structure Rule
  (L52-94), Boundary Rules (L141), Tenant Scope Enforcement (TASK-BE-249,
  L232-280). **WI-1 edits L75/L87/L141.**
- `specs/services/admin-service/data-model.md` — `admin_operator_roles.tenant_id
  VARCHAR(32) NOT NULL` semantics (WI-2).
- `docs/adr/ADR-002-admin-tenant-scope-sentinel.md` — per-tenant role binding
  + platform-scope sentinel.
- `rules/traits/multi-tenant.md` — row-level isolation + mandatory isolation
  regression test (WI-2).
- `rules/traits/audit-heavy.md` A2/A3 — role binding tenant fields.

# Related Skills

- `.claude/skills/review-checklist/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/admin-api.md` — `PATCH /api/admin/operators/{id}/roles`
  response shape (unchanged — WI-2 is persisted-state, not envelope).
- `specs/contracts/events/admin-events.md` — `admin.action.performed`
  envelope (unchanged).

---

# Target Service

- `admin-service`

---

# Architecture

Follow `specs/services/admin-service/architecture.md` — Thin Layered (Command
Gateway). WI-1 *corrects the architecture doc* to match the TASK-BE-288 port
shape. WI-2 changes no architecture style (persisted value + migration + test).

---

# Implementation Notes

1. WI-1 is project-internal spec polish — ADR-MONO-003a §D1.1 / D4 override
   precedent (same class as BE-283/BE-284 dead-ref closures). Fix-forward.
2. WI-2 is the deliberate multi-tenancy correctness fix Option A deferred from
   TASK-BE-288. Migration ordering: backfill existing rows in the same PR that
   flips the code, so post-deploy no operator carries mixed-tenant bindings.
3. The behaviour-preservation pin lives in `PatchOperatorRoleUseCase` as
   `LEGACY_BINDING_TENANT_ID` with javadoc cross-referencing this task — remove
   both when WI-2 lands.
4. `OperatorView.tenantId()` may be `null`/`"*"` (SUPER_ADMIN platform
   sentinel). Verify the flip + migration handle the sentinel and null per
   ADR-002 (`isTenantAllowed` rules, architecture.md L255-263) — do not write
   `"*"` into a binding row if data-model.md forbids it.

---

# Edge Cases

- SUPER_ADMIN (tenant_id `"*"`) patches roles of a `"fan-platform"` operator →
  binding tenant_id must be the **target** operator's tenant, not `"*"` —
  confirm against ADR-002 / data-model.md.
- Target operator `tenant_id = null` → `NOT NULL` column; WI-2 must define the
  fallback (legacy code used `"fan-platform"`; `AdminActionAuditor` uses a
  `"fan-platform"` defensive fallback — keep consistent).
- Empty `roleNames` → no bindings persisted (adapter empty-guard) — unaffected.
- Backfill must skip platform-sentinel rows if any exist with `tenant_id = "*"`.

# Failure Scenarios

- WI-2 code flip without the backfill migration → same operator carries mixed
  `tenant_id` across old (`"fan-platform"`) and new (real tenant) bindings →
  tenant-scoped audit/role queries return inconsistent sets.
- WI-1 left undone → architecture.md remains a spec→deleted-code dead reference;
  an implementer following L141 to name `OperatorEndpointAccessResolver` is
  mis-guided by a caveat about a class that no longer exists.
- Removing the legacy 4-arg overload without confirming zero callers →
  compile break elsewhere.

---

# Test Requirements

- WI-2: tenant-isolation regression test (non-fan-platform target → binding
  tenant_id == target tenant) + migration verification query (0 mismatched
  rows post-backfill).
- WI-1: no test (doc) — verified by grep = 0 `OperatorRoleResolver` in
  specs + production.
- `./gradlew :projects:global-account-platform:apps:admin-service:test` GREEN
  (CI authoritative for Testcontainers IT).

---

# Definition of Done

- [ ] WI-1: architecture.md L75/L87/L141 updated; grep = 0 `OperatorRoleResolver`
- [ ] WI-2: code flip + Flyway backfill + isolation regression test + spec/ADR
      confirmation; legacy 4-arg overload + `LEGACY_BINDING_TENANT_ID` resolved
- [ ] admin-service tests GREEN (CI)
- [ ] Branch(es): `task/be-289-...` (substring `master` 금지)
- [ ] PR(s): `docs(gap-admin):` (WI-1) + `fix(gap-admin):` (WI-2) — bundled or
      split per `feedback_pr_bundling`
- [ ] Ready for review
