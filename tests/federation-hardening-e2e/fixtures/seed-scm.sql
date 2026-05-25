-- =============================================================================
-- TASK-MONO-139 — Phase 8 Federation Hardening e2e scm seed (phase 2.5)
-- =============================================================================
-- Applied against scm-postgres via psql AFTER scm-procurement-service is healthy.
-- PostgreSQL (scm uses PostgreSQL — application.yml confirms
-- jdbc:postgresql://.../scm_procurement).
--
-- One supplier row + one purchase_order row — minimum shape to satisfy
-- scm-golden-path.spec.ts. tenant_id='*' (platform-scope sentinel accepted
-- by TenantClaimValidator per ADR-018 § 3.3 zero-retrofit confirmation).
--
-- Re-runnable: every INSERT is idempotent via ON CONFLICT DO NOTHING.
-- =============================================================================

-- supplier row (required FK for purchase_orders.supplier_id)
INSERT INTO suppliers (
    id, tenant_id, name, status,
    created_at, updated_at, version
) VALUES (
    'e2e-supplier-001',
    '*',
    'E2E Test Supplier',
    'ACTIVE',
    NOW(),
    NOW(),
    0
)
ON CONFLICT (id) DO NOTHING;

-- purchase_order row — minimum columns for PO list endpoint.
INSERT INTO purchase_orders (
    id, tenant_id, po_number, supplier_id, buyer_account_id,
    status, total_amount, currency,
    created_at, updated_at, version
) VALUES (
    'e2e-po-001',
    '*',
    'PO-E2E-001',
    'e2e-supplier-001',
    'e2e-buyer-001',
    'DRAFT',
    100.00,
    'KRW',
    NOW(),
    NOW(),
    0
)
ON CONFLICT (id) DO NOTHING;
