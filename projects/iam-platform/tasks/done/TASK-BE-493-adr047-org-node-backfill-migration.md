# Task ID

TASK-BE-493

# Title

ADR-047 § 4 step 4 — D7 migration: backfill one `org_node` per existing tenant (singleton company owning one service-tenant) + prove behavioural no-op

# Status

done

# Owner

backend

# Task Tags

- code
- test

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-BE-491` (the `org_node` DDL + `tenants.org_node_id` column must exist) and `TASK-BE-492` (so a backfilled node is administrable). Runs last in the ADR-047 roadmap.
- **후속**: none. This closes ADR-047 § 4.

---

# Goal

Execute D7: every existing tenant becomes **one `org_node` (the company) owning one service-tenant** — behaviourally identical to today — so that splitting a company into several isolated service-tenants later is a purely **additive** operation (create sibling tenants under the same node).

The migration must be a **provable no-op**: no `tenant_id` changes, no isolation change, no subscription change, and every operator's resolved `entitled_domains` and derived roles are byte-identical before and after.

---

# Scope

## In Scope

- **Flyway (account-service)** — next free version after BE-491's `V0027` (re-verify; likely `V0028`):
  - `V0028__backfill_org_node_per_tenant.sql` — for each row in `tenants` with `org_node_id IS NULL`: insert an `org_node` named after the tenant's `display_name` (parent `NULL`, depth 1) with an **unbounded** `entitlement_ceiling`, then set `tenants.org_node_id`. Idempotent (`WHERE org_node_id IS NULL`, re-runnable).
- Seed alignment: `acme-corp`, `globex-trading`, and the other seeded tenants each gain a same-named parent node.
- **Verification tests** (the substance of this task):
  - Integration (Testcontainers) — snapshot resolved `entitled_domains` for every seeded tenant **before** and **after** the migration; assert byte-equality.
  - Assert `effectiveCeiling(tenant)` is **unbounded** for every backfilled tenant (an unbounded ceiling intersects as identity — this is what makes it a no-op).
  - Assert no `tenants.tenant_id` value changed and no `tenant_domain_subscription` row changed.
  - Assert an operator's assume-tenant token (`entitled_domains` + derived `roles`) is unchanged post-migration.
  - Migration idempotence: run twice, second run inserts zero rows.

## Out of Scope

- Actually **splitting** any company into multiple service-tenants (a product decision, additive, later).
- Setting a real (narrowing) ceiling on any node — backfilled nodes are unbounded by construction. A ceiling that narrows anything would make this **not** a no-op.
- Console UI, RBAC, contracts (done in BE-490/492/PC-FE-237).

---

# Acceptance Criteria

- [ ] **AC-1**: After migration, every `tenants` row has a non-`NULL` `org_node_id`; each points to a distinct depth-1 node with no parent.
- [ ] **AC-2**: Every backfilled node's `entitlement_ceiling` is **unbounded** (the identity element), not an enumerated "all current domains" set — a domain added later must not be excluded.
- [ ] **AC-3**: Resolved `entitled_domains` per tenant is **byte-identical** pre/post migration (ordered list equality), proven by a before/after integration snapshot.
- [ ] **AC-4**: No `tenant_id`, no `tenant_domain_subscription` row, and no `admin_operator_roles` row is modified.
- [ ] **AC-5**: An assume-tenant token minted post-migration carries identical `entitled_domains` and derived `roles` to one minted pre-migration for the same operator/tenant.
- [ ] **AC-6**: Migration is idempotent — a second run is a zero-row no-op. `V0028` also tolerates a tenant that was manually grouped before the migration ran (`org_node_id IS NOT NULL` → skipped).
- [ ] **AC-7**: `./gradlew :projects:iam-platform:apps:account-service:test` GREEN; Testcontainers GREEN in **CI Linux** (authoritative).

---

# Related Specs

> `platform/entrypoint.md` Step 0 first.

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` § D7 + § 4 step 4 (**authority**)
- `projects/iam-platform/specs/services/account-service/data-model.md` (the `org_node` DDL landed by BE-490/491)
- `platform/testing-strategy.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-tenant-domain-subscriptions.md` (the resolution leg whose output must not move)

---

# Target Service

- `account-service`

# Architecture

Follow `projects/iam-platform/specs/services/account-service/architecture.md`.

---

# Implementation Notes

- D7 explicitly allows a **lazy** migration (`org_node_id` nullable ⟹ ungrouped is legal). This task chooses the eager backfill so the console tree is non-degenerate from day one — but the nullability and the `NULL`-safe code paths from BE-491 must remain, because they are what makes the backfill safe to run (and safe to *not* run).
- The node name comes from `tenants.display_name`, not `tenant_id` — the node is the *company*, the tenant is the *service*.
- Re-verify the next free Flyway version; BE-491 may have taken more than one.

---

# Edge Cases

- A tenant already grouped by hand before the migration runs → skipped (`WHERE org_node_id IS NULL`), not re-parented.
- Two tenants with the same `display_name` → two distinct nodes with the same name. Names are not unique; ids are. Do not dedupe — merging two companies is a business decision, not a migration's.
- Re-running on a DB where a backfilled node has since been given children → untouched (the `WHERE` clause never matches a grouped tenant).
- An empty `tenants` table (fresh dev DB) → zero rows, no error.
- Backfilled node given an enumerated ceiling instead of unbounded → the migration stops being a no-op the moment a 7th domain is added. AC-2 is the guard.

---

# Failure Scenarios

- Ceiling backfilled as `{wms, erp, scm, finance, ecommerce, fan}` (today's domain list) → a future domain is silently denied for every legacy tenant, and the bug surfaces months later as "why can't acme-corp subscribe to the new domain". Guard: AC-2.
- Migration made non-idempotent (no `WHERE org_node_id IS NULL`) → a re-run duplicates nodes and re-parents tenants. Guard: AC-6.
- `org_node_id` made NOT NULL "since everything is backfilled now" → the ungrouped-singleton path (D7's laziness escape hatch, and BE-491's net-zero tests) dies. Out of scope; do not.
- Verified by inspection rather than by a before/after snapshot → "behavioural no-op" is asserted, not proven. Guard: AC-3/AC-5 require the snapshot.
