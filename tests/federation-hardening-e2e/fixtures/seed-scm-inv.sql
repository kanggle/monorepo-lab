-- =============================================================================
-- TASK-MONO-162 — Federation Hardening e2e scm inventory-visibility seed (phase 2.5)
-- =============================================================================
-- Applied against scm-inv-postgres via psql AFTER scm-inventory-visibility-service
-- is healthy. PostgreSQL (scm_inventory_visibility DB — application.yml confirms
-- jdbc:postgresql://.../scm_inventory_visibility).
--
-- Read-model the GET /api/inventory-visibility/snapshot endpoint reads:
--   inventory_nodes      — registered source node (FK target for snapshots)
--   inventory_snapshots  — SKU × Node quantity rows (the snapshot payload)
--   node_staleness       — per-node staleness (joined into meta.staleness)
-- Schema derived from db/migration/inventory-visibility/V1__init.sql.
--
-- Tenant: the snapshot endpoint filters by the JWT tenant_id claim
-- (TenantClaimExtractor). In the tenant-switch-rescope.spec.ts B-side the
-- operator's assumed token carries tenant_id=globex-corp + entitled_domains=[scm,erp];
-- the decode validator + filter dual-accept it (entitled_domains ∋ scm), and the
-- controller queries tenant_id='globex-corp'. Seeding that tenant yields a
-- genuine non-empty "ok" card (the spec only requires NOT forbidden, so an empty
-- 200 would also pass, but a real row makes the proof unambiguous).
--
-- Re-runnable: every INSERT is idempotent via ON CONFLICT DO NOTHING.
-- =============================================================================

-- inventory_node — the source node the snapshot rows reference.
INSERT INTO inventory_nodes (
    id, tenant_id, node_type, node_external_id, name, status,
    created_at, updated_at
) VALUES (
    'e2e-node-globex-01',
    'globex-corp',
    'WMS_WAREHOUSE',
    'GLOBEX-WH-01',
    'Globex E2E Warehouse',
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- inventory_snapshot — one SKU × Node quantity row (non-empty snapshot list).
INSERT INTO inventory_snapshots (
    id, node_id, sku, quantity, tenant_id,
    last_event_id, last_event_at, version, updated_at
) VALUES (
    'e2e-snapshot-globex-01',
    'e2e-node-globex-01',
    'SKU-E2E-001',
    42.000,
    'globex-corp',
    'e2e-event-001',
    NOW(),
    0,
    NOW()
)
ON CONFLICT (id) DO NOTHING;

-- node_staleness — FRESH status so the snapshot meta reports a healthy node.
INSERT INTO node_staleness (
    node_id, tenant_id, last_event_at, last_event_id,
    staleness_status, last_checked_at
) VALUES (
    'e2e-node-globex-01',
    'globex-corp',
    NOW(),
    'e2e-event-001',
    'FRESH',
    NOW()
)
ON CONFLICT (node_id) DO NOTHING;
