-- TASK-FAN-BE-045 (ADR-MONO-059 ACCEPTED — A): give the artist aggregate the
-- account that authors as it.
--
-- Why the column exists. The community feed joins
--   posts.author_account_id ⋈ follows.artist_account_id
-- and PublishPostUseCase fixes the author to the authenticated caller
-- (actor.accountId()). With no account on the artist row, no real caller could
-- ever produce an ARTIST_POST that reaches a follower's feed — the join held
-- only because the demo seed wrote rows directly into the database.
--
-- Contract: specs/contracts/http/artist-api.md § `accountId`
-- Schema spec: specs/services/artist-service/data-model.md § V3 backfill obligation
--
-- ---------------------------------------------------------------------------
-- Backfill obligation (TASK-SCM-BE-059 V6 lesson: prove it, do not hope).
-- The column ends up NOT NULL with a UNIQUE key, so this migration must be able
-- to state that (a) no NULL can survive and (b) no collision is possible.
--
--   (a) No NULL survives. The column is added nullable, then every row is set to
--       its own `id`. `id` is the PRIMARY KEY, so it is NOT NULL on every
--       existing row by definition — the UPDATE cannot leave a NULL behind, and
--       SET NOT NULL therefore cannot fail.
--
--   (b) No collision is possible. `id` is the primary key, hence unique across
--       the entire table. Backfilled `(tenant_id, account_id)` pairs are
--       therefore distinct a fortiori: a tighter key cannot collide where the
--       looser one already does not.
--
--   (c) Why identity and not NULL / a placeholder. infra/demo/seed/seed-fan.sh
--       already writes the artist ENTITY id into BOTH sides of the join —
--       follows.artist_account_id through the API, posts.author_account_id
--       direct-DB. Identity is the only backfill under which AC-6's follow
--       validation does not start rejecting the demo's own follow calls on the
--       day it lands. The backfilled value is NOT a real IAM subject: nobody can
--       log in as it, so those artists still cannot publish through the API.
--       That half is TASK-MONO-512 (the ARTIST role has no issuing path).
--       Re-binding a demo artist onto a real IAM subject later is a data
--       migration across two databases, not a PATCH — see the contract.
-- ---------------------------------------------------------------------------

ALTER TABLE artists ADD COLUMN account_id VARCHAR(36);

UPDATE artists SET account_id = id WHERE account_id IS NULL;

ALTER TABLE artists ALTER COLUMN account_id SET NOT NULL;

-- One account authors as at most one artist within a tenant. Violation surfaces
-- as 409 ARTIST_ACCOUNT_CONFLICT. This key also serves the account_id lookup
-- behind GET /internal/artists/exists — no separate index is needed.
ALTER TABLE artists
    ADD CONSTRAINT uq_artists_tenant_account_id UNIQUE (tenant_id, account_id);
