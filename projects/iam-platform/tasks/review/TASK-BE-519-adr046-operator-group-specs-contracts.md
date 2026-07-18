# Task ID

TASK-BE-519

# Title

ADR-046 step 1 — operator-group specs & contracts (rbac.md `group.manage` + data-model group tables + admin-api group endpoints + operator-management feature)

# Status

review

# Owner

backend

# Task Tags

- docs
- spec
- contract

---

# Dependency Markers

- **prerequisite (선행)**: `TASK-MONO-428` (ADR-046 ACCEPTED — roadmap UNPAUSED). This task is § 4 step 1.
- **후속 (unblocks)**: `TASK-BE-520` (backend) — MUST NOT start until this task's contracts are merged (Change Rule: contracts precede code).

---

# Goal

Author the specs and contracts for the operator-group model exactly as ADR-046 § 2 (D1–D6) decided, so the backend (BE-520) implements against a merged contract rather than inventing one. **Doc-only** — no `.java`/`.sql`/`.ts` in this task.

Operator groups are a **fan-out** (D2-A) grouping primitive owned by admin-service: granting a role/tenant-assignment to a group materialises ordinary `operator_tenant_assignment` / `admin_operator_roles` rows for each member, tagged with a `group_origin` marker. The evaluator, cache, and the three `rbac.md` confinement axes are **untouched**.

---

# Scope

## In Scope

- `projects/iam-platform/specs/services/admin-service/rbac.md`:
  - New permission key **`group.manage`** (gates all group mutations, D6). Add to the permission catalogue + the seed role→permission matrix (which admin roles hold it — mirror the role that holds `operator.manage` / tenant-assignment authority).
  - An explicit note: **v1 is fan-out (D2-A)** — group membership is NOT an evaluation-time edge; `PermissionEvaluator` and the three confinement axes are byte-unchanged; a group grant is materialised as ordinary flat per-operator rows.
- `projects/iam-platform/specs/services/admin-service/data-model.md`:
  - `operator_group(id, tenant_id, name, description, created_at, updated_at, …)` — tenant-scoped (D3).
  - `operator_group_member(group_id, operator_id, added_at, …)` — membership edge.
  - The **`group_origin` marker** on fan-out assignment rows (how `operator_tenant_assignment` / `admin_operator_roles` rows record that they were materialised by a group grant vs. a direct grant) — sibling of ADR-045's cascade trail. Specify the exact column(s)/table(s) and classification.
  - Classification (tenant-scoped, D3) + FK/PK + the idempotence invariant (D5: fan-out never duplicates an equal existing direct grant).
- `projects/iam-platform/specs/services/admin-service/contracts/http/admin-api.md`:
  - Group CRUD — `POST/GET/PATCH/DELETE /api/admin/groups` (+ `GET /api/admin/groups/{id}`).
  - Membership — `GET/POST/DELETE /api/admin/groups/{id}/members` (+ `/{operatorId}`).
  - Group grants — `GET/POST/DELETE /api/admin/groups/{id}/grants` (role and/or tenant-assignment grant to the group; fan-out on write, cascade-revoke on delete).
  - Each endpoint: request/response schema, `@RequiresPermission("group.manage")` gating, error envelope (401/403/404/409/422), no-escalation cap (D4), tenant-confinement (D3).
- `projects/iam-platform/specs/features/operator-management.md` (or the admin-service feature spec) — add the operator-group management flow (create group → add members → grant → fan-out; remove member / delete group → cascade-revoke).

## Out of Scope

- Inheritance semantics (D2-B) — explicitly a follow-up ADR, not v1.
- Consumer/account grouping (D1-C deferred).
- Any code, DDL file, seed SQL, or UI (that is BE-520 / PC-FE-250).
- Evaluator / cache / confinement-axis changes — v1 fan-out leaves them byte-unchanged (record that they are untouched; do not edit them).

---

# Acceptance Criteria

- [ ] **AC-1**: `rbac.md` defines `group.manage`, places it in the seed role matrix, and states v1 fan-out (evaluation unchanged) in prose.
- [ ] **AC-2**: `data-model.md` fully specifies `operator_group`, `operator_group_member`, and the `group_origin` marker (columns, PK/FK, tenant-scope, idempotence invariant D5).
- [ ] **AC-3**: `admin-api.md` specifies every group CRUD / membership / grant endpoint with method, path, schema, `group.manage` gating, error envelope, no-escalation (D4) and tenant-confinement (D3) rules.
- [ ] **AC-4**: The feature spec describes the fan-out / cascade lifecycle (D5) end-to-end, matching ADR-046 § 2 D5 bullet-for-bullet (grant-to-group, add-member, remove-member, delete-group, idempotence).
- [ ] **AC-5**: Doc-only — `git diff --stat` touches no `.java` / `.sql` / `.ts` / `.tsx`. No decision in D1–D6 is contradicted or re-opened (HARDSTOP-04).

---

# Related Specs

- `docs/adr/ADR-MONO-046-operator-group-model.md` § 2 (D1–D6), § 3 (invariants), § 4 step 1 (this task's charter)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (evaluator + three confinement axes — v1 does NOT extend)
- ADR-MONO-020 (`operator_tenant_assignment` — the fan-out substrate), ADR-MONO-024 (no-escalation cap D4), ADR-MONO-045 (cascade-trail sibling for `group_origin`)

# Related Contracts

- `projects/iam-platform/specs/services/admin-service/contracts/http/admin-api.md` (the file this task extends)

---

# Edge Cases

- **`group_origin` on a shared substrate** — fan-out rows live in the SAME `operator_tenant_assignment` / `admin_operator_roles` tables as direct grants; the marker must distinguish them so cascade-revoke never touches a direct grant. Spec must nail the exact discriminator.
- **Idempotent fan-out** — a member who already holds an equal direct grant must not get a duplicate `group_origin` row; conversely removing them from the group must not revoke their pre-existing direct grant (D5).
- **No-escalation at group-grant time** — the granter cannot grant the group a role/tenant they do not themselves hold (D4); the cap is evaluated at grant time AND (per D5) re-checked when a new member is added and the group's existing grants fan out to them.

---

# Failure Scenarios

- Contract omits the `group_origin` discriminator → BE-520 invents one, and cascade-revoke either misses group rows or destroys direct grants. Guard: AC-2.
- `group.manage` missing from the seed matrix → every group mutation 403s at runtime (fail-closed) with no role able to perform it. Guard: AC-1.
- Spec silently implies inheritance (evaluation-time) → BE-520 touches `PermissionEvaluator` (the exact HARDSTOP-09 risk the ADR spent D2 avoiding). Guard: AC-1/AC-5 assert v1 fan-out, evaluator untouched.
- No-escalation not specified per-endpoint → a TENANT_ADMIN escalates via a group grant. Guard: AC-3.
