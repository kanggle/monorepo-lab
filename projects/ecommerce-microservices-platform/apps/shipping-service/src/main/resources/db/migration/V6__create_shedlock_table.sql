-- TASK-BE-360: ShedLock lock-state table for the unattended auto-collect tracking sweep.
-- Standard ShedLock JdbcTemplateLockProvider schema (name PK, lock_until, locked_at,
-- locked_by). One row per lock name (here: shipping-auto-collect-tracking); a replica that
-- cannot CAS-acquire the row skips the tick (single-instance execution, AC-2). The table is
-- created unconditionally (Flyway runs regardless of the scheduler being enabled) — it stays
-- empty until the auto-collect scheduler is switched on, so this is net-zero for a default
-- (enabled=false) deploy.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
