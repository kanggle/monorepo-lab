-- finance-platform ledger-service period-close schema (2nd increment,
-- TASK-FIN-BE-008). MySQL 8, InnoDB, utf8mb4 — parity with V1.
-- Accounting period (OPEN→CLOSED state machine, half-open [from, to) window) +
-- the immutable close-time trial-balance snapshot. fintech F2 (period close /
-- non-overlap), F3/F6 (snapshot insert-only), F5 (integer minor-unit money).
-- Multi-tenant: every table carries tenant_id.

-- ---------------------------------------------------------------------------
-- accounting_period — the OPEN→CLOSED aggregate. period_from/period_to are the
-- half-open window edges (period_from inclusive, period_to exclusive); the
-- non-overlap invariant is enforced in the application layer (findOverlapping).
-- closed_at/closed_by/entry_count are stamped at close (NULL while OPEN). version
-- supports optimistic-lock parity (T7).
-- ---------------------------------------------------------------------------
CREATE TABLE accounting_period (
    period_id       VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    period_from     DATETIME(6)  NOT NULL,
    period_to       DATETIME(6)  NOT NULL,
    status          VARCHAR(10)  NOT NULL,
    closed_at       DATETIME(6),
    closed_by       VARCHAR(128),
    entry_count     BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (period_id),
    CONSTRAINT ck_accounting_period_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_accounting_period_tenant_window
    ON accounting_period (tenant_id, period_from, period_to);

-- ---------------------------------------------------------------------------
-- period_balance_snapshot — the close-time per-account trial-balance record.
-- Insert-only (F3/F6 parity): no UPDATE/DELETE path; one row per ledger account
-- in the period. The grand totals + inBalance are computed on read by summing the
-- rows (no header row stored). Money is BIGINT minor units + currency VARCHAR(3)
-- (F5 — never a float).
-- ---------------------------------------------------------------------------
CREATE TABLE period_balance_snapshot (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    period_id           VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    ledger_account_code VARCHAR(100) NOT NULL,
    debit_minor         BIGINT       NOT NULL,
    credit_minor        BIGINT       NOT NULL,
    currency            VARCHAR(3)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_period_snapshot_period FOREIGN KEY (period_id)
        REFERENCES accounting_period (period_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_period_snapshot_period ON period_balance_snapshot (period_id);
