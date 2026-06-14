-- finance-platform ledger-service cross-currency base-leg matching (14th increment,
-- TASK-FIN-BE-021). MySQL 8, InnoDB, utf8mb4 — parity with V1..V7.
--
-- A base-currency (KRW) external statement line may now reconcile against a FOREIGN
-- internal ledger line by that line's carrying base value, as a fallback after the
-- same-currency match is exhausted (within the per-tenant FxTolerance). Such a match
-- is flagged for regulated-ledger audit transparency — "this KRW bank line matched a
-- foreign ledger position by carrying base".
--
-- Net-zero: the column is ADDITIVE + DEFAULTED — every existing reconciliation_match
-- row, and every same-currency match, is FALSE. NO backfill, NO other table change,
-- NO CHECK change. Boolean follows the existing dialect (MySQL BOOLEAN == TINYINT(1)).
ALTER TABLE reconciliation_match
    ADD COLUMN cross_currency BOOLEAN NOT NULL DEFAULT FALSE;
