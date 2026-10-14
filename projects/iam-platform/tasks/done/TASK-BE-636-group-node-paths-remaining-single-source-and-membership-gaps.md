# Task ID

TASK-BE-636

# Title

Gaps TASK-BE-633 left: **tenant-assignment fan-out still has a single owner** (revoking G1 drops a tenant that G2 also gives) · group revoke can **leave domain permission set rows behind with no membership** · a node grant writes its row to the **home tenant, which may not be a membership** (784 D-1)

# Status

done

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

- [x] **AC-0**: the red results of the three ITs (file:line), plus the design choice for each and why.
- [x] **AC-1**: G1 and G2 both give U → G1 revoked → U kept → G2 revoked → U removed (control group: a direct membership survives).
- [x] **AC-2**: when a person holding only domain set rows in U has their group TENANT_ASSIGNMENT revoked, no row is left without a membership (kept, or removed together — per the decision).
- [x] **AC-3**: after a node grant, D-1 holds: the row's tenant ∈ memberships.

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

---

# Implementation Record

## AC-0 red measurement (CI, 2026-10-14 UTC — #4357 first commit = IT only + old code)

Integration lane «iam A», run 38571399837 / job 115877338776, `GroupNodeMembershipSourceIntegrationTest` (12 methods): **9 FAILED · 3 PASSED** — as predicted from the code. **All 9 reds stopped at the defect assertion** (0 SQL errors, 0 fixture errors). The 3 PASSED are the controls. (The implementing agent's report said «14 methods, 10 red, 4 controls»; the class has 12 `@Test` and the lane log lists 12 — counted from the log, not the report.)

| # | Methods (red) | Assertion that failed | Old-code path |
|---|---|---|---|
| ① | TENANT_ASSIGNMENT revoke · member leave · group deletion (×3) | «G2 still gives U — … must not take it»: actual 0 | `GroupFanOutService.java:218-221` (G2 fan-out `existsById` skip → G1 owns the only row) · then `:149` / `:114-116` / `:128-129` delete by `group_origin` |
| ② | revoke · leave · group deletion · **company exit** (×4) | «no domain permission set row outside the person's memberships»: expected 0, was 1 | `GroupFanOutService.java:180` (keep rule counts role rows only) · `OperatorCompanyExitCascade.java:73-81` · `:106-112` |
| ③ | decision-neutral invariant · refuse (×2) | «no non-'*' role row outside its memberships» / «no node row written»: expected 0, was 1 | `OrgNodeAdminUseCase.java:204-206` (row at `target.getTenantId()` = home) |

Controls PASSED: ① direct + G1 · ① edge direct + G1 + G2 · ③ home is a membership → 201.

Found while writing the ITs (corrections to the ticket text):
- **② has a fourth path the ticket did not list**: «이 회사에서 내보내기». The exit cascade's keep rule also counts role rows only, so a person leaving company H loses the G-given membership in U while their set row in U stays. It is fixed with the other three.
- **① is narrower than written.** «Direct + group» only arises when the direct membership came first (the group fan-out then skips and never owns it — survives today, the two controls). Group-first then direct is refused 409 `ASSIGNMENT_ALREADY_EXISTS` (`ManageOperatorAssignmentUseCase.java:95-97`). So the only real loss is group-vs-group.

## Design choice for ① — re-ownership at revoke time, no schema change

When a group source is revoked, the row is first handed to another group G′ of the member that still carries TENANT_ASSIGNMENT(U). Only if there is none does the ② rule apply (keep as direct if role **or** set rows remain), and only then is the row deleted.
- Rejected: BE-633's source-in-PK shape. A membership row carries per-`(operator, tenant)` attributes (`org_scope`, `permission_set_id`) that the assume gate, `TenantScopeResolver`, `QueryTenantScopeGate`, the console registry and the org-scope PUT all read by that key. Rows per source would make every reader merge attributes and the PUT ambiguous. Role rows carry no such attributes, which is why the PK change was cheap there.
- No migration: group sources are derivable exactly from templates × members, so re-ownership at revoke time also repairs existing v1 rows (Failure Scenario 1 does not apply).
- A group TENANT_ASSIGNMENT template carries no membership attributes (`OperatorGroupGrantJpaEntity` = tenant · grantedBy · grantedAt; fan-out always writes `org_scope = NULL`, `permission_set_id = NULL`). So re-ownership changes `group_origin` only and **keeps** `org_scope` / `permission_set_id` — those can differ only when an admin set them on the membership later, and resetting them would silently widen scope.
- G′ = the lowest group id. Audit `detail` gains `reownedToGroup=N` (and `keptAsDirect=N`), each only when > 0 — the usual detail string is byte-unchanged.

## ② and ③ fixes

- ② The keep-as-direct rule counts role rows **or** domain set rows, in `GroupFanOutService.settleGroupMemberships` and `OperatorCompanyExitCascade.keepsGrantsIn` (renamed from `keepsRolesIn`). On member removal and group deletion, that group's own `GROUP:<G>` set rows do not count (they are deleted right after); on a TENANT_ASSIGNMENT revoke they stay, so they count.
- ③ `OrgNodeAdminUseCase.requireHomeIsMembership` after the ceiling check → 422 `ORG_ADMIN_GRANT_TARGET_NOT_MEMBER`, nothing written, no success audit row. Order 404 → 403 ≤-own → 422 ceiling → 422 this. A `'*'` target is exempt from this check, but its node grant was already impossible before BE-636 (`ck_admin_operator_roles_node_not_platform`) — clause added to the contract.

## AC-1 · AC-2 · AC-3 green (CI, 2026-10-14 UTC — #4357 fix commit `4f011b8f7`)

Integration lane «iam A», run 38575551475 / job 115891686199, read from the job log (not the check badge):
- `GroupNodeMembershipSourceIntegrationTest` **12 PASSED · 0 FAILED · 0 SKIPPED** (the 9 reds + 3 controls).
- Neighbours unchanged: `AdminRoleSourceIntegrationTest` 10 · `OrgNodeAdminIntegrationTest` 15 · `OperatorMembershipWriteAxisIntegrationTest` 5 · `GroupAdminIntegrationTest` 5 PASSED. 0 FAILED anywhere in the lane · `BUILD SUCCESSFUL`.
- Unit (local): `:admin-service:test` rc=0, 1330 tests, 0 failures. Guards (staged): the three required task guards · both error-code registries rc=0.
- ⚪ Production: not yet applied — rides the next AMI bake (owner decision 1). No migration, so there is no pre-deploy query. Live check → `TASK-MONO-672` run order.

## Close (2026-10-14 UTC)

- #4357 → `2d3a0661d` (squash, merged 14:12:06Z). `origin/main` tip = that commit.
- 0 failing checks at merge: 34 pass · 35 skipping. The four required checks passed (`changes` · INDEX queue drift · Task ID collision · Walkthrough ledger drift), as did «iam A» and «iam B».
- Measurement order: first commit (IT only + old code) **9 FAILED at the defect assertion + 3 controls PASSED** → fix commit **12 PASSED** (lane log, job 115891686199).
- Live check → `TASK-MONO-672` § Bake contents / Order step 5 (added in this close).
