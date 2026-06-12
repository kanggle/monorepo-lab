-- finance-platform ledger-service multi-currency journals (8th increment,
-- TASK-FIN-BE-014). MySQL 8, InnoDB, utf8mb4. A single journal entry may now carry
-- lines in different currencies, balanced in the fixed base/reporting currency
-- (KRW): each journal_line gains its value in the base currency (base_amount_minor,
-- BIGINT — authoritative for the balance) + base_currency + an exact-decimal
-- exchange_rate (DECIMAL(20,8) — provenance only, NOT a float; F5 preserved).
--
-- Net-zero: every existing row is KRW, so the backfill sets base_amount_minor =
-- amount_minor, base_currency = currency (KRW), exchange_rate = 1 — the
-- base-currency balance check on an all-KRW entry equals the prior same-currency
-- check.

-- ---------------------------------------------------------------------------
-- journal_line — add the per-line FX provenance + base-currency value. The
-- NOT NULL DEFAULTs keep INSERTs from the auto-journal path (which set them
-- explicitly via the entity) and any in-flight rows well-defined; the backfill
-- below realigns every pre-existing row's base value to its KRW amount.
-- ---------------------------------------------------------------------------
ALTER TABLE journal_line
    ADD COLUMN exchange_rate     DECIMAL(20,8) NOT NULL DEFAULT 1,
    ADD COLUMN base_amount_minor BIGINT        NOT NULL DEFAULT 0,
    ADD COLUMN base_currency     VARCHAR(3)    NOT NULL DEFAULT 'KRW';

-- Backfill existing rows — all are KRW (single-currency until this increment).
UPDATE journal_line
   SET base_amount_minor = amount_minor,
       base_currency     = currency,
       exchange_rate     = 1;
