-- finance-platform ledger-service per-ACCOUNT FX cost-flow method override (21st increment —
-- TASK-FIN-BE-029, ADR-001 D1 follow-up — per-account granularity). MySQL 8, InnoDB, utf8mb4 —
-- parity with V1..V10. Generalises the per-tenant V9 fx_cost_flow_config to per ledger account:
-- an operator may pin a specific ledger account to FIFO (or weighted-average) on top of the
-- tenant default. SettleForeignPositionUseCase resolves the effective method with the precedence
-- account override (tenant, ledger_account_code) > tenant default (tenant) > WEIGHTED_AVERAGE.
--
-- ADDITIVE — a NEW table only. NO change to V9 fx_cost_flow_config (or any existing table), NO
-- change to any existing CHECK constraint. NO backfill: absence of an account override row means
-- "fall through to the tenant default, else WEIGHTED_AVERAGE" (the use case treats an empty lookup
-- as the net-zero fall-through), so every existing tenant's settlement computation is byte-identical
-- to FIN-BE-028. updated_by/updated_at are the regulated/audit-heavy audit columns, stamped on every
-- operator upsert (last-write-wins). DATETIME(6) matches the existing audit / period / V9 columns.

-- ---------------------------------------------------------------------------
-- fx_cost_flow_account_config — one row per (tenant_id, ledger_account_code) composite PK.
-- method = the selected cost-flow algorithm for that account; the DB CHECK mirrors the
-- CostFlowMethod enum values (WEIGHTED_AVERAGE, FIFO — LIFO is IFRS-prohibited and excluded by
-- ADR-001 D1). Absence of a row for a given account means the account falls back to the per-tenant
-- config (V9), and if that is also absent, to WEIGHTED_AVERAGE (net-zero). There is NO FK to
-- ledger_account — an operator may pre-configure a code (parity with the per-tenant config, which
-- is keyed only by tenant).
-- ---------------------------------------------------------------------------
CREATE TABLE fx_cost_flow_account_config (
    tenant_id           VARCHAR(64)  NOT NULL,
    ledger_account_code VARCHAR(64)  NOT NULL,
    method              VARCHAR(20)  NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    updated_by          VARCHAR(64)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (tenant_id, ledger_account_code),
    CONSTRAINT ck_fx_cost_flow_account_method CHECK (method IN ('WEIGHTED_AVERAGE', 'FIFO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
