# Task ID

TASK-BE-492

# Title

ADR-047 § 4 step 2b — admin-service org plane: `org.manage` permission + `ORG_ADMIN` node-scoped seed role, `admin_operator_roles.org_node_id`, `TenantScopeGuard` subtree driver, `OrgNodeAdminController` (`/api/admin/org-nodes` CRUD + ceiling + node admins), no-escalation cap, `admin_actions` audit

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- test

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-BE-490` (specs/contracts) → `TASK-BE-491` (account-service `org_node` authority + internal reads). This task consumes `GET /internal/org-nodes/{id}/tenants` and `/effective-ceiling`; it cannot start before BE-491 merges.
- **후속 (blocks)**: `TASK-PC-FE-237` (console consumes `/api/admin/org-nodes`).

---

# Goal

Give admin-service the **org plane**: a company-wide delegated admin (`ORG_ADMIN @ node`, D5) and the operator-facing CRUD for the tree, without weakening any existing guard.

After this task:

1. `org.manage` is a real permission key (`Permission.java` + Flyway seed), deny-default gated by `@RequiresPermission`.
2. `ORG_ADMIN` is a seed role. Seeding assigns it to **nobody** (net-zero — same discipline as `V0033__seed_tenant_admin_roles.sql`).
3. `admin_operator_roles` gains a **nullable `org_node_id`**, mutually exclusive with a concrete `tenant_id`: a grant row is either platform-scoped (`tenant_id='*'`), tenant-scoped, or **node-scoped**.
4. `TenantScopeGuard` / `AdminGrantScopeEvaluator` gain a **subtree driver**: a node-scoped grant's effective scope is the set of tenants under that node's subtree (resolved from account-service). `SUPER_ADMIN '*'` short-circuit and the existing tenant-scoped path are **byte-unchanged**.
5. `OrgNodeAdminController` exposes `/api/admin/org-nodes` as a **thin command gateway** onto account-service (the same shape `TenantAdminController` already uses).
6. **No-escalation is reused, not reinvented** (ADR-024 D2/D3): an `ORG_ADMIN` cannot grant a role/domain it does not itself hold, cannot exceed its node's effective ceiling, and can never mint `SUPER_ADMIN`.
7. Every node mutation, ceiling edit, and `ORG_ADMIN` grant writes an `admin_actions` row; denials write a DENIED row.

---

# Scope

## In Scope

- `domain/rbac/Permission.java` — add `ORG_MANAGE` (`org.manage`) to the catalog (pinned by `AdminActionPermissionRegistryTest`).
- **Flyway (admin-service)** — next free is `V0041` (highest today = `V0040`; re-verify):
  - `V0041__seed_org_manage_permission_and_org_admin_role.sql` — permission seed + `ORG_ADMIN` role + `admin_role_permissions` mapping, `INSERT IGNORE` idempotent, **no `admin_operator_roles` row**.
  - `V0042__admin_operator_roles_org_node_id.sql` — nullable `org_node_id` + CHECK/trigger asserting mutual exclusion with a concrete `tenant_id`.
- `AdminGrantScopeEvaluator.effectiveAdminScope` — add the node-scoped branch: expand `org_node_id` → subtree tenant ids via a new `OrgNodePort` (account-service internal client, mirroring the existing admin→account client). Short-lived cache; **fail-closed** on account-service failure (an unresolvable subtree contributes **no** tenants — never "all").
- `TenantScopeGuard.requireTenantInScope` — unchanged signature; the subtree membership rides in through the evaluator.
- `presentation/OrgNodeAdminController` — `GET/POST /api/admin/org-nodes`, `GET/PATCH/DELETE /{id}`, `PUT /{id}/ceiling`, `GET /{id}/tenants`, `GET/POST/DELETE /{id}/admins`. All `@RequiresPermission("org.manage")`.
- No-escalation on `POST /{id}/admins`: granted role ⊆ granter's holdings, granted domains ⊆ node effective ceiling, `SUPER_ADMIN` never grantable.
- `AdminActionAuditor` wiring for every mutation + `AdminActionDenyWriter` on 403.
- **Tests**:
  - Unit — subtree expansion, mutual-exclusion validation, no-escalation matrix, fail-closed on port failure.
  - Integration (Testcontainers + WireMock account-service) — `ORG_ADMIN @ node` reaches every subtree tenant; reaches **no** tenant outside it (404, not 403 — cross-scope is invisible, matching `OperatorAdminScopeConfinementIntegrationTest`); `SUPER_ADMIN` unchanged; a non-`org.manage` operator gets 403; over-ceiling grant → 422; `admin_actions` rows written.
  - Security — deny-default on every new endpoint; DENIED audit row on cross-scope.

## Out of Scope

- `org_node` persistence and ceiling math — owned by account-service (`TASK-BE-491`). admin-service **proxies**, it does not store the tree.
- auth-service (unchanged; the ceiling is applied at the account-service source).
- Console UI (`TASK-PC-FE-237`).
- Backfill (`TASK-BE-493`).
- Role-level ceiling (D3-B), grant-at-node (D2-C).

---

# Acceptance Criteria

- [ ] **AC-1**: `org.manage` in `Permission.catalog()`, seeded idempotently, surfaced by `GET /api/admin/permissions`. `ORG_ADMIN` seeded with its permission set and assigned to **zero** operators (net-zero).
- [ ] **AC-2**: `admin_operator_roles.org_node_id` nullable; a row with both a concrete `tenant_id` and an `org_node_id` is rejected at the DB and the application layer.
- [ ] **AC-3**: `effectiveAdminScope` for a node-scoped grant = exactly `subtreeTenantIds(org_node_id)`. Existing tenant-scoped and `'*'` platform behaviour is **byte-unchanged** (regression test pins both).
- [ ] **AC-4**: `ORG_ADMIN @ node` administers every tenant in the subtree; a tenant outside the subtree returns **404** and writes a DENIED `admin_actions` row.
- [ ] **AC-5**: account-service unreachable → subtree resolves to the **empty set** (fail-closed: the admin loses reach). A test asserts it never resolves to `'*'` or to all tenants.
- [ ] **AC-6**: No-escalation — granting a role the granter lacks → 403; granting a domain outside the node's effective ceiling → 422; granting `SUPER_ADMIN` → 403. All three covered.
- [ ] **AC-7**: Every org-node endpoint is deny-default (`@RequiresPermission("org.manage")`); an operator without it gets 403 + DENIED audit row. GET success writes no audit row (mirrors `grantable-roles` / BE-486 convention).
- [ ] **AC-8**: `./gradlew :projects:iam-platform:apps:admin-service:test` GREEN; Testcontainers integration GREEN in **CI Linux** (authoritative).

---

# Related Specs

> `platform/entrypoint.md` Step 0 first. `rules/traits/multi-tenant.md` M1–M7 load-bearing.

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` D2/D3/D5/D6 (**authority**)
- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` § D2/D3 (no-escalation — reused verbatim)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the ceiling bounds entitlement, never mints roles)
- `projects/iam-platform/specs/services/admin-service/{architecture,rbac,data-model,security}.md` (Thin Layered / Command Gateway)
- `platform/testing-strategy.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (org-node endpoints)
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (subtree + effective-ceiling reads)

