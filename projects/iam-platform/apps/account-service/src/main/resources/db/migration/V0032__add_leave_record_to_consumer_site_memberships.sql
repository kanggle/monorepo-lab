-- TASK-BE-619 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 탈퇴 vs 계정 삭제»;
-- account-service data-model.md § consumer_site_memberships).
--
-- The first writer of status = 'LEFT' arrives with this ticket, and the owner decided (2026-10-03)
-- that the two ways a membership becomes LEFT reopen differently:
--   * SELF      — the person left the site themself → consenting to the site again reopens it;
--   * OPERATOR  — that site's operator removed the person → consent does NOT reopen it.
-- So the row records who made it LEFT, and when.
--
-- Additive only: three NULLable columns and one CHECK. Every existing row is ACTIVE (V0030 shipped no
-- LEFT writer; the legacy-move and signup paths insert ACTIVE), so the CHECK holds on an existing volume
-- with no backfill. Existing INSERTs name their columns explicitly, so they are unaffected.
ALTER TABLE consumer_site_memberships
    ADD COLUMN left_at          DATETIME(6) NULL AFTER consented_at,
    ADD COLUMN left_by          VARCHAR(20) NULL AFTER left_at,
    ADD COLUMN left_by_actor_id VARCHAR(64) NULL AFTER left_by,
    ADD CONSTRAINT ck_consumer_site_memberships_left_by
        CHECK (left_by IS NULL OR left_by IN ('SELF', 'OPERATOR')),
    ADD CONSTRAINT ck_consumer_site_memberships_active_has_no_leave
        CHECK (status <> 'ACTIVE' OR (left_at IS NULL AND left_by IS NULL));
