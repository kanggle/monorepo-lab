-- TASK-FIN-BE-041: ShedLock lock-state table for the FX-rate poller single-leader guard
-- (ADR-002 D4 realized). Standard ShedLock JdbcTemplateLockProvider schema (name PK,
-- lock_until, locked_at, locked_by). One row per lock name (here: ledger-fx-rate-poll);
-- a replica that cannot CAS-acquire the row skips the tick (single-instance execution, AC-2).
-- The table is created unconditionally (Flyway runs regardless of the poller being enabled)
-- — it stays empty until the FX feed is switched on, so this is net-zero for a default
-- (enabled=false) deploy.
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
