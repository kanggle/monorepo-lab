-- =============================================================================
-- REPEATABLE (R__) — this file was `V9005__…` until TASK-MONO-524 (2026-08-12).
-- =============================================================================
-- The V9000+ band was chosen (TASK-MONO-207) to stop dev seeds COLLIDING with
-- production version numbers. It did that, and in exchange it poisoned ORDERING:
-- once 9001 is the highest APPLIED version, every later production migration
-- resolves BELOW it = out-of-order, which Flyway rejects by default. auth_db hit
-- exactly that when V0032 landed (2026-08-11) and crash-looped on every host
-- with an existing volume. CI never saw it — CI always starts from a fresh
-- volume, where everything applies in order.
--
-- R__ has neither problem: a repeatable carries NO version (nothing to collide
-- with, nothing to be out of order against) and always runs AFTER every
-- versioned migration — which is the order seed data wants anyway.
--
-- Editing rules this buys, all three load-bearing:
--   · it re-runs whenever its checksum changes ⇒ every statement MUST stay
--     idempotent (this file: INSERT IGNORE only)
--   · repeatables run in DESCRIPTION order ⇒ the `NN_` prefix is the ordering
--     contract between the seeds in this directory. Do not drop it.
--   · never rename an already-applied R__ file — the description is its
--     identity, and a rename reads as "applied migration not resolved locally",
--     which Flyway does NOT tolerate. (A missing VERSIONED file it *does*
--     tolerate, silently, as `future` — that asymmetry is measured in the doc.)
--
-- Rationale in full + how to recover an already-poisoned database:
--   projects/iam-platform/docs/flyway-dev-seed-migrations.md
-- =============================================================================
-- !!! DEV/DEMO ONLY — loaded via spring.flyway.locations ONLY under the `e2e`
-- profile (application-e2e.yml). application.yml pins production to
-- db/migration alone, so nothing here can reach a production account_db. !!!
--
-- TASK-BE-571 — the portfolio-demo tenant + the demo consumer identities.
--
-- 1. `demo-corp` — ONE tenant subscribed to ALL FIVE console domains.
--    At assume-tenant, OperatorRoleDerivation.fromEntitledDomains maps the
--    selected tenant's ACTIVE subscriptions to operator roles, so this single
--    tenant yields ECOMMERCE_OPERATOR + WMS_OPERATOR (+ the granular wms set) +
--    SCM_OPERATOR + ERP_OPERATOR + FINANCE_OPERATOR in one token. Every one of
--    those five domains' gateways AND services run
--    `TenantClaimValidator...trustEntitledDomains()`, so a tenant_id=demo-corp
--    token is admitted by all of them on the entitlement leg — the operator does
--    NOT have to flip the tenant switcher to reach a second set of domains
--    (which is what the existing acme-corp[finance,wms] ↔ globex-corp[scm,erp]
--    demo pair requires).
--
--    WHY FLYWAY AND NOT A RUNTIME INSERT: account-service's per-tenant keystone
--    query does not return rows inserted AFTER startup — TASK-MONO-160 hit
--    exactly this (a runtime-seeded globex-corp produced an empty
--    entitled_domains while the Flyway-seeded acme-corp worked through the
--    identical query). Seeding here uses the same path acme-corp (V0020) uses.
--
--    Version band: V9000+ per TASK-MONO-207 — the production timeline in this
--    service is contiguous, so dev seeds live in a band it will never reach.
--
-- 2. The two CONSUMER identities/accounts (ecommerce + fan-platform), linked
--    through the central `identities` registry per ADR-MONO-034 U1-A/U4: an
--    identity row per (tenant, email) and accounts.identity_id populated, so the
--    demo identities are born in the unified shape rather than as orphans the
--    ADR-034 backfill would later have to adopt.
--
--    NO account row is created for the console operator credential
--    (tenant `iam`), and that is deliberate, not an omission:
--      * `identities` and `accounts` both carry an FK to `tenants`, and there is
--        NO `iam` tenants row — `iam` is a RESERVED slug (admin-service's
--        reserved-word set, V0024), not a customer tenant. Creating one to
--        satisfy the FK would register the IdP's own operational slug as a
--        tenant and surface it in the console's tenant list.
--      * Every operator seeded in this repo today (e2e-super-admin,
--        acme-operator, multi-operator) likewise has a credential + an
--        admin_operators row and NO accounts row. The operator plane is a
--        separate store — that separation is the very thing ADR-MONO-034 §1.1
--        documents and U2 defers consolidating to step 4.
--      * Nothing in the console path reads an accounts row: the operator is
--        resolved by `sub` → admin_operators.oidc_subject (TASK-MONO-299).
--
-- Idempotent: INSERT IGNORE throughout. FK order: tenant → subscriptions;
-- identities → accounts.

-- ---------------------------------------------------------------------------
-- 1. demo-corp + all five domain subscriptions
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('demo-corp', 'Demo Corporation', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));

INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
VALUES ('demo-corp', 'ecommerce', 'ACTIVE', NOW(6), NOW(6)),
       ('demo-corp', 'wms',       'ACTIVE', NOW(6), NOW(6)),
       ('demo-corp', 'scm',       'ACTIVE', NOW(6), NOW(6)),
       ('demo-corp', 'erp',       'ACTIVE', NOW(6), NOW(6)),
       ('demo-corp', 'finance',   'ACTIVE', NOW(6), NOW(6));

-- ---------------------------------------------------------------------------
-- 2. Consumer identities (ADR-MONO-034 U1-A) — one per (tenant, email).
--    identity_id is a NEW UUID, deliberately NOT reusing the account id.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('0199de71-0000-7000-8000-00000000ec01', 'ecommerce',    'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de71-0000-7000-8000-00000000fa02', 'fan-platform', 'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 3. Consumer accounts — id MUST equal the matching credentials.account_id
--    (auth-service migration-dev V9001), because that value is the OIDC `sub`.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
VALUES
    ('0199de70-0000-7000-8000-00000000ec01', '0199de71-0000-7000-8000-00000000ec01',
     'ecommerce',    'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de70-0000-7000-8000-00000000fa02', '0199de71-0000-7000-8000-00000000fa02',
     'fan-platform', 'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);
