-- TASK-MONO-207: renumbered V0021 → V9001. The e2e Flyway timeline merges
-- db/migration (production) + db/migration-dev (this dir) under one version
-- sequence. Production grew a real V0021 (tenant_domain_subscription status
-- CHECK, TASK-BE-341 / ADR-MONO-023 step 1) that COLLIDED with this file's old
-- V0021 → Flyway "more than one migration with version 21" → account-service
-- failed to start under the e2e profile (federation-e2e RED). Dev-only seeds now
-- live in a high V9000+ band the (contiguous) production timeline will never
-- reach, permanently decoupling them. (admin-service dev seeds interleave into
-- production gaps — V0014/23/28 — but account-service production is gapless, so
-- the high band is the robust choice here.)
--
-- TASK-MONO-160 (fixes TASK-MONO-158 ADR-MONO-020 D4 federation-e2e B-side):
-- Seed a SECOND demo customer `globex-corp` with COMPLEMENTARY subscriptions
-- [scm, erp] so the active-tenant switcher A↔B proof can flip the entitled set
-- (acme-corp [finance,wms] ↔ globex-corp [scm,erp]).
--
-- WHY migration-dev (not the e2e seed.sql, not production db/migration):
--   The federation-e2e originally seeded globex via the runtime seed.sql
--   (applied AFTER account-service had already started). account-service's
--   per-tenant keystone query (findByStatusAndTenantId) did NOT return those
--   post-startup externally-inserted rows for globex, while the Flyway-inserted
--   acme-corp (V0020) WORKED through the identical query — so the assume-tenant
--   entitled_domains for globex came back empty and scm/erp stayed gate-rejected.
--   Seeding globex via Flyway (the EXACT path acme-corp V0020 uses) makes it
--   present at account-service startup, so the keystone returns [scm,erp]
--   exactly as it does for acme-corp.
--   `db/migration-dev` is loaded ONLY under the `e2e` Flyway locations
--   (application-e2e.yml) — globex-corp never reaches a production DB (it is a
--   demo customer for the A↔B switch proof, unlike acme-corp which is the first
--   real customer in production db/migration V0020).
--
-- FK (fk_tds_tenant): the tenant INSERT must precede the subscription INSERTs.
-- INSERT IGNORE keeps it idempotent (mirrors V0014–V0020).

INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('globex-corp', 'Globex Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
VALUES ('globex-corp', 'scm', 'ACTIVE', NOW(6), NOW(6)),
       ('globex-corp', 'erp', 'ACTIVE', NOW(6), NOW(6));
