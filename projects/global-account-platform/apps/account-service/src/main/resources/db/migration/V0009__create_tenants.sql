-- TASK-BE-228: Create tenants table for multi-tenant row-level isolation.
-- This table is the source of truth for registered tenants.
-- First seed row: 'fan-platform' (B2C consumer) — the default tenant for all existing data.
CREATE TABLE tenants (
    tenant_id    VARCHAR(32)  NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    tenant_type  VARCHAR(20)  NOT NULL,
    status       VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('fan-platform', 'Fan Platform', 'B2C_CONSUMER', 'ACTIVE', NOW(6), NOW(6));
