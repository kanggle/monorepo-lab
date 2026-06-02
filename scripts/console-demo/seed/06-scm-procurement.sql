-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — SCM procurement (globex-corp)
-- =============================================================================
-- Applied against the per-project `scm-platform-postgres` scm_procurement DB
-- AFTER scm procurement-service is healthy (Flyway done):
--
--   docker exec -i scm-platform-postgres psql -U scm -d scm_procurement < 06-scm-procurement.sql
--
-- Rows reuse tests/federation-hardening-e2e/fixtures/seed-scm.sql but are
-- RE-SCOPED to tenant_id='globex-corp' (NOT the fixture's '*'). Rationale: the
-- SCM PO read-model is tenant-FILTERED. The **SCM 운영** page (PO list +
-- inventory-visibility) is reachable only when the active tenant entitles scm —
-- i.e. globex-corp ([scm,erp]). The globex assumed token carries
-- tenant_id='globex-corp'; the gateway + procurement-service query that tenant,
-- so the rows must be globex-corp-scoped to render non-empty. The console
-- reaches these via the scm GATEWAY (scm.local/api/v1/procurement/po), which
-- maps to procurement-service.
--
-- Re-runnable: ON CONFLICT (id) DO NOTHING.
-- =============================================================================

-- supplier (required FK for purchase_orders.supplier_id)
INSERT INTO suppliers (
    id, tenant_id, name, status, created_at, updated_at, version
) VALUES (
    'demo-supplier-globex-001', 'globex-corp', 'Globex Demo Supplier', 'ACTIVE',
    NOW(), NOW(), 0
)
ON CONFLICT (id) DO NOTHING;

-- purchase_order — minimum columns for the PO list endpoint.
INSERT INTO purchase_orders (
    id, tenant_id, po_number, supplier_id, buyer_account_id,
    status, total_amount, currency, created_at, updated_at, version
) VALUES (
    'demo-po-globex-001', 'globex-corp', 'PO-DEMO-001',
    'demo-supplier-globex-001', 'demo-buyer-001',
    'DRAFT', 100.00, 'KRW', NOW(), NOW(), 0
)
ON CONFLICT (id) DO NOTHING;
