# Task ID

TASK-BE-491

# Title

ADR-047 § 4 step 2a — account-service `org_node` authority: Flyway DDL (cycle + depth + child⊆parent), domain/JPA/repo, effective-ceiling resolution, entitled-domains intersection (D6 seam), subscription-activation ceiling gate, internal read API

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

- **선행 (prerequisite)**: `TASK-MONO-340` (ADR-047 ACCEPTED) → `TASK-BE-490` (specs/contracts merged). Implementing before BE-490 merges violates the Change Rule.
- **후속 (blocks)**: `TASK-BE-492` (admin-service consumes the internal read API defined here), `TASK-BE-493` (backfill migration sits on this DDL).

---

# Goal

Make **account-service the authority for the org-node tree**, because it owns the `tenants` table and D1 attaches `org_node_id` to `tenant`.

After this task:

1. `org_node` exists as a data-less grouping node (`id, parent_id, name, ceiling_mode, ceiling_domains, depth`), and `tenants.org_node_id` is a **nullable** FK. Existing rows are untouched — `NULL` means "ungrouped singleton, unbounded ceiling" (D7 net-zero).
   > The ADR D1 sketch writes a single `entitlement_ceiling` field; storage splits it into `ceiling_mode ∈ {UNBOUNDED, BOUNDED}` + `ceiling_domains` (CSV) so that `UNBOUNDED` (intersection identity) and `BOUNDED({})` (nothing permitted) — which are **opposites** — cannot be conflated at the storage layer. Same value, safer encoding; not a re-decision.
2. Writes are guarded: `parent_id` **cycle-check**, `max_depth = 5` (root = depth 1), and `child.entitlement_ceiling ⊆ parent.entitlement_ceiling`.
3. `effectiveCeiling(tenantId)` = the **intersection** of `entitlement_ceiling` over the chain `root → … → tenant.org_node`. A `NULL` `org_node_id` yields *unbounded* (no ceiling), **not** the empty set.
4. **D6 seam**: a dedicated `GET /internal/tenants/{tenantId}/entitled-domains` returns `ACTIVE subscriptions ∩ effectiveCeiling(tenant)`. This is the single point of enforcement. `TenantClaimTokenCustomizer` is **byte-unchanged** — it consumes `AccountServicePort.listEntitledDomains(tenantId)`, whose adapter is repointed to the new endpoint; `derive(E ∩ C) = derive(E) ∩ derive(C)` because ADR-035 derivation is per-domain. `GET /internal/tenant-domain-subscriptions` keeps returning **raw ACTIVE rows** for the console catalog and subscription management.
5. The **entitlement plane** is bounded at write time too: activating a `tenant_domain_subscription` for a domain outside the tenant's effective ceiling is rejected (422). The ceiling never mints an IAM role (ADR-023 plane separation holds).
6. Internal reads exist for admin-service: subtree tenant ids for a node, and a node's effective ceiling.

---

# Scope

## In Scope

- **Flyway (account-service)** — next free version is `V0027` (highest today = `V0026__account_outbox_v2.sql`; re-verify before writing):
  - `V0027__create_org_node.sql` — `org_node` table + `tenants.org_node_id` nullable FK + indexes (`parent_id`, `tenants.org_node_id`). Depth stored and maintained on write (cheap subtree/depth queries without recursive CTE reliance).
