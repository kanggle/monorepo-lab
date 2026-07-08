# ADR-MONO-046 — Operator Group Model (a first-class grouping primitive for `admin_operators` that lets an admin assign roles / tenant-assignments to **many operators as a unit** — the workforce-grouping facet AWS IAM User Group, IdC Group, and Google Group all provide but the portfolio has never had)

**Status:** PROPOSED

**Date:** 2026-07-08

**History:** PROPOSED 2026-07-08 (records the **operator-grouping model**: how a set of `admin_operators` becomes a named unit to which roles and tenant-assignments can be granted **once** instead of per-operator, and how that grouping stays **tenant-scoped**, **no-escalation-capped**, and **relationship-tracked for cascade removal**. Decisions D1–D6, **CHOSEN-PROPOSED** direction per the reasoning below; the ACCEPTED transition is a separate user-explicit-intent-gated task, mirroring the ADR-019/020/023/024/045 staged-child pattern. **No implementation in this task — decision record + impact scope + execution roadmap only. Self-ACCEPT prohibited.**)

**Decision driver:** The portfolio's IAM plane realizes operators (`admin_operators`), roles (`admin_operator_roles` / `admin_roles` → `admin_role_permissions`), and multi-tenant grants (`operator_tenant_assignment` + `permission_set_id`, ADR-020/024). RBAC evaluation is the **union of an operator's role→permission sets** — flat, no hierarchy, no inheritance (`rbac.md` § evaluation). **There is no primitive for grouping operators.** Every grant is per-`(operator, …)`: onboarding a five-person support squad to three tenants is 15 manual assignment rows, and a role change re-touches each. This is exactly the workforce-grouping facet that AWS **IAM User Group** / IdC **Group** and GCP **Google Group** provide (attach a policy/role to a group; membership inherits). The portfolio has faked it with per-individual rows, with two costs the exploration confirmed: (a) **N× manual repetition** on grant and on change; (b) **no unit** to reason about, audit, or revoke as a whole. Building operator grouping during implementation would silently bake a **new authorization-origination axis** (HARDSTOP-09): whether a group *inherits* (a new evaluation path through `PermissionEvaluator` + cache invalidation + the three `rbac.md` confinement axes) or merely *fans out* (a bulk-assign convenience that materializes ordinary assignment rows and never touches evaluation); whether groups are tenant-scoped; whether group grants are no-escalation-capped; and what happens to a member's access on group deletion / membership removal. Each is load-bearing and must not be chosen implicitly.

**Supersedes:** none. **Amends:** [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) § D6 (additive — the assignment/revocation lifecycle gains a *group-origin* driver: a fan-out assignment carries a group marker and is cascade-revoked when the group is deleted or the member leaves the group; ADR-020 bodies byte-unchanged). [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) § D2/D3 (additive — the no-escalation / within-tenant-confinement cap is **reused** as the cap on group grants; not re-decided).

