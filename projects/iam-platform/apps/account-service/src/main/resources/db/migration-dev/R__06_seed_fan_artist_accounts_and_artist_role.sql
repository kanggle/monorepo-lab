-- =============================================================================
-- REPEATABLE (R__) — this file was `V9006__…` until TASK-MONO-524 (2026-08-12).
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
-- TASK-MONO-512 (ADR-MONO-059 ACCEPTED — A) — the demo artists' login accounts
-- and the `ARTIST` role grant that makes ARTIST_POST authoring reachable.
--
-- WHAT WAS MISSING, PRECISELY
-- ---------------------------------------------------------------------------
-- TASK-FAN-BE-045 landed `artists.account_id NOT NULL` and backfilled the three
-- demo rows with the IDENTITY value (`account_id := id`). That backfill made the
-- feed join well-formed — `posts.author_account_id` ⋈ `follows.artist_account_id`
-- both carry the artist entity id — but it did NOT make the value an IAM subject:
-- no `accounts` row, no `credentials` row, so nobody could log in as an artist,
-- and `PublishPostUseCase`'s `ARTIST` gate had no caller who could pass it. That
-- remaining half is this ticket.
--
-- WHY THE ACCOUNT IDs EQUAL THE ARTIST ENTITY IDs (this is the point, not a hack)
-- ---------------------------------------------------------------------------
-- The ids below are byte-identical to `artists.id` / `artists.account_id` in
-- `infra/demo/seed/seed-fan.sh` (ARTIST_A/B/C). We provision the IAM account AT
-- that id rather than re-pointing `artists.account_id` at a fresh UUID, because
-- re-pointing would strand every already-seeded demo database: `follows` rows
-- (created through the API on earlier runs) and the existing direct-DB
-- `ARTIST_POST` rows both carry the old value, the seed's artist INSERTs are
-- `WHERE NOT EXISTS` so they would never update it, and the feed join would
-- silently empty out on exactly the stacks that have been demoed most.
-- Provisioning at the same id instead makes FAN-BE-045's backfill RETROACTIVELY
-- TRUE — the identity value becomes a real subject — and no other table moves.
--
-- 🔵 The equality is a property of these three demo rows, NOT of the model. An
-- artist registered through `POST /api/artists` supplies an `accountId` that is
-- an independent UUID, and nothing reads one id as the other (the web app reads
-- `artist.accountId` for follows since FAN-BE-045; the route key stays the
-- entity id). Do not turn this seed convenience into an invariant.
--
-- 🔵 SINCE TASK-MONO-744 (2026-10-02 UTC) THESE ARE CONSUMER-POOL ACCOUNTS
-- ---------------------------------------------------------------------------
-- ADR-MONO-078 A / multi-tenancy.md § 소비자 계정 풀: the artists' account,
-- identity and credential live in tenant `consumer-pool`, each with an ACTIVE
-- `fan-platform` membership, and their roles live in `consumer_site_roles`
-- (account, 'fan-platform', role) instead of `account_roles` — that table's
-- composite FK (tenant_id, account_id) → accounts(tenant_id, id) cannot hold a
-- pool account under the site's tenant. 🔴 The ids did NOT change: the pool
-- keeps a moved account's id (§ 3), so `artists.account_id`, seed-fan.sh's
-- ARTIST_* literals and the public snapshot under infra/demo/public-data/ all
-- stay valid. This is the same end state TASK-BE-618's legacy mover produces
-- for a single-site account — the seed just starts there.
--
-- 🔴 EXISTING LOCAL VOLUMES KEEP THE OLD SHAPE — same reason and same rule as
-- R__05's header: INSERT IGNORE + no DELETE, so a volume that already holds
-- these ids as `fan-platform` accounts ignores the pool INSERTs and keeps its
-- `account_roles` grant (and keeps working). The membership statement is
-- guarded on `tenant_id = 'consumer-pool'`; the role tuples then have no
-- membership to reference, and INSERT IGNORE turns that FK miss into a skipped
-- row (MySQL downgrades ER_NO_REFERENCED_ROW_2 to a warning under IGNORE), so
-- nothing is written onto a still-site account. Such a volume can be moved
-- with TASK-BE-618's `POST /internal/consumer-pool/legacy-moves`, or recreated.
--
-- WHY BOTH `FAN` AND `ARTIST` ARE STORED
-- ---------------------------------------------------------------------------
-- For a POOL principal the token's roles are the site seed UNION the stored site
-- roles (TenantClaimTokenCustomizer#customizeForPoolPrincipal — RoleSeedPolicy
-- `fan-platform → FAN` ∪ consumer_site_roles), so `ARTIST` alone would already
-- yield [FAN, ARTIST] there. FAN is stored anyway, for two reasons:
--   1. it is exactly what TASK-BE-618's mover writes for these accounts (it
--      copies the stored account_roles verbatim), so a seeded demo and a moved
--      one are the same shape and one test describes both;
--   2. 🔴 the pre-pool rule has not gone away: `populateRoles` still emits a
--      stored set VERBATIM (no seed union) for every non-pool path, and the
--      roles GET that TASK-BE-618 widened answers consumer_site_roles to such a
--      path. Storing ARTIST alone would let any verbatim reader hand an artist
--      a token without FAN — the displacement TASK-MONO-512 stored FAN to
--      prevent (measured then: nothing reads FAN, so it would stay invisible).
--
-- WHY NO iam CODE CHANGED, AND WHY THAT IS THE FINDING
-- ---------------------------------------------------------------------------
-- The ticket recorded "`ARTIST` is 0 occurrences across projects/iam-platform"
-- with an instrument check (`FAN_OPERATOR` matched 3) and read it as a MISSING
-- ISSUANCE PLANE. The detection was right and the reading was wrong: the role
-- plane is open. `AccountRoleName` validates `^[A-Z][A-Z0-9_]*$` and nothing
-- else — no whitelist, no catalog, no per-tenant allow-list (its own javadoc
-- defers that to "a future task") — and `AddAccountRoleUseCase` /
-- `ProvisionAccountUseCase` persist whatever passes the regex. `ARTIST` was
-- absent because no row had ever used the value, not because no mechanism could.
-- ⇒ issuing it is a DATA change. That is why this file is the whole iam-side
-- change, and it is also why `FAN_OPERATOR` is NOT the same kind of gap: that one
-- is DERIVED from `tenant_domain_subscription` by `OperatorRoleDerivation`, so it
-- needs a subscription row, and ADR-MONO-059 § Decision (B excluded) forbids
-- opening that plane for fan. Same "0 occurrences", different cost.
--
-- WHY FLYWAY AND NOT A RUNTIME PROVISIONING CALL
-- ---------------------------------------------------------------------------
-- Same two reasons as V9005. (1) account-service's per-tenant keystone query does
-- not return rows inserted AFTER startup (TASK-MONO-160). (2) ADR-MONO-059 § A
-- assigns account issuance + `ARTIST` grant to iam, and TASK-FAN-BE-045 measured
-- that fan-platform has zero IAM-provisioning call sites and deliberately created
-- none — fan does not write into iam, so the demo's artist accounts must be born
-- on this side.
--
-- Version band: V9000+ per TASK-MONO-207. Idempotent: INSERT IGNORE throughout.
-- FK order: tenants (V0029 seeds `consumer-pool`, V0009 `fan-platform`) →
-- identities → accounts → consumer_site_memberships → consumer_site_roles
-- (FK to the membership, V0030).

