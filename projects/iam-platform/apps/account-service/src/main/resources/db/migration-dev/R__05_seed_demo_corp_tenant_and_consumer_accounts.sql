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
-- 2. The demo CONSUMER — since TASK-MONO-744 ONE consumer-POOL account
--    (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀), linked through the
--    central `identities` registry per ADR-MONO-034 U1-A/U4 (identity row +
--    accounts.identity_id populated, so it is born in the unified shape).
--
--    WHAT CHANGED (TASK-MONO-744, 2026-10-02 UTC)
--    ---------------------------------------------------------------------------
--    Until then this file seeded TWO site accounts for demo@demo.com — `…ec01` in
--    `ecommerce` and `…fa02` in `fan-platform` — so the store and the fan web
--    were two different `sub`s and moving between them asked for the password
--    again. Now there is ONE account in tenant `consumer-pool` plus an ACTIVE
--    membership of BOTH sites, i.e. the demo account is PRE-JOINED (AC-1, the
--    implementer default R3): fan → store passes with no password and no
--    first-visit consent screen, and both sites' tokens carry `sub = …ec01`.
--    (The owner may flip this to "the demo also walks the consent flow" by
--    dropping one membership statement below.)
--
--    WHY `…ec01` SURVIVES (and not `…fa02`): infra/demo/seed/seed-ecommerce.sh
--    writes the store profile directly with that literal id (user-service has
--    no profile-create endpoint — TASK-BE-575), whereas seed-fan.sh already
--    trusts the token's `sub` over its default. Keeping ec01 keeps the one
--    hard-coded consumer of the id valid; seed-fan.sh's default was moved to it.
--
--    The pool account carries NO stored role: the seed roles (store CUSTOMER,
--    fan FAN) are added per site at issuance (RoleSeedPolicy ∪
--    consumer_site_roles, TenantClaimTokenCustomizer#customizeForPoolPrincipal),
--    and account_roles cannot hold a pool account at all (its composite FK
--    (tenant_id, account_id) → accounts(tenant_id, id) would need tenant =
--    the site). consented_at = the account's created_at, the value TASK-BE-618's
--    legacy mover writes for the same shape.
--
--    🔴 EXISTING LOCAL VOLUMES KEEP THE OLD SHAPE. Every statement here is
--    INSERT IGNORE and this file never deletes: on a volume that already holds
--    `…ec01` as an `ecommerce` account, the pool INSERT below collides on the
--    primary key and is ignored, `…fa02` stays, and auth-service's pool
--    credential collides on credentials.account_id's global unique index and is
--    ignored too — so such a volume keeps working exactly as before (two site
--    accounts, re-login between sites), it just does not get the new shape.
--    The membership statements are guarded on `tenant_id = 'consumer-pool'` so
--    they write nothing onto a still-site account (a membership row there would
--    make TASK-BE-618's mover — a plain INSERT — fail for that account).
--    Deliberately NO DELETE: account_status_history is append-only (DB
--    triggers, V0004) and FK cascades from accounts could make this repeatable
--    seed fail on someone's volume. To get the pool shape locally, start from
--    a fresh account_db + auth_db volume. The demo server always does: the AMI
--    bakes images only and seeds run at first boot on empty volumes.
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
-- identities → accounts → consumer_site_memberships. `consumer-pool` itself is
-- seeded by the production migration V0029, which runs before any repeatable.

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
-- 2. The consumer identity (ADR-MONO-034 U1-A) — in the pool, like its account
--    (multi-tenancy.md § 소비자 계정 풀 § 3: the account's identities row lives in
--    `consumer-pool`). identity_id is a NEW UUID, deliberately NOT reusing the
--    account id.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('0199de71-0000-7000-8000-00000000ec01', 'consumer-pool', 'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 3. The consumer pool account — id MUST equal the pool credential's
--    credentials.account_id (auth-service migration-dev R__01), because that
--    value is the OIDC `sub` on BOTH consumer sites.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
VALUES
    ('0199de70-0000-7000-8000-00000000ec01', '0199de71-0000-7000-8000-00000000ec01',
     'consumer-pool', 'demo@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 4. Pre-joined to both consumer sites (AC-1 — R3). No row for a site ⇒ the
--    first-visit consent screen there (TASK-BE-616); both rows ⇒ none.
--    Guarded on the account really being a pool account — see the header's
--    local-volume note.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at)
SELECT id, 'fan-platform', 'ACTIVE', created_at
  FROM accounts
 WHERE id = '0199de70-0000-7000-8000-00000000ec01'
   AND tenant_id = 'consumer-pool';

INSERT IGNORE INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at)
SELECT id, 'ecommerce', 'ACTIVE', created_at
  FROM accounts
 WHERE id = '0199de70-0000-7000-8000-00000000ec01'
   AND tenant_id = 'consumer-pool';
