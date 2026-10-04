-- TASK-BE-621 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 5 «사이트 잠금 vs 계정 잠금»;
-- account-service data-model.md § consumer_site_memberships).
--
-- Owner decision (2026-10-04): a site operator's lock applies to THAT site only; locking the whole
-- account is the platform admin's. A site lock of a consumer-pool member is therefore a state of the
-- membership, not of the account: status = 'LOCKED', with who locked it and when.
--
-- 'LOCKED' is a new value rather than a reuse of 'LEFT' (OPERATOR): leaving drops the site roles and
-- has no "undo", while a lock is temporary — unlocking must restore the membership as it was.
--
-- Additive for data: the status CHECK is replaced by a wider one (same name), two NULLable columns and
-- three CHECKs are added. Every existing row is ACTIVE or LEFT with NULL lock columns, so all CHECKs
-- hold on an existing volume with no backfill. Existing INSERTs/UPDATEs name their columns explicitly.
--
-- MySQL 8.0.16+ enforces CHECK constraints (the Testcontainers image is mysql:8.0).
ALTER TABLE consumer_site_memberships
    DROP CHECK ck_consumer_site_memberships_status;

ALTER TABLE consumer_site_memberships
    ADD COLUMN locked_at          DATETIME(6) NULL AFTER left_by_actor_id,
    ADD COLUMN locked_by_actor_id VARCHAR(64) NULL AFTER locked_at,
    ADD CONSTRAINT ck_consumer_site_memberships_status
        CHECK (status IN ('ACTIVE', 'LEFT', 'LOCKED')),
    ADD CONSTRAINT ck_consumer_site_memberships_locked_has_time
        CHECK (status <> 'LOCKED' OR locked_at IS NOT NULL),
    ADD CONSTRAINT ck_consumer_site_memberships_lock_only_when_locked
        CHECK (status = 'LOCKED' OR (locked_at IS NULL AND locked_by_actor_id IS NULL)),
    ADD CONSTRAINT ck_consumer_site_memberships_locked_has_no_leave
        CHECK (status <> 'LOCKED' OR (left_at IS NULL AND left_by IS NULL));