-- ---------------------------------------------------------------------------
-- 1. Artist identities (ADR-MONO-034 U1-A) — in the pool, like their accounts.
--    identity_id is a NEW UUID, deliberately NOT reusing the account id (R__05).
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('0199de82-0000-7000-8000-00000000a001', 'consumer-pool', 'lumi@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a002', 'consumer-pool', 'noah@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a003', 'consumer-pool', 'sea@demo.com',  'ACTIVE', NOW(6), NOW(6), 0),
    -- TASK-MONO-638 — 아티스트 셋 → 여섯.
    ('0199de82-0000-7000-8000-00000000a004', 'consumer-pool', 'harin@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a005', 'consumer-pool', 'rio@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a006', 'consumer-pool', 'yuno@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 2. Artist accounts. `id` MUST equal the matching credentials.account_id
--    (auth-service migration-dev R__02) because that value is the OIDC `sub`,
--    AND it must equal `artists.id` in seed-fan.sh — see the header.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
VALUES
    ('0199de80-0000-7000-8000-00000000a001', '0199de82-0000-7000-8000-00000000a001',
     'consumer-pool', 'lumi@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a002', '0199de82-0000-7000-8000-00000000a002',
     'consumer-pool', 'noah@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a003', '0199de82-0000-7000-8000-00000000a003',
     'consumer-pool', 'sea@demo.com',  'ACTIVE', NOW(6), NOW(6), 0),
    -- TASK-MONO-638 — id 는 artists.id 와 **같아야** 한다(헤더의 이유).
    ('0199de80-0000-7000-8000-00000000a004', '0199de82-0000-7000-8000-00000000a004',
     'consumer-pool', 'harin@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a005', '0199de82-0000-7000-8000-00000000a005',
     'consumer-pool', 'rio@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a006', '0199de82-0000-7000-8000-00000000a006',
     'consumer-pool', 'yuno@demo.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 3. The fan-platform membership — without it the issuer mints no fan token for
--    a pool account (TASK-BE-615), and consumer_site_roles below has nothing to
--    reference. consented_at = the account's created_at (TASK-BE-618's value).
--    Guarded on the account really being a pool account (header).
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO consumer_site_memberships (account_id, site_tenant_id, status, consented_at)
SELECT id, 'fan-platform', 'ACTIVE', created_at
  FROM accounts
 WHERE tenant_id = 'consumer-pool'
   AND id IN ('0199de80-0000-7000-8000-00000000a001',
              '0199de80-0000-7000-8000-00000000a002',
              '0199de80-0000-7000-8000-00000000a003',
              '0199de80-0000-7000-8000-00000000a004',
              '0199de80-0000-7000-8000-00000000a005',
              '0199de80-0000-7000-8000-00000000a006');

-- ---------------------------------------------------------------------------
-- 4. The grant — a fan-platform SITE role of a pool account (consumer_site_roles,
--    not account_roles; header). `granted_by` is NULL — no operator performed
--    this; the demo seed did. A non-null value here would name an operator who
--    does not exist and make the audit trail lie.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO consumer_site_roles (account_id, site_tenant_id, role_name, granted_by, granted_at)
VALUES
    ('0199de80-0000-7000-8000-00000000a001', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a001', 'fan-platform', 'ARTIST', NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a002', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a002', 'fan-platform', 'ARTIST', NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a003', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a003', 'fan-platform', 'ARTIST', NULL, NOW(6)),
    -- TASK-MONO-638 — 여섯으로 늘린 아티스트에게 같은 두 역할을 준다.
    ('0199de80-0000-7000-8000-00000000a004', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a004', 'fan-platform', 'ARTIST', NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a005', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a005', 'fan-platform', 'ARTIST', NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a006', 'fan-platform', 'FAN',    NULL, NOW(6)),
    ('0199de80-0000-7000-8000-00000000a006', 'fan-platform', 'ARTIST', NULL, NOW(6));
