-- =============================================================================
-- TASK-MONO-170 — console full-stack DEMO seed — SCM inventory-visibility (globex)
-- =============================================================================
-- Applied against the per-project `scm-platform-postgres` scm_inventory_visibility
-- DB AFTER scm inventory-visibility-service is healthy (Flyway done):
--
--   docker exec -i scm-platform-postgres psql -U scm -d scm_inventory_visibility < 07-scm-inventory.sql
--
-- Rows reuse tests/federation-hardening-e2e/fixtures/seed-scm-inv.sql verbatim
-- (already tenant_id='globex-corp'). Feeds the inventory-visibility snapshot the
-- **SCM 운영** page reads (scm.local/api/v1/inventory-visibility/snapshot →
-- gateway → inventory-visibility-service). The globex assumed token's
-- entitled_domains ∋ scm is dual-accepted by the decode validator + tenant
-- filter (MONO-161/162); the controller queries tenant_id='globex-corp'.
--
-- Re-runnable: ON CONFLICT DO NOTHING.
-- =============================================================================

-- source node (FK target for snapshots)
INSERT INTO inventory_nodes (
    id, tenant_id, node_type, node_external_id, name, status,
    created_at, updated_at
) VALUES (
    'demo-node-globex-01', 'globex-corp', 'WMS_WAREHOUSE', 'GLOBEX-WH-01',
    'Globex Demo Warehouse', 'ACTIVE', NOW(), NOW()
)
ON CONFLICT (id) DO NOTHING;

-- one SKU × Node quantity row (non-empty snapshot list)
INSERT INTO inventory_snapshots (
    id, node_id, sku, quantity, tenant_id,
    last_event_id, last_event_at, version, updated_at
) VALUES (
    'demo-snapshot-globex-01', 'demo-node-globex-01', 'SKU-DEMO-001', 42.000,
    'globex-corp', 'demo-event-001', NOW(), 0, NOW()
)
ON CONFLICT (id) DO NOTHING;

-- node staleness — FRESH so the snapshot meta reports a healthy node
INSERT INTO node_staleness (
    node_id, tenant_id, last_event_at, last_event_id,
    staleness_status, last_checked_at
) VALUES (
    'demo-node-globex-01', 'globex-corp', NOW(), 'demo-event-001',
    'FRESH', NOW()
)
ON CONFLICT (node_id) DO NOTHING;
