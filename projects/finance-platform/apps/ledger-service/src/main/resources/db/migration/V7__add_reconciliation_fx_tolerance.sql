-- finance-platform ledger-service configurable FX reconciliation tolerance (13th
-- increment, TASK-FIN-BE-020). MySQL 8, InnoDB, utf8mb4 — parity with V1..V6. A
-- per-tenant base-leg (KRW) FX tolerance so within-threshold FX-rounding differences
-- between the bank-reported base value and the ledger's carrying base match cleanly
-- instead of each raising an AMOUNT_MISMATCH discrepancy for operator review.
--
-- ADDITIVE — a NEW table only. NO change to any existing table, and NO change to the
-- V4 ck_recon_discrepancy_type CHECK (AMOUNT_MISMATCH stays in the allow-list). NO
-- backfill: absence of a row means FxTolerance.EXACT (the use case treats an empty
-- lookup as the net-zero exact compare), so every existing tenant's reconciliation is
-- byte-identical to FIN-BE-017.
--
-- bps/floor are numeric (basis points + KRW minor units) so the table is
-- base-currency-agnostic (no VARCHAR(3) currency column). updated_by/updated_at are
-- the regulated/audit-heavy audit columns, stamped on every operator upsert
-- (last-write-wins). DATETIME(6) matches the existing audit/period timestamp columns.

-- ---------------------------------------------------------------------------
-- reconciliation_fx_tolerance — one row per tenant (tenant_id PK). tolerance_bps =
-- basis points (万分율) of the internal carrying-base magnitude; floor_minor = an
-- absolute floor in base/KRW minor units. The matcher's allowed band is the LOOSER
-- (larger) of the two; both default to 0 (EXACT) and are CHECK >= 0 (the structural
-- backstop for the VALIDATION_ERROR the use case raises on a negative input).
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_fx_tolerance (
    tenant_id       VARCHAR(64)  NOT NULL,
    tolerance_bps   INT          NOT NULL DEFAULT 0,
    floor_minor     BIGINT       NOT NULL DEFAULT 0,
    updated_by      VARCHAR(64)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_recon_fx_tolerance_bps   CHECK (tolerance_bps >= 0),
    CONSTRAINT ck_recon_fx_tolerance_floor CHECK (floor_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