**Related:** [ADR-MONO-020](ADR-MONO-020-operator-multitenant-assignment.md) (`operator_tenant_assignment` — the grant substrate group fan-out rides on), [ADR-MONO-024](ADR-MONO-024-tenant-admin-delegation.md) (within-tenant delegated-admin + no-escalation — the cap group grants inherit), [ADR-MONO-025](ADR-MONO-025-abac-data-scope-generalization.md) (`org_scope` data-scope — orthogonal; groups grant role/tenant scope, not data scope, in v1), [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (unified identity — group members are the SAME operator identities, gaining no new principal), `projects/iam-platform/specs/services/admin-service/rbac.md` (the evaluator + three confinement axes v1 deliberately does NOT extend), `rules/traits/multi-tenant.md` M1–M7 (row isolation groups must not weaken).

---

> **PROPOSED (staged, sibling: ADR-019/020/023/024/045).** This ADR records the **decision direction** (D1–D6) + the **invariants the chosen direction preserves** (M1–M7 untouched; `SUPER_ADMIN` net-zero; group grants ≤ what the granter holds; deny-default; fail-closed; ADR-023 plane separation) + a **zero-regression execution roadmap**. The roadmap steps are **PAUSED** until a separate user-explicit ACCEPT gate. **Self-ACCEPT prohibited.** No implementation in this task.

---

## 1. Context

### 1.1 What exists (verified 2026-07-08)

- **Operators** — `admin_operators` (physically separate namespace from consumer `accounts`; `token_type="admin"`).
- **Roles** — `admin_roles` + `admin_role_permissions` (role = explicit set of permission keys); `admin_operator_roles` (operator↔role, `tenant_id`-scoped). Evaluation = **union** of role→permission sets, flat.
- **Tenant grants** — `operator_tenant_assignment` (PK `(operator_id, tenant_id)`, multi-row) + `permission_set_id` (per-assignment role-set narrowing, ADR-020 D5) + `org_scope` (ABAC data-scope, ADR-025).
- **Confinement** — `AdminGrantScopeEvaluator` (`'*'` or exact tenant), no-escalation (ADR-024 D2/D3), BE-467/468 tenant-confinement, fail-closed, audited via `admin_actions`.

### 1.2 The gap — no grouping primitive

`account_group|user_group|operator_group|group_member` matches **zero** tables/entities/specs/ADRs. The only way to grant "these five operators, these three tenants, this role" is 15 individual rows, with no unit to revoke or audit as a whole. This is the **workforce-grouping** facet every major IAM provides:

| Concept | AWS | GCP | portfolio (this ADR) |
|---|---|---|---|
| Group of workforce identities | **IAM User Group** / IdC **Group** | **Google Group** / Cloud Identity Group | **Operator group** (`operator_group` of `admin_operators`) |
| Grant to the group | attach IAM Policy to group | bind IAM Role to group | assign role / tenant-assignment to group |
| Member gains grant | membership → policy applies | membership → role binding applies | membership → group grants **fan out** (D2) |

(Consumer `accounts` grouping — the Cognito User Pool Group / Identity Platform facet — is **explicitly out of scope**: consumer identity is a separate plane, ADR-032; operator grouping is the IAM-plane need.)

### 1.3 Why an ADR (HARDSTOP-09 + HARDSTOP-04)

Introducing a primitive that **originates authorization for many operators at once** is an IAM-plane change on the order of ADR-020/024. Implementing it without first deciding **(D1)** the primitive + owning service, **(D2)** inheritance-vs-fan-out (the difference between touching the evaluator/cache/confinement axes or not), **(D3)** tenant scope, **(D4)** the no-escalation cap, and **(D5)** the deletion/removal cascade would bake an authorization model silently (HARDSTOP-09). Because it **extends ADR-020's assignment lifecycle** and **reuses ADR-024's cap**, HARDSTOP-04 requires the extension be recorded additively, not applied implicitly.

---

## 2. Decision

Six axes. Each table's first row is **CHOSEN (PROPOSED direction)**.

### D1 — Primitive & owning service

| Option | Mechanics | Verdict |
|---|---|---|
| **A. First-class `operator_group` aggregate owned by admin-service** — `operator_group(id, tenant_id, name, …)` + `operator_group_member(group_id, operator_id)` | admin-service owns the grouping of its own `admin_operators`. Group is the unit of grant (D2) and revocation (D5). | **CHOSEN** — operators are admin-service-owned; grouping them belongs there. Mirrors IAM User Group / Google Group. No cross-service ownership question (unlike consumer grouping, which would be account-service's). |
| B. No primitive (status quo) | per-operator rows | Rejected — the exact N× gap. Recorded as the retreat if grouping is judged out of scope. |
| C. Universal principal group (operators **and** consumers) | one group spans both planes | Deferred — mixes IAM plane and Cognito-plane (ADR-032), doubles ownership + inheritance surface. Revisit only on a real consumer-grouping need; additive over D1-A. |

### D2 — Semantics: inheritance vs fan-out

| Option | Mechanics | Verdict |
|---|---|---|
| **A. Fan-out (bulk-assign convenience)** — granting a role/tenant-assignment to a group **materializes ordinary assignment rows** for each current member, tagged with a `group_origin` marker | `PermissionEvaluator`, cache, and the three `rbac.md` confinement axes are **untouched** — evaluation still reads flat per-operator rows. The group is a management convenience, not an evaluation path. | **CHOSEN (v1)** — least risk: zero change to the security-critical evaluation/cache/confinement logic. Delivers the N×→1 win immediately. |
| B. Inheritance (evaluation-time path) — membership is itself an authorization edge: `operator → groups → group_grants → permissions` computed at evaluation | Requires extending `PermissionEvaluator` (new union path) + cache invalidation on membership/group-grant change + re-reasoning the three confinement axes. | **Deferred to a follow-up ADR** — strictly more powerful (live membership change instantly re-authorizes without re-fan-out) but touches the most security-sensitive code. Not v1. |

### D3 — Tenant scope

| Option | Verdict |
|---|---|
| **A. Tenant-scoped (`operator_group.tenant_id`, `TenantScopeGuard` D2)** | **CHOSEN** — groups obey M1–M7 row isolation; a TENANT_ADMIN manages only their tenant's groups; SUPER_ADMIN (`'*'`) platform-wide. |
| B. Platform-global groups | Rejected v1 — breaks tenant confinement, needs a new scope model. |

### D4 — No-escalation cap

**CHOSEN:** a group grant is capped by the **granter's own holdings** (reuse ADR-024 D2/D3 — cannot grant a role/tenant the granter does not hold). Group membership never lets a member exceed what a direct grant could give. `SUPER_ADMIN` net-zero preserved.

### D5 — Lifecycle & cascade

**CHOSEN:** fan-out assignments carry a `group_origin` marker (relationship trail, sibling of ADR-045's cascade). Then:
- **Grant role/assignment to group** → fan out to all current members.
- **Add member** → fan out the group's current grants to the new member.
- **Remove member** → revoke that member's `group_origin`-tagged grants for this group (direct grants untouched).
- **Delete group** → cascade-revoke all `group_origin` assignments; members keep any independent direct grants.
- **Idempotence**: fan-out never duplicates an existing equal direct grant; removal never touches non-group-origin rows.

### D6 — Audit & events

**CHOSEN:** all group mutations require `@RequiresPermission("group.manage")` (new key) + an `admin_actions` row (deny-default, fail-closed). Group lifecycle events, if consumed downstream, ride the existing `admin_outbox` v2 with a new `topicFor` mapping (sibling pattern: `PartnershipEventPublisher`). v1 may be audit-only (no external event) if no consumer exists.

---

## 3. Invariants preserved

1. **M1–M7 row isolation** — groups are tenant-scoped (D3); fan-out rows inherit the member+tenant scoping.
2. **`SUPER_ADMIN` net-zero + no-escalation** — group grants ≤ granter's holdings (D4).
3. **Evaluation/cache/confinement untouched (v1)** — fan-out (D2-A) keeps `PermissionEvaluator` + the three `rbac.md` axes byte-unchanged.
4. **ADR-023 plane separation** — groups grant IAM authority (roles/assignments), never entitlement (`tenant_domain_subscription`); a member still needs the tenant's `entitled_domains`.
5. **Deny-default, fail-closed, audited** — `group.manage` gated, every mutation audited.

---

## 4. Execution roadmap (PAUSED until ACCEPT)

Each step is a separate dependency-ordered task spawned off the ACCEPTED main (sibling: ADR-045 § 3.4). **None may start before the user-explicit ACCEPT gate.**

1. **Spec** (`iam-platform`, backend) — `rbac.md` (`group.manage` key + seed matrix; note v1 fan-out, evaluation unchanged), `data-model.md` (group tables DDL + classification), `contracts/http/admin-api.md` (group CRUD + membership + group-grant endpoints), `features/operator-management.md`. **Contracts/specs precede code (Change Rule).**
2. **Backend** (`iam-platform`) — Flyway `operator_group` + `operator_group_member` (+ `group_origin` marker on fan-out rows); JPA entities + repository/adapter/port; `GroupAdminController` (`/api/admin/groups` CRUD, `/{id}/members`, `/{id}/grants`); fan-out/cascade service (D5) with transaction + outbox atomicity; `group.manage` seed; `@RequiresPermission` + `admin_actions`; `TenantScopeGuard` confinement. Tests: Unit (fan-out/cascade + audit-on-failure), Integration (Testcontainers + WireMock), Security (non-admin 403), Audit immutability, Fail-closed, no-escalation matrix.
3. **Frontend** (`platform-console`) — 「운영자 그룹」 screen (`src/features/operator-groups/`): CRUD, member management, group-level role/tenant assignment, no-escalation gating (grantable-roles convention). Consumes step-2 API.

Follow-up ADR (out of this scope): **inheritance semantics (D2-B)** — promote fan-out to an evaluation-time path if live-membership re-authorization is needed.

---

## 5. Consequences

- **Positive** — N×→1 operator onboarding/role-change; a unit to audit/revoke; the workforce-grouping parity AWS/GCP have; zero risk to the evaluation/cache/confinement core (v1 fan-out).
- **Negative / trade-offs** — fan-out is eventually-consistent with membership (a membership change requires a re-fan-out, not instant like inheritance); `group_origin` rows add lifecycle bookkeeping; inheritance power deferred.
- **Neutral** — consumer grouping (Cognito-plane) remains unaddressed by design (D1-C deferred).

**No implementation in this task.** PROPOSED → ACCEPTED is a separate user-explicit-gated step; **self-ACCEPT prohibited**.
