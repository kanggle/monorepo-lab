-- finance-platform ledger-service per-tenant FX contract-rate override (28th increment —
-- TASK-FIN-BE-042, ADR-002 § 3.1 deferred "per-tenant override / 특수 계약환율"). MySQL 8,
-- InnoDB, utf8mb4 — parity with V1..V13 (V14 is TASK-FIN-BE-041's ShedLock table; this task
-- owns V15). Stores a tenant-scoped contract FX rate for a currency pair that overrides the
-- tenant-agnostic market rate from the fx_rate_quote feed during FX resolution.
--
-- ADDITIVE — a NEW table only. NO change to any existing table, NO change to any existing
-- CHECK constraint. NO backfill: absence of a row means NO override — resolution falls
-- through to the existing feed path byte-identically (net-zero, today's behaviour). The
-- market fx_rate_quote (V12) stays global/tenant-agnostic; this override is the per-tenant
-- layer on top, resolved at read time by ResolveEffectiveFxRate (precedence: manual
-- providedRate > per-tenant override > feed market rate > FX_RATE_UNAVAILABLE).
-- updated_by/updated_at are the regulated/audit-heavy audit columns, stamped on every
-- operator upsert (last-write-wins). DATETIME(6) matches the existing audit / period /
-- fx_cost_flow_config timestamp columns.

-- ---------------------------------------------------------------------------
-- fx_rate_override — one row per (tenant_id, base_currency, foreign_currency). rate =
-- the contract rate in the SAME unit as fx_rate_quote.rate (base-minor-per-foreign-minor),
-- DECIMAL(20,8) exact (no float — regulated F5). The DB CHECK enforces rate > 0 (a foreign
-- position can never be valued at a zero/negative contract rate — the structural backstop
-- behind the application's VALIDATION_ERROR on a non-positive rate). Currencies stored as
-- their 3-letter ISO-4217 codes. Always-effective (v1 minimal — no temporal validity, matching
-- the fx_cost_flow_config idiom).
-- ---------------------------------------------------------------------------
CREATE TABLE fx_rate_override (
    tenant_id        VARCHAR(64)    NOT NULL,
    base_currency    VARCHAR(3)     NOT NULL,
    foreign_currency VARCHAR(3)     NOT NULL,
    rate             DECIMAL(20, 8) NOT NULL,
    updated_by       VARCHAR(64)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    PRIMARY KEY (tenant_id, base_currency, foreign_currency),
    CONSTRAINT ck_fx_rate_override_rate_positive CHECK (rate > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
