# Task ID

TASK-BE-520

# Title

ADR-046 step 2 — operator-group backend (Flyway + JPA + GroupAdminController + fan-out/cascade service + group.manage seed + audit + test matrix)

# Status

ready

# Owner

backend

# Task Tags

- backend
- iam
- rbac

---

# Dependency Markers

- **prerequisite (선행)**: `TASK-BE-519` (specs & contracts) — MUST be merged first (Change Rule: contracts precede code). `TASK-MONO-428` (ADR-046 ACCEPTED).
- **후속 (unblocks)**: `TASK-PC-FE-250` (console screen) — consumes this task's `/api/admin/groups` API.

---

# Goal

Implement the operator-group model in admin-service exactly as ADR-046 § 2 (D1–D6) decided and BE-519's contracts specify: a **fan-out** grouping primitive (D2-A) that materialises ordinary per-operator assignment rows tagged `group_origin`, leaving `PermissionEvaluator` + cache + the three `rbac.md` confinement axes **byte-unchanged**.

---

# Scope

## In Scope

- **Flyway migration** (admin-service): `operator_group` (tenant-scoped, D3) + `operator_group_member`; the `group_origin` marker on the fan-out assignment substrate (`operator_tenant_assignment` / `admin_operator_roles` per BE-519's data-model). Idempotent, backward-compatible (nullable/defaulted marker so existing direct grants are unaffected).
- **JPA entities + repository/adapter/port** for `operator_group`, `operator_group_member`, following the service's existing hexagonal/layered structure (mirror `OrgNode*` / operator-assignment adapters).
- **`GroupAdminController`** (`/api/admin/groups`): CRUD, `/{id}/members` (list/add/remove), `/{id}/grants` (list/add/remove role & tenant-assignment grants). Every mutation `@RequiresPermission("group.manage")` + `admin_actions` audit row (D6, deny-default, fail-closed).
- **Fan-out / cascade service (D5)** with transaction + outbox atomicity:
  - Grant role/assignment to group → fan out to all current members (materialise `group_origin` rows).
  - Add member → fan out the group's current grants to the new member.
  - Remove member → revoke that member's `group_origin` rows for this group (direct grants untouched).
  - Delete group → cascade-revoke all its `group_origin` rows; members keep independent direct grants.
  - **Idempotence**: fan-out never duplicates an equal existing direct grant; removal never touches non-`group_origin` rows.
- **No-escalation cap (D4)** — reuse the existing ADR-024 no-escalation evaluator (`AdminGrantScopeEvaluator` / grantable-roles): a group grant is capped by the granter's own holdings, checked at grant time AND when a new member triggers fan-out. `SUPER_ADMIN` net-zero preserved.
- **`TenantScopeGuard` confinement (D3)** — a TENANT_ADMIN manages only their tenant's groups; `SUPER_ADMIN` (`'*'`) platform-wide.
- **`group.manage` seed** (Flyway) into the role→permission matrix per BE-519's `rbac.md`.
- **Group lifecycle events** (D6) — audit-only in v1 unless a consumer exists; if emitted, ride `admin_outbox` v2 with a new `topicFor` mapping (sibling: `PartnershipEventPublisher`).

## Out of Scope

- Inheritance (D2-B) — follow-up ADR. Do NOT touch `PermissionEvaluator`, the RBAC cache, or the three confinement axes.
- Consumer/account grouping (D1-C).
- Console UI (PC-FE-250).

---

# Acceptance Criteria

- [ ] **AC-1**: Flyway creates `operator_group` + `operator_group_member` + the `group_origin` marker; migration is idempotent and existing direct grants are byte-unaffected (marker defaults to non-group). A migration-shape test proves it.
- [ ] **AC-2**: `GroupAdminController` exposes every endpoint in BE-519's `admin-api.md` with matching schema + status codes; all mutations gated by `@RequiresPermission("group.manage")` and audited.
- [ ] **AC-3**: Fan-out/cascade (D5) is transactional and outbox-atomic; unit tests cover grant-to-group, add-member, remove-member, delete-group, and idempotence (no duplicate on equal existing direct grant; removal spares non-group rows).
- [ ] **AC-4**: No-escalation (D4) enforced — a Testcontainers integration test proves a granter cannot grant the group a role/tenant it does not hold (grant-time AND add-member fan-out time), returning the contract's 403/422; `SUPER_ADMIN` net-zero unchanged.
- [ ] **AC-5**: Tenant-confinement (D3) enforced — a TENANT_ADMIN cannot see/mutate another tenant's group (403); `SUPER_ADMIN` platform-wide.
- [ ] **AC-6**: `PermissionEvaluator`, RBAC cache, and the three `rbac.md` confinement axes are **byte-unchanged** (v1 fan-out). `git diff` proves no edit to those files.
- [ ] **AC-7**: Test matrix per ADR § 4 step 2 — Unit (fan-out/cascade + audit-on-failure), Integration (Testcontainers + WireMock), Security (non-admin 403), Audit immutability, Fail-closed, no-escalation matrix. CI (Linux Testcontainers) GREEN — authoritative.

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (evaluator + confinement axes — v1 does NOT extend), `data-model.md`, `contracts/http/admin-api.md` (all authored by BE-519)
- `docs/adr/ADR-MONO-046-operator-group-model.md` § 2 (D1–D6), § 3 (invariants preserved)
- `projects/iam-platform/specs/services/admin-service/architecture.md` (Service Type → test-requirement section)

# Related Contracts

- `projects/iam-platform/specs/services/admin-service/contracts/http/admin-api.md` (group endpoints, BE-519)

---

# Edge Cases

- **Fan-out on the shared substrate** — `group_origin` rows share tables with direct grants; cascade-revoke MUST filter on the marker so a direct grant is never destroyed (D5). Test both directions.
- **Idempotent double-grant** — granting the same role to a group twice, or adding a member who already holds the direct grant, must not create duplicate rows (unique constraint or upsert).
- **Concurrent membership + grant** — add-member and grant-to-group racing must not lose a fan-out row (transaction boundary).
- **Audit-on-failure** — a fan-out that partially fails must roll back AND still record the attempt in `admin_actions` (deny-default, fail-closed).
- **Testcontainers on Windows is non-authoritative** — CI Linux is the arbiter; do not conclude GREEN from a local Windows run (may be FLAKY/SKIPPED).

---

# Failure Scenarios

- Cascade-revoke filters on `(operator, tenant, role)` instead of the `group_origin` marker → deleting a group destroys a member's direct grant. Guard: AC-3 idempotence + AC-6.
- No-escalation checked only at grant time, not at add-member fan-out → a low-privilege granter's group later fans a role they hold onto a member, but a member added after a granter's own privilege was revoked escalates. Guard: AC-4 (both times).
- Evaluator touched "for convenience" → the exact HARDSTOP-09 risk D2 avoided; cache-invalidation + confinement re-reasoning silently required. Guard: AC-6 byte-unchanged evaluator.
- Migration not idempotent / marker not defaulted → existing direct grants shift origin or the migration fails on re-run. Guard: AC-1 migration-shape test.
- Local Windows Testcontainers green misread as done while CI is RED. Guard: AC-7 CI-authoritative.
