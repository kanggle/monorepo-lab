-- finance-platform ledger-service multi-currency reconciliation (11th increment,
-- TASK-FIN-BE-017). MySQL 8, InnoDB, utf8mb4. A foreign-currency external statement
-- line MAY now carry the bank-reported base/reporting-currency (KRW) value it
-- credited, at the bank's FX rate. When the line matches an internal line on the
-- transaction (foreign) leg, the matcher compares this base value to the internal
-- line's carrying base; a difference records an AMOUNT_MISMATCH (FX-difference)
-- discrepancy on the MATCHED line (F8 — recorded for operator review, never
-- auto-adjusted).
--
-- Net-zero: the two columns are ADDITIVE + NULLABLE — every existing
-- reconciliation_statement_line row stays NULL, and a KRW / base-less foreign line
-- never sets them, so the base-leg check never fires. NO backfill. The
-- ck_recon_discrepancy_type CHECK is UNCHANGED — AMOUNT_MISMATCH is already in the
-- V4 allow-list (this is its first activation). Money stays BIGINT minor units +
-- currency VARCHAR(3) (F5 — never a float).

-- ---------------------------------------------------------------------------
-- reconciliation_statement_line — add the optional bank-reported base value. NULL
-- when the statement does not carry one (existing rows + KRW / base-less lines).
-- ---------------------------------------------------------------------------
ALTER TABLE reconciliation_statement_line
    ADD COLUMN base_amount_minor BIGINT     NULL,
    ADD COLUMN base_currency     VARCHAR(3) NULL;