- **Domain** (`domain/orgnode/`): `OrgNode`, `OrgNodeId`, `EntitlementCeiling` (an ordered domain-key set with `intersect`, `isSubsetOf`, an explicit `unbounded()` sentinel distinct from `empty()`).
- **Persistence**: `OrgNodeJpaEntity`, `OrgNodeRepository` (port) + `OrgNodeRepositoryImpl`, `TenantJpaEntity.orgNodeId` (nullable).
- **Application**: `OrgNodeCommandService` (create / rename / re-parent / delete / set-ceiling — enforcing cycle, depth, `child ⊆ parent`, and "cannot delete a node with children or tenants"), `OrgNodeQueryService` (`effectiveCeiling(tenantId)`, `effectiveCeiling(nodeId)`, `subtreeTenantIds(nodeId)`, `tree()`).
- **D6 wiring**: new `GET /internal/tenants/{tenantId}/entitled-domains` = `ACTIVE ∩ effectiveCeiling`. auth-service `AccountServiceClient.doListEntitledDomains` repointed to it (URI + WireMock stubs only; `TenantClaimTokenCustomizer` and `AccountServicePort` signature untouched). The existing `GET /internal/tenant-domain-subscriptions` — the **subscription management + console-catalog read** — stays unnarrowed.
- **Write gate**: subscription activation rejects an out-of-ceiling domain (422, distinct error code).
- **Internal API** for admin-service, per BE-490's `admin-to-account.md`: `GET /internal/org-nodes` (tree), `GET /internal/org-nodes/{id}/tenants` (subtree tenant ids), `GET /internal/org-nodes/{id}/effective-ceiling`, plus the org-node command endpoints admin-service proxies.
- **Tests**:
  - Unit — ceiling intersection (incl. unbounded ≠ empty), `isSubsetOf`, cycle reject, depth-cap reject, delete-with-children reject.
  - Integration (Testcontainers) — `effectiveCeiling` over a 3-level chain; entitled-domains narrowed by an ancestor ceiling; `NULL org_node_id` → byte-identical to pre-change behaviour (net-zero); out-of-ceiling subscription activation → 422; Flyway migration idempotence.

## Out of Scope

- **auth-service logic**: no behavioural change. The ONLY permitted auth-service diff is repointing `AccountServiceClient.doListEntitledDomains`'s URI to the new endpoint (+ its WireMock stubs). `TenantClaimTokenCustomizer`, `OperatorRoleDerivation`, `applyCrossOrgCap` are byte-unchanged. If any of those needs editing, STOP — the intersection leaked to the wrong layer.
- admin-service RBAC / `ORG_ADMIN` / `org.manage` / `TenantScopeGuard` → `TASK-BE-492`.
- Console UI → `TASK-PC-FE-237`.
- Backfilling existing tenants into nodes → `TASK-BE-493` (this task only makes the column nullable and the code `NULL`-safe).
- Role-level ceiling (D3-B), grant-at-node (D2-C).

---

# Acceptance Criteria

