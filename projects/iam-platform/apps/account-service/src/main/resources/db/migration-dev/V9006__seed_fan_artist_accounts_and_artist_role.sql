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
-- WHY BOTH `FAN` AND `ARTIST` — the seed is REPLACED, not unioned
-- ---------------------------------------------------------------------------
-- 🔴 `TenantClaimTokenCustomizer#populateRoles` emits the stored `account_roles`
-- VERBATIM when the set is non-empty, and falls to `RoleSeedPolicy` only when it
-- is EMPTY ("stored roles, when present, are emitted verbatim — never unioned
-- with the seed"). So granting `ARTIST` alone would not ADD a role to these
-- accounts — it would REPLACE the `fan-platform → FAN` seed and hand the artist
-- a token that no longer says FAN. Measured today no production code path reads
-- the `FAN` role (the fan gateway admits on `RoleAdmissions.roleOrScope()`, which
-- takes any role; the premium tier is a membership lookup, not a role), so the
-- displacement would be invisible until something started reading it — which is
-- precisely the failure this repo keeps paying for. Both roles are stored.
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
-- FK order: tenants (V0009 seeds `fan-platform`) → identities → accounts →
-- account_roles (composite FK to accounts + tenants, V0013).

-- ---------------------------------------------------------------------------
-- 1. Artist identities (ADR-MONO-034 U1-A) — one per (tenant, email).
--    identity_id is a NEW UUID, deliberately NOT reusing the account id (V9005).
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('0199de82-0000-7000-8000-00000000a001', 'fan-platform', 'lumi@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a002', 'fan-platform', 'noah@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de82-0000-7000-8000-00000000a003', 'fan-platform', 'sea@demo.com',  'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 2. Artist accounts. `id` MUST equal the matching credentials.account_id
--    (auth-service migration-dev V9002) because that value is the OIDC `sub`,
--    AND it must equal `artists.id` in seed-fan.sh — see the header.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, created_at, updated_at, version)
VALUES
    ('0199de80-0000-7000-8000-00000000a001', '0199de82-0000-7000-8000-00000000a001',
     'fan-platform', 'lumi@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a002', '0199de82-0000-7000-8000-00000000a002',
     'fan-platform', 'noah@demo.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('0199de80-0000-7000-8000-00000000a003', '0199de82-0000-7000-8000-00000000a003',
     'fan-platform', 'sea@demo.com',  'ACTIVE', NOW(6), NOW(6), 0);

-- ---------------------------------------------------------------------------
-- 3. The grant. `granted_by` is NULL — no operator performed this; the demo
--    seed did. A non-null value here would name an operator who does not exist
--    and make the audit trail lie.
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO account_roles (tenant_id, account_id, role_name, granted_by, granted_at)
VALUES
    ('fan-platform', '0199de80-0000-7000-8000-00000000a001', 'FAN',    NULL, NOW(6)),
    ('fan-platform', '0199de80-0000-7000-8000-00000000a001', 'ARTIST', NULL, NOW(6)),
    ('fan-platform', '0199de80-0000-7000-8000-00000000a002', 'FAN',    NULL, NOW(6)),
    ('fan-platform', '0199de80-0000-7000-8000-00000000a002', 'ARTIST', NULL, NOW(6)),
    ('fan-platform', '0199de80-0000-7000-8000-00000000a003', 'FAN',    NULL, NOW(6)),
    ('fan-platform', '0199de80-0000-7000-8000-00000000a003', 'ARTIST', NULL, NOW(6));
