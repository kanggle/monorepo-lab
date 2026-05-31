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
