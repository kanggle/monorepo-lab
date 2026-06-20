-- TASK-BE-409: ShedLock lock-state table for distributed scheduler single-instance enforcement.
-- Standard ShedLock JdbcTemplateLockProvider schema (name PK, lock_until, locked_at, locked_by).
-- One row per lock name (e.g. batch-search-index-consistency-check). A replica that cannot
-- CAS-acquire the row skips the tick, satisfying the batch-job.md "분산락 필수" requirement.
-- Mirrors shipping-service V6__create_shedlock_table.sql (TASK-BE-360) for monorepo consistency.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
