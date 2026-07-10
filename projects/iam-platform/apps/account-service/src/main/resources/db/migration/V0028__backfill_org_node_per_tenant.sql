-- TASK-BE-493 (ADR-MONO-047 § D7 — "migration: company=tenant → org_node + service-tenant"):
-- every existing tenant becomes ONE org_node (the company) owning ONE service-tenant.
--
-- THIS MIGRATION IS A BEHAVIOURAL NO-OP, BY CONSTRUCTION.
-- Each backfilled node carries an UNBOUNDED ceiling, which is the intersection IDENTITY
-- (EntitlementCeiling.intersect), so `effectiveCeiling(tenant)` after the backfill equals
-- what an ungrouped tenant (org_node_id = NULL) already resolved to before it. No
-- tenant_id changes, no isolation changes, no subscription changes. The proof — a
-- before/after snapshot of every tenant's resolved entitled_domains — lives in
-- OrgNodeBackfillIntegrationTest; the syntactic guards live in V0028BackfillMigrationShapeTest.
--
-- WHY UNBOUNDED AND NOT AN ENUMERATED SET OF TODAY'S DOMAINS:
-- writing ceiling_domains = 'wms,scm,erp,finance,…' would look equivalent today and silently
-- deny every legacy tenant the NEXT domain we add. UNBOUNDED means "no ceiling", not "all
-- domains currently known". Never enumerate here.
--
-- WHY org_node_id STAYS NULLABLE:
-- NULL = "ungrouped singleton" is a LEGAL, PERMANENT state (D7), not a migration remnant.
-- It is what lets this migration be skipped entirely (a lazy migration is legal), and the
-- dev-only seeds (migration-dev/V9xxx) create tenants AFTER this file runs, so they stay
-- ungrouped on purpose. Do NOT promote the column to NOT NULL.
--
-- ── The node id: deterministic, derived from tenant_id ────────────────────────────────
-- MySQL's UUID() is non-deterministic, so a node inserted with UUID() cannot be joined back
-- to the tenant it was created for. Joining on `name = display_name` is WRONG: display names
-- are NOT unique, and two same-named companies must get two distinct nodes (never one).
-- So the id is computed from the tenant_id, and BOTH statements below recompute the exact
-- same expression:
--
--   CONCAT('00000000-0000-5000-8000-', RIGHT(SHA2(CONCAT('adr047:org-node:', t.tenant_id), 256), 12))
--
-- It is a 36-char UUID-shaped opaque string (8-4-4-4-12). The `5` nibble marks it
-- name-derived rather than random; it is SHA-256-derived, not literally RFC-4122 v5 (SHA-1).
-- Nothing in the stack parses the version nibble — OrgNodeId only checks non-blank + length
-- ≤ 36 — so the shape is for human recognisability, not for a parser.
--
-- Consequences, all of them wanted:
--   * Idempotent by the WHERE clause AND by the id: a re-run recomputes the same id.
--   * The two statements are connection-independent (no temp table, no session variable),
--     so the integration test can execute this very file statement-by-statement and prove
--     the same thing Flyway will do at startup.
--   * If the two expressions are ever edited out of sync, the UPDATE sets an org_node_id
--     with no matching org_node row and fk_tenants_org_node aborts the migration LOUDLY.
--   * A 48-bit suffix collision between two tenants would violate the org_node PK and abort
--     the migration. It cannot silently merge two companies.

-- 1. One root company node per ungrouped tenant, named after the tenant's display_name
--    (the node is the COMPANY; the tenant is the SERVICE). parent_id NULL ⟹ depth 1.
INSERT INTO org_node (id, parent_id, name, ceiling_mode, ceiling_domains, depth, created_at, updated_at)
SELECT CONCAT('00000000-0000-5000-8000-', RIGHT(SHA2(CONCAT('adr047:org-node:', t.tenant_id), 256), 12)),
       NULL,
       t.display_name,
       'UNBOUNDED',
       '',
       1,
       NOW(6),
       NOW(6)
FROM tenants t
WHERE t.org_node_id IS NULL;

-- 2. Link each tenant to the node created for it above.
--    `updated_at` is deliberately NOT bumped: the tenant's own business state did not change,
--    only the grouping link it now hangs from.
--    A tenant grouped by hand BEFORE this migration ran is skipped by the same WHERE clause
--    and is never re-parented — merging or moving a company is a business decision, not a
--    migration's.
UPDATE tenants t
SET t.org_node_id = CONCAT('00000000-0000-5000-8000-', RIGHT(SHA2(CONCAT('adr047:org-node:', t.tenant_id), 256), 12))
WHERE t.org_node_id IS NULL;
