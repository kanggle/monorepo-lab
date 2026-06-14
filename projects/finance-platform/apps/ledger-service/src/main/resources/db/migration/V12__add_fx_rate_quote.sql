-- finance-platform ledger-service FX rate quote cache (23rd increment —
-- TASK-FIN-BE-031, ADR-002 D2/D6 — first external HTTP integration, shadow). MySQL 8, InnoDB,
-- utf8mb4 — parity with V1..V11. Stores the latest fetched market rate per currency pair so the
-- (future, FIN-BE-032) cache-fallback consumption path can read provider rates WITHOUT a synchronous
-- external call on the operator path. The scheduled poller (FxRateFeedPoller) upserts this table;
-- the operator settlement/revaluation paths are NOT changed by this increment (shadow).
--
-- ADDITIVE — a NEW table only. NO change to any existing table (or any existing CHECK constraint),
-- NO backfill. An EMPTY cache means "auto-apply unavailable" = the operator keeps entering rates by
-- hand = net-zero: every existing settlement/FIFO/revaluation/reconciliation computation is
-- byte-identical to FIN-BE-029. `rate` is an EXACT DECIMAL(20,8) (base-minor-per-foreign-minor, the
-- SAME unit convention as closingRate/settlementRate — NOT a BIGINT minor amount). DATETIME(6)
-- matches the existing audit / period / V9 / V11 columns. `as_of` = the provider-stated rate instant
-- (staleness basis for FIN-BE-032), `fetched_at` = when we pulled it (audit-heavy provenance).
--
-- NOT per-tenant — a market rate is tenant-agnostic (unlike reconciliation_fx_tolerance /
-- fx_cost_flow_config which are per-tenant); the PK is the currency pair only.

-- ---------------------------------------------------------------------------
-- fx_rate_quote — one row per (base_currency, foreign_currency) composite PK; the latest quote
-- (last-write-wins upsert). source = provider identifier (audit). base_currency is the reporting
-- currency (KRW); foreign_currency is the priced leg.
-- ---------------------------------------------------------------------------
CREATE TABLE fx_rate_quote (
    base_currency    VARCHAR(3)     NOT NULL,
    foreign_currency VARCHAR(3)     NOT NULL,
    rate             DECIMAL(20, 8) NOT NULL,
    as_of            DATETIME(6)    NOT NULL,
    source           VARCHAR(64)    NOT NULL,
    fetched_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (base_currency, foreign_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