- [ ] **AC-1**: `V0027` creates `org_node` + `tenants.org_node_id` (nullable FK). Migration is idempotent and re-runnable on a populated DB; no existing row is modified.
- [ ] **AC-2**: Cycle (`parent_id` chain revisits a node, incl. self-parent) → rejected at write with a distinct 422 code. Depth > 5 (root = 1) → rejected.
- [ ] **AC-3**: `child.entitlement_ceiling ⊆ parent.entitlement_ceiling` enforced on both create-with-parent and set-ceiling; a violating write is rejected 422 (and re-parenting that would break a descendant's subset property is rejected too).
- [ ] **AC-4**: `effectiveCeiling(tenantId)` = intersection over root→node chain. `org_node_id = NULL` → **unbounded**; a node with `{}` → **nothing permitted**. A unit test pins that these two are different.
- [ ] **AC-5**: `GET /internal/tenants/{tenantId}/entitled-domains` returns `ACTIVE ∩ effectiveCeiling`, order preserved from the ACTIVE list. For a `NULL`-node tenant the output is **byte-identical** to what `GET /internal/tenant-domain-subscriptions?tenantId=` returned before this change (net-zero regression test). That older endpoint still returns raw ACTIVE rows and is asserted unchanged.
- [ ] **AC-6**: Subscription activation for a domain outside the effective ceiling → 422 with a distinct code; the row is not written. Deactivation is always allowed (narrowing).
- [ ] **AC-7**: The auth-service diff is confined to `AccountServiceClient`'s request URI (+ test stubs); `TenantClaimTokenCustomizer`, `OperatorRoleDerivation` and `AccountServicePort` are byte-unchanged (`git diff` asserted). An integration test proves an assume-tenant token's `entitled_domains` + derived `roles` are narrowed by an **ancestor** node's ceiling with no token-issuance logic change.
- [ ] **AC-8**: `GET /internal/org-nodes/{id}/tenants` returns exactly the subtree's tenant ids (self + descendants); `/effective-ceiling` returns the chain intersection. Both are `/internal/**` (client_credentials Bearer, per ADR-005 step 4 / BE-487).
- [ ] **AC-9**: `./gradlew :projects:iam-platform:apps:account-service:test` GREEN; Testcontainers integration suite GREEN in CI Linux (local Windows npipe is flaky and not authoritative).

---

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0 — `projects/iam-platform/PROJECT.md` → `rules/common.md` → declared domain/trait rule files. `rules/traits/multi-tenant.md` M1–M7 is load-bearing.

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` D1/D2/D3/D4/D6/D7 (**authority**)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the ceiling bounds entitlement, never mints IAM roles)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 (per-domain derivation — why the seam placement is sound)
- `projects/iam-platform/specs/services/account-service/{architecture,data-model}.md` (Layered Architecture + explicit state machine)
- `projects/iam-platform/specs/features/multi-tenancy.md`
- `platform/testing-strategy.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md` (effective entitled-domains)
- `projects/iam-platform/specs/contracts/http/internal/admin-to-account.md` (subtree + effective-ceiling reads)

---

# Target Service

- `account-service`

# Architecture

Follow `projects/iam-platform/specs/services/account-service/architecture.md` (Layered Architecture + explicit state machine).

---

# Implementation Notes

- **Do not touch `TenantClaimTokenCustomizer`.** The ceiling is applied before auth-service ever sees the list. A diff there means the seam moved.
- The **subscription management read** and the **entitlement resolution read** are different call sites even though both start from `tenant_domain_subscription`. They now get **different endpoints** rather than one endpoint whose meaning flips on a query parameter — narrowing the management read would hide rows from the admin who must manage them, and a param-dependent security semantic is a footgun.
- `GET /internal/tenant-domain-subscriptions` currently serves both admin-service (catalog, no `tenantId`) and auth-service (keystone, with `tenantId`). Only the latter moves.
- `EntitlementCeiling.unbounded()` must not be modelled as "the set of all known domains" — a new domain added later would then be silently excluded from every legacy node. Model it as a distinct case that intersects as identity.
- Depth is maintained on write (`parent.depth + 1`); re-parenting must recompute the moved subtree's depths and re-assert the cap.
- Re-verify the next free Flyway version before writing `V0027` — another merged task may have taken it.

---

# Edge Cases

- Self-parent (`parent_id = id`) and long cycles (A→B→C→A) — both rejected.
- Re-parenting a node under its own descendant — a cycle in disguise; rejected.
- Re-parenting under a parent whose ceiling is narrower than the moved node's (or any descendant's) — rejected, else the subset invariant breaks retroactively.
- Deleting a node that still has children or tenants — rejected (would orphan the FK / strand tenants).
- `org_node_id = NULL` (ungrouped) — unbounded, byte-identical legacy behaviour. **The most important test.**
- A node with an empty `{}` ceiling — the tenant may resolve **zero** entitled domains → the domain gateway 403s. That is the intended fail-closed direction, not a bug.
- Account-service down at token issuance — auth-service's existing fail-soft omits `entitled_domains` entirely. Failure never *widens* reach (the claim's absence 403s at the gateway).

---

# Failure Scenarios

- Intersection applied to the management read → the console's subscription screen appears to lose rows; an operator "re-subscribes" and nothing changes. Guard: AC-5 scopes the change to the resolution leg only.
- `unbounded()` implemented as `allKnownDomains()` → adding a 7th domain later silently excludes it for every ungrouped tenant. Guard: AC-4 unit test pins `unbounded ≠ {}` and identity-intersection.
- Depth cap enforced on create but not on re-parent → a deep chain assembles by moving subtrees. Guard: AC-2 covers both paths.
- Ceiling enforced on subscription **write** but not on **resolution** (or vice versa) → a pre-existing out-of-ceiling subscription keeps resolving. Both gates are required; AC-5 + AC-6.
- The migration adds a NOT NULL / defaulted `org_node_id` → every existing tenant is silently grouped/locked. Guard: AC-1 nullable + AC-5 net-zero test.
- Testcontainers verified only on local Windows → not authoritative (npipe flake). Guard: AC-9 requires CI Linux GREEN.
