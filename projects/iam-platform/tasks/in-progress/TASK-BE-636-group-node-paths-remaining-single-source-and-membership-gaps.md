# Task ID

TASK-BE-636

# Title

Gaps TASK-BE-633 left: **tenant-assignment fan-out still has a single owner** (revoking G1 drops a tenant that G2 also gives) · group revoke can **leave domain permission set rows behind with no membership** · a node grant writes its row to the **home tenant, which may not be a membership** (784 D-1)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Opus (fan-out · source rows · membership invariant)
> Source: `TASK-BE-633` implementation report (2026-10-14 UTC), «not fixed — needs a follow-up ticket» items ①②③. BE-633 fixed only the **role** rows (`admin_operator_roles`) per source. The three below are the remaining paths that share the same shape of problem.

# Dependency Markers

- **Prerequisite**: `TASK-BE-633` (merged `ee7738b02` — role per-source rows + V0053), `TASK-BE-631` (domain set per-source rows).

# Goal

1. **Tenant assignment (`operator_tenant_assignment`) fan-out still has a single owner.**
   - G1 and G2 both give tenant U. Revoking G1's TENANT_ASSIGNMENT removes U from the member, even though G2 still gives U.
   - The exception is when a role row is left behind: then BE-633 keeps U as a direct membership.
   - Same shape as the role case before V0053: `group_origin` is a single column.
2. **A group TENANT_ASSIGNMENT revoke can leave domain permission set rows behind with no membership.**
   - BE-633's «keep as direct membership» rule looks only at **role rows** (`admin_operator_roles`).
   - If the person holds only domain set rows (`operator_domain_set_grant`) in U, the membership is deleted and the set rows stay with no membership. This is a sibling of 784's D-1 invariant («a non-`'*'` row's tenant ∈ memberships»).
3. **A node (org-node) grant writes its row to the target's home tenant.**
   - Since 784, the home is not a membership (S2), so a node grant can create a row in a tenant that is not one of the person's memberships.
   - This is a D-1 gap outside the group paths.

# Scope

## In Scope

- AC-0 measurement (red first): write an IT for each of ①②③ and confirm it is red against the current code.
- ① Per-source membership rows, or an equivalent (decide whether to reuse the BE-633 design in AC-0), plus a migration (re-fan-out of existing data, same principle as V0053).
- ② Make the «keep as direct membership» rule also count domain set rows. Or delete the set rows together with the membership — only after an owner decision; the default is to follow the BE-633 decision and keep.
- ③ Decide the node grant's tenant: refuse a node grant whose target is outside its memberships, or add a membership. Decide in AC-0.

## Out of Scope

- Domain set per-source rows themselves (done in BE-631) · role per-source rows (done in BE-633).

# Acceptance Criteria

- [ ] **AC-0**: the red results of the three ITs (file:line), plus the design choice for each and why.
- [ ] **AC-1**: G1 and G2 both give U → G1 revoked → U kept → G2 revoked → U removed (control group: a direct membership survives).
- [ ] **AC-2**: when a person holding only domain set rows in U has their group TENANT_ASSIGNMENT revoked, no row is left without a membership (kept, or removed together — per the decision).
- [ ] **AC-3**: after a node grant, D-1 holds: the row's tenant ∈ memberships.

# Related Specs

- `projects/iam-platform/specs/services/admin-service/data-model.md` (§ operator_tenant_assignment · § V0053 · § operator_domain_set_grant)
- `docs/adr/ADR-MONO-082-…md` D-1 · `docs/adr/ADR-MONO-046-operator-group-model.md` D2 · D5

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § groups · § org-nodes (no change to the response fields is the default)

# Edge Cases

- U is given by a direct grant + G1 + G2 → after G1 and G2 are revoked, the direct one stays.
- A `'*'` operator has no membership; it is out of scope for ① and ③.

# Failure Scenarios

1. Changing the PK alone without migrating existing data — rows that v1 skipped are never recorded (the same lesson as V0053).
2. Fixing only ① and leaving ② — the membership survives but the orphaned set rows in other tenants remain.

---

# Owner decisions (2026-10-14 UTC)

1. **BE-636 ships in the next re-bake.** It rides in the same AMI as BE-631 · 632 · 633 · 634 · 635. After merge, add it to the `TASK-MONO-672` run order (§ Bake contents) together with the checks for the window.
2. **③ — refuse.** When the target operator's home tenant (`admin_operators.tenant_id`) is not one of their memberships, a node grant is refused with **422** and writes no row. The row is not moved to another membership, and no membership is added in the home tenant. Adding one would quietly re-admit the person to a company that removed them.
   - The usual case, where the home tenant is a membership, stays byte-unchanged.
   - The cost: such a person cannot become a node admin while their home tenant stays the same.
   - New error code → `admin-api.md` § POST …/org-nodes/{id}/admins is updated before the code.
3. **②** follows the default in § Scope (BE-633 owner decision 2): keep the membership as a direct membership while domain set rows remain. No separate question was needed.
