-- finance-platform ledger-service per-tenant FX cost-flow method config (15th increment —
-- TASK-FIN-BE-023, ADR-001 D1 step 1). MySQL 8, InnoDB, utf8mb4 — parity with V1..V8.
-- Stores the operator-selected cost-flow method (WEIGHTED_AVERAGE | FIFO) per tenant so
-- FIN-BE-025 can read it when wiring FIFO consumption into SettleForeignPositionUseCase.
--
-- ADDITIVE — a NEW table only. NO change to any existing table, NO change to any existing
-- CHECK constraint. NO backfill: absence of a row means WEIGHTED_AVERAGE (the use case
-- treats an empty lookup as the net-zero weighted-average default), so every existing
-- tenant's settlement computation is byte-identical to FIN-BE-018 (shadow — the config
-- surface is wired but SettleForeignPositionUseCase does NOT yet branch on it; that is
-- FIN-BE-025). updated_by/updated_at are the regulated/audit-heavy audit columns, stamped
-- on every operator upsert (last-write-wins). DATETIME(6) matches the existing audit /
-- period timestamp columns.

-- ---------------------------------------------------------------------------
-- fx_cost_flow_config — one row per tenant (tenant_id PK). method = the selected
-- cost-flow algorithm; the DB CHECK mirrors the CostFlowMethod enum values
-- (WEIGHTED_AVERAGE, FIFO — LIFO is IFRS-prohibited and excluded by ADR-001 D1).
-- Absence of a row is functionally equivalent to method = WEIGHTED_AVERAGE (net-zero).
-- ---------------------------------------------------------------------------
CREATE TABLE fx_cost_flow_config (
    tenant_id   VARCHAR(64)  NOT NULL,
    method      VARCHAR(20)  NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    updated_by  VARCHAR(64)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_fx_cost_flow_method CHECK (method IN ('WEIGHTED_AVERAGE', 'FIFO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