---

# Target Service

- `admin-service`

# Architecture

Follow `projects/iam-platform/specs/services/admin-service/architecture.md` (Thin Layered — Command Gateway; `presentation → application → port ← infrastructure adapter`).

---

# Implementation Notes

- `OrgNodeAdminController` is a **gateway**, mirroring `TenantAdminController`: it authorizes, audits, and forwards. The tree, the cycle/depth checks, and the ceiling math live in account-service.
- Subtree expansion runs on the permission-check path. Cache it briefly, but **never** cache a failure as a permissive value.
- The evaluator's existing `'*'` short-circuit must stay first — a `SUPER_ADMIN` must not pay a subtree round-trip.
- `AdminGrantScopeEvaluator` is security-critical: add the branch, do not restructure the method.
- Re-verify the next free Flyway versions before writing `V0041`/`V0042`.

---

# Edge Cases

- A grant row with `org_node_id` AND `tenant_id='*'` — platform scope wins and the node is meaningless; forbid the combination rather than resolving it.
- An `ORG_ADMIN` whose node is deleted (BE-491 forbids deleting a node with tenants, but not a childless empty node) → the grant resolves to an empty subtree = no reach. Fail-closed; do not fall back to the operator's home tenant.
- Nested `ORG_ADMIN` grants (a parent-node admin and a child-node admin) — the parent's subtree includes the child's; union of grants is correct and does not escalate (both are already ≤ their own ceilings).
- An `ORG_ADMIN` granting `TENANT_ADMIN` inside the subtree — allowed **iff** the granter holds it and the target domain set ≤ node ceiling.
- Cross-scope must be **404**, not 403 — 403 leaks the existence of a tenant outside the subtree (existing confinement convention).
- account-service slow → the permission check must time out **closed**, not open.

---

# Failure Scenarios

- Subtree resolution failure treated as "unknown → allow" → a company-wide admin silently becomes platform-wide. Guard: AC-5 (empty set, explicit test that it is never `'*'`).
- `effectiveAdminScope` restructured and the `'*'` short-circuit reordered after the subtree branch → `SUPER_ADMIN` pays a round-trip and, if account-service is down, **loses** platform reach. Guard: AC-3 regression test + "add a branch, don't restructure".
- Mutual exclusion enforced only in the app layer → a hand-written SQL row (seed, migration, ops fix) creates an ambiguous grant. Guard: AC-2 requires the DB constraint too.
- No-escalation re-implemented rather than reused → a subtly different cap than ADR-024's, and `SUPER_ADMIN` becomes mintable. Guard: AC-6 + reuse requirement.
- The ceiling is consulted when *deriving* a role rather than when *bounding entitlement* → ADR-023 plane separation breaks. The ceiling appears only on entitlement paths.
- Local-only Testcontainers verification → not authoritative. Guard: AC-8 CI Linux.
