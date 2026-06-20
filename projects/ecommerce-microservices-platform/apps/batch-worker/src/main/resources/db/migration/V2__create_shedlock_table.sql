-- TASK-BE-409: ShedLock lock-state table for distributed scheduler single-instance enforcement.
-- Standard ShedLock JdbcTemplateLockProvider schema (name PK, lock_until, locked_at, locked_by).
-- One row per lock name (e.g. batch-search-index-consistency-check). A replica that cannot
-- CAS-acquire the row skips the tick, satisfying the batch-job.md "분산락 필수" requirement.
--
-- Timestamp type decision (B-1 fix): shipping-service V6__create_shedlock_table.sql uses bare
-- TIMESTAMP (the upstream ShedLock canonical DDL). However, batch-worker V1 uses TIMESTAMPTZ
-- throughout and the JdbcTemplateLockProvider is configured with .usingDbTime() which resolves
-- to Postgres now() — a TIMESTAMPTZ expression. Using bare TIMESTAMP here would silently strip
-- the time-zone, causing subtle DST-boundary drift. We therefore use TIMESTAMP WITH TIME ZONE
-- (alias TIMESTAMPTZ) for both columns to stay consistent with V1 and avoid tz-stripping.
-- JdbcTemplateLockProvider binds Instants and is fully compatible with TIMESTAMPTZ.
CREATE TABLE shedlock (
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
