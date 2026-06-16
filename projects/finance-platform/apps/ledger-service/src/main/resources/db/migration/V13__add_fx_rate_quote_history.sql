-- finance-platform ledger-service FX rate quote history (26th increment —
-- TASK-FIN-BE-039, ADR-002 D2/item-3 — append-only audit trail). MySQL 8, InnoDB,
-- utf8mb4 — parity with V1..V12. Persists every fetched market-rate quote so the
-- provenance of an auto-applied rate (provider · as-of · fetched-at) is queryable
-- historically, not just as the latest-only cache. The `fx_rate_quote` table retains
-- one row per pair (last-write-wins upsert); this table retains ALL rows ever appended
-- (one per poll run per pair) to satisfy the "audit-heavy / regulated" trait requirement
-- ("a P&L line must answer *where did this rate come from*", ADR-002 § Context).
--
-- ADDITIVE — a NEW table only. NO change to any existing table (or any existing CHECK
-- constraint), NO backfill. The surrogate `id BIGINT AUTO_INCREMENT PRIMARY KEY` is
-- required because multiple rows exist per (base_currency, foreign_currency) pair —
-- the pair composite PK of `fx_rate_quote` would collapse them to a latest-only row,
-- defeating the append-only trail. `rate` is an EXACT DECIMAL(20,8) (base-minor-per-
-- foreign-minor, the SAME unit convention as closingRate/settlementRate — NOT a BIGINT
-- minor amount). DATETIME(6) matches the existing audit / period / V9 / V11 / V12
-- columns. `as_of` = the provider-stated rate instant, `fetched_at` = when we pulled
-- it (audit-heavy provenance — both mirrored from `fx_rate_quote`). NOT per-tenant —
-- a market rate is tenant-agnostic.
--
-- NOT per-tenant — mirrors `fx_rate_quote` (market rate is tenant-agnostic).
-- Out of scope (still-deferred): history read / per-pair drill endpoint, console FX
-- dashboard, per-tenant override, ShedLock single-leader guard, retention / pruning.

-- ---------------------------------------------------------------------------
-- fx_rate_quote_history — one row per poll-run per (base_currency, foreign_currency);
-- append-only (no UPDATE / DELETE). Surrogate PK `id` enables many rows per pair.
-- Index on (base_currency, foreign_currency, fetched_at) for future time-ordered drill.
-- ---------------------------------------------------------------------------
CREATE TABLE fx_rate_quote_history (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    base_currency    VARCHAR(3)     NOT NULL,
    foreign_currency VARCHAR(3)     NOT NULL,
    rate             DECIMAL(20, 8) NOT NULL,
    as_of            DATETIME(6)    NOT NULL,
    source           VARCHAR(64)    NOT NULL,
    fetched_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_fx_rate_quote_history_pair_fetched
    ON fx_rate_quote_history (base_currency, foreign_currency, fetched_at);
