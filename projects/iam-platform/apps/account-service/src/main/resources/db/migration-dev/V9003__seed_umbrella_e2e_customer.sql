-- TASK-MONO-210 (ADR-MONO-024 § 3.3 step 3 — tenant-admin delegation federation-e2e
-- proof): seed a FOURTH demo customer `umbrella-corp`, dedicated to the
-- tenant-admin-delegation spec, with ONE ACTIVE subscription [finance].
--
-- WHY a dedicated tenant (not acme-corp / globex-corp): the TENANT_BILLING_ADMIN
-- leg of the proof mutates a subscription AT RUNTIME (suspend → resume) via the
-- `subscription.manage` admin RBAC surface. The federation-e2e suite runs
-- fullyParallel; acme-corp [finance,wms] and globex-corp [scm,erp] are asserted by
-- the MONO-154 entitlement-trust and MONO-158 A↔B switch specs, and initech-corp
-- is the MONO-207 plane-separation tenant — a runtime suspend on any of them would
-- race-break those specs. `umbrella-corp` is referenced by NO other spec, so the
-- suspend/resume cycle is fully isolated. This mirrors the MONO-207 initech-corp
-- discipline exactly.
--
-- WHY this is the entitlement side of the billing-admin leg: the
-- TENANT_BILLING_ADMIN (scoped to umbrella-corp via its admin_operator_roles grant
-- row) suspends/resumes `umbrella-corp/finance` (200) but is denied the same on
-- globex-corp (403 TENANT_SCOPE_DENIED — the guard rejects before delegating to
-- account-service, so globex is never touched). For the ALLOW (200) the
-- subscription must exist at account-service startup; hence this Flyway-dev seed.
--
-- WHY migration-dev V9000+ (not seed.sql, not production db/migration): identical
-- rationale to V9001 (globex) / V9002 (initech) — account-service's keystone
-- reverse-lookup only returns rows present at startup via Flyway, and dev-only
-- seeds live in the high V9000+ band to never collide with the (gapless)
-- production timeline. `db/migration-dev` is loaded ONLY under the e2e Flyway
-- locations (application-e2e.yml) — umbrella-corp never reaches a production DB.
--
-- The matching admin_db side (the TENANT_ADMIN / TENANT_BILLING_ADMIN operators
-- scoped to umbrella-corp + the dedicated target operator they manage) is seeded
-- in tests/federation-hardening-e2e/fixtures/seed.sql § 15.
--
-- FK (fk_tds_tenant): the tenant INSERT must precede the subscription INSERT.
-- INSERT IGNORE keeps it idempotent (mirrors V9001 / V9002).

INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('umbrella-corp', 'Umbrella Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
VALUES ('umbrella-corp', 'finance', 'ACTIVE', NOW(6), NOW(6));
