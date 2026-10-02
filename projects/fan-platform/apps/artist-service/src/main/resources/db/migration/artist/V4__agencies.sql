-- TASK-MONO-748 (ADR-MONO-079 ACCEPTED — A, D1 · D2): agencies become an entity.
--
-- Until now an artist's or group's agency was free text — artists.agency and
-- artist_groups.agency, VARCHAR(120), no normalisation, no FK, display only.
-- This migration:
--   1. creates `agencies` (one row per agency per tenant, UNIQUE(tenant_id, name)),
--      with the nullable `store_seller_id` that ADR-079 D2 puts on the fan side;
--   2. gives artists / artist_groups a nullable `agency_id` FK;
--   3. MOVES the existing free text into it — identical values (after the
--      normalisation rule below) collapse to ONE agencies row, and every artist /
--      group whose text is non-blank is pointed at that row;
--   4. proves step 3 left nothing behind (DO block at the end — Failure Scenario 1:
--      «a partial fill makes the agency display disappear»).
--
-- The free-text columns are KEPT (ADR-079 D1: «이전 기간에만 남긴다»; dropping them
-- is a separate step). Display now reads the agency entity's name; the free text is
-- the fallback for rows with no agency_id.
--
-- Contract: specs/contracts/http/artist-api.md § Agencies
-- Schema spec: specs/services/artist-service/data-model.md § V4
--
-- ---------------------------------------------------------------------------
-- Normalisation rule (written down because the move depends on it, and the API's
-- Agency.normalizeName applies the SAME rule so a name typed later lands on the
-- same row the move created):
--
--   key = the free text with every run of ASCII whitespace [ \t\n\r\f\v] collapsed
--         to ONE space, then the leading/trailing space removed.
--   Case is PRESERVED and SIGNIFICANT — 'Aurora' and 'AURORA' are two rows.
--   A value that is empty after this (NULL, '', '   ') is "no agency" → agency_id NULL.
--
-- Why case is not folded: the move must not invent equivalences a human did not
-- write. TASK-MONO-748 § Edge Cases — «SM» / «SM Entertainment» must NOT auto-merge —
-- states the principle: anything outside the rule stays a separate row and a person
-- merges it. Case-folding is the same kind of guess (it must also pick ONE spelling
-- as the display name, i.e. silently rewrite what some rows showed). Whitespace
-- differences are invisible on screen, so collapsing them changes nothing a reader
-- could have seen. stage_name already uses the same convention (case-sensitive
-- UNIQUE, "caller normalizes" — V1).
-- ---------------------------------------------------------------------------

CREATE TABLE agencies (
    id                  VARCHAR(36)   PRIMARY KEY,
    tenant_id           VARCHAR(64)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    -- ADR-079 D2: the store seller (ecommerce `sellers.seller_id`, VARCHAR(64)) that
    -- sells this agency's goods. 0..1 (rider R2). Different database → no FK; the
    -- value is verified against the store at write time (fail-closed).
    store_seller_id     VARCHAR(64),
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_agencies_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT uq_agencies_tenant_name UNIQUE (tenant_id, name),
    -- Target of the tenant-safe composite FKs below: an artist can only point at an
    -- agency of its OWN tenant, enforced by the database, not only by the service.
    CONSTRAINT uq_agencies_tenant_id_id UNIQUE (tenant_id, id)
);

-- ---------------------------------------------------------------------------
-- 1. One agencies row per distinct normalised name per tenant, across BOTH tables
--    (an artist and a group with the same text share the row).
-- ---------------------------------------------------------------------------
INSERT INTO agencies (id, tenant_id, name, status, store_seller_id, created_at, updated_at, version)
SELECT gen_random_uuid()::text, src.tenant_id, src.norm_name, 'ACTIVE', NULL, now(), now(), 0
FROM (
    SELECT tenant_id,
           btrim(regexp_replace(agency, '[ \t\n\r\f\v]+', ' ', 'g'), ' ') AS norm_name
    FROM artists
    UNION
    SELECT tenant_id,
           btrim(regexp_replace(agency, '[ \t\n\r\f\v]+', ' ', 'g'), ' ') AS norm_name
    FROM artist_groups
) src
WHERE src.norm_name IS NOT NULL
  AND src.norm_name <> '';

-- ---------------------------------------------------------------------------
-- 2. agency_id columns — nullable (solo / unaffiliated is a legal state, Edge Case 2),
--    composite FK so the database refuses a cross-tenant link.
-- ---------------------------------------------------------------------------
ALTER TABLE artists ADD COLUMN agency_id VARCHAR(36);
ALTER TABLE artists
    ADD CONSTRAINT fk_artists_agency
    FOREIGN KEY (tenant_id, agency_id) REFERENCES agencies (tenant_id, id);
CREATE INDEX idx_artists_tenant_agency ON artists (tenant_id, agency_id);

ALTER TABLE artist_groups ADD COLUMN agency_id VARCHAR(36);
ALTER TABLE artist_groups
    ADD CONSTRAINT fk_artist_groups_agency
    FOREIGN KEY (tenant_id, agency_id) REFERENCES agencies (tenant_id, id);
CREATE INDEX idx_artist_groups_tenant_agency ON artist_groups (tenant_id, agency_id);

-- ---------------------------------------------------------------------------
-- 3. Point every row at its agency (same normalisation expression as step 1).
-- ---------------------------------------------------------------------------
UPDATE artists a
SET agency_id = ag.id
FROM agencies ag
WHERE ag.tenant_id = a.tenant_id
  AND ag.name = btrim(regexp_replace(a.agency, '[ \t\n\r\f\v]+', ' ', 'g'), ' ');

UPDATE artist_groups g
SET agency_id = ag.id
FROM agencies ag
WHERE ag.tenant_id = g.tenant_id
  AND ag.name = btrim(regexp_replace(g.agency, '[ \t\n\r\f\v]+', ' ', 'g'), ' ');

-- ---------------------------------------------------------------------------
-- 4. Prove it — no non-blank free text may be left without an agency_id. If this
--    fires, the migration (and the boot) fails instead of shipping a directory whose
--    agency display silently vanished for some rows.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    orphan_artists INTEGER;
    orphan_groups  INTEGER;
BEGIN
    SELECT count(*) INTO orphan_artists FROM artists
    WHERE agency_id IS NULL
      AND btrim(regexp_replace(coalesce(agency, ''), '[ \t\n\r\f\v]+', ' ', 'g'), ' ') <> '';
    SELECT count(*) INTO orphan_groups FROM artist_groups
    WHERE agency_id IS NULL
      AND btrim(regexp_replace(coalesce(agency, ''), '[ \t\n\r\f\v]+', ' ', 'g'), ' ') <> '';
    IF orphan_artists > 0 OR orphan_groups > 0 THEN
        RAISE EXCEPTION 'V4 agency move left % artists and % groups with free text but no agency_id',
            orphan_artists, orphan_groups;
    END IF;
END
$$;
