-- finance-platform ledger-service FX acquisition position lots (16th increment —
-- TASK-FIN-BE-024, ADR-001 D2 lot model + D5 additive migration / backfill). MySQL 8,
-- InnoDB, utf8mb4 — parity with V1..V9. Materializes each foreign-currency acquisition
-- as a lot row so FIN-BE-025 can walk them FIFO on settlement. This increment is
-- SHADOW / write-only: lots are created (acquisition hook) and backfilled (below) but
-- NOTHING consumes them yet — SettleForeignPositionUseCase / FxSettlementPolicy /
-- FxRevaluationPolicy are byte-unchanged (net-zero), settlement stays weighted-average.
--
-- ADDITIVE — a NEW table only. NO change to any existing table / row / CHECK constraint.
-- The backfill reconstructs each open pre-existing foreign position as a SINGLE synthetic
-- lot whose carrying EXACTLY equals the position's current pool carrying (D5 — zero double
-- counting). A fresh CI/test DB has no pre-V10 journal_line rows, so the backfill is a
-- no-op there; it exists for real deployments carrying open positions at migration time.

-- ---------------------------------------------------------------------------
-- fx_position_lot — one row per foreign acquisition (or one synthetic row per open
-- pre-existing position from the backfill). lot_id is a UUID PK. remaining_foreign_minor /
-- carrying_base_minor are the still-open portion (FIN-BE-025 decrements them; in this
-- increment they equal the originals). source_journal_entry_id is the acquiring entry's
-- id for hook-created lots, NULL for synthetic backfill lots. The CHECK constraints mirror
-- the FxPositionLot factory invariants. The idx_fx_lot_position index serves the FIFO
-- open-lots read (tenant, account, currency ordered by acquired_at, seq).
-- ---------------------------------------------------------------------------
CREATE TABLE fx_position_lot (
    lot_id                  VARCHAR(36)  NOT NULL,
    tenant_id               VARCHAR(64)  NOT NULL,
    ledger_account_code     VARCHAR(100) NOT NULL,
    currency                VARCHAR(3)   NOT NULL,
    acquired_at             DATETIME(6)  NOT NULL,
    seq                     BIGINT       NOT NULL,
    original_foreign_minor  BIGINT       NOT NULL,
    original_base_minor     BIGINT       NOT NULL,
    remaining_foreign_minor BIGINT       NOT NULL,
    carrying_base_minor     BIGINT       NOT NULL,
    source_journal_entry_id VARCHAR(36)  NULL,
    created_at              DATETIME(6)  NOT NULL,
    PRIMARY KEY (lot_id),
    KEY idx_fx_lot_position (tenant_id, ledger_account_code, currency, acquired_at, seq),
    CONSTRAINT ck_fx_lot_original_foreign_positive  CHECK (original_foreign_minor > 0),
    CONSTRAINT ck_fx_lot_original_base_nonneg       CHECK (original_base_minor >= 0),
    CONSTRAINT ck_fx_lot_remaining_foreign_nonneg   CHECK (remaining_foreign_minor >= 0),
    CONSTRAINT ck_fx_lot_remaining_le_original       CHECK (remaining_foreign_minor <= original_foreign_minor),
    CONSTRAINT ck_fx_lot_carrying_base_nonneg       CHECK (carrying_base_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Backfill — reconstruct each open pre-existing foreign position as ONE synthetic lot.
-- Group existing journal_line by (tenant_id, ledger_account_code, currency) for the
-- foreign currencies (currency <> 'KRW'). The signed foreign sum (DEBIT +amount, CREDIT
-- -amount) is the net open foreign quantity; HAVING <> 0 drops fully-settled positions.
-- ABS(Σ signed amount) = original_foreign = remaining_foreign; ABS(Σ signed base) =
-- original_base = carrying_base — EXACTLY the position's pool carrying (D5 no double-count).
-- seq = MIN(line id), acquired_at = MIN(posted_at) so the synthetic lot sorts first FIFO.
-- source_journal_entry_id is NULL (synthetic — no single acquiring entry). On a fresh DB
-- the SELECT yields no rows (no pre-V10 lines) → no-op.
-- ---------------------------------------------------------------------------
INSERT INTO fx_position_lot (
    lot_id, tenant_id, ledger_account_code, currency, acquired_at, seq,
    original_foreign_minor, original_base_minor,
    remaining_foreign_minor, carrying_base_minor,
    source_journal_entry_id, created_at)
SELECT
    UUID(),
    jl.tenant_id,
    jl.ledger_account_code,
    jl.currency,
    MIN(jl.posted_at),
    MIN(jl.id),
    ABS(SUM(CASE WHEN jl.direction = 'DEBIT' THEN jl.amount_minor      ELSE -jl.amount_minor      END)),
    ABS(SUM(CASE WHEN jl.direction = 'DEBIT' THEN jl.base_amount_minor ELSE -jl.base_amount_minor END)),
    ABS(SUM(CASE WHEN jl.direction = 'DEBIT' THEN jl.amount_minor      ELSE -jl.amount_minor      END)),
    ABS(SUM(CASE WHEN jl.direction = 'DEBIT' THEN jl.base_amount_minor ELSE -jl.base_amount_minor END)),
    NULL,
    NOW(6)
FROM journal_line jl
WHERE jl.currency <> 'KRW'
GROUP BY jl.tenant_id, jl.ledger_account_code, jl.currency
HAVING SUM(CASE WHEN jl.direction = 'DEBIT' THEN jl.amount_minor ELSE -jl.amount_minor END) <> 0;
