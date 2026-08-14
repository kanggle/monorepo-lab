-- ===========================================================================
-- V3 — tenant axis on the two tenant-owned read-model projections
--      (TASK-BE-583 / ADR-MONO-065 D1)
--
-- ADR-MONO-064 made outbound_order.tenant_id the isolation axis of the wms
-- outbound REST plane. ADR-MONO-065 D1 extends that axis to the admin-service
-- projections of the SAME data — the order and shipment dashboards — because
-- they were serving those rows with no tenant filter at all.
--
-- Scope is deliberately TWO tables, not all fifteen. The other thirteen
-- projections (inventory, ASN, adjustments, alerts, master refs, throughput)
-- carry no owner and neither does anything upstream of them: across the six
-- wms databases (91 tables) the only tenant column is outbound_order.tenant_id
-- (ADR-MONO-065 § M2). They describe the warehouse's own operation and stay
-- warehouse-global (ADR-MONO-065 D3 / R1=a); adding a column with no upstream
-- source would make any filter a constant comparison — a guard that is
-- permanently green and protects nothing.
--
-- NULLABLE, deliberately (ADR-MONO-065 D1, mirroring ADR-MONO-064):
--   * rows projected before this migration have no tenant and are NOT
--     back-stamped — the source order may itself be tenant-less, and
--     ADR-MONO-064 forbids inventing one. Recovery is a volume reset +
--     reseed, not a data migration.
--   * orders created by an unrestricted caller (Kafka consumer, scheduler,
--     no security context) legitimately have no tenant.
-- A NOT NULL here would therefore fail on existing volumes and would require
-- exactly the back-stamp ADR-MONO-064 excluded.
-- ===========================================================================

ALTER TABLE admin_order_summary
    ADD COLUMN tenant_id VARCHAR(64) NULL;

ALTER TABLE admin_shipment_summary
    ADD COLUMN tenant_id VARCHAR(64) NULL;

COMMENT ON COLUMN admin_order_summary.tenant_id IS
    'Owning customer tenant, from the outbound.order.received.v1 envelope. '
    'NULL = no tenant resolvable (pre-ADR-MONO-064 order, or created by an '
    'unrestricted caller); visible only to unrestricted callers. ADR-MONO-065 D1.';

COMMENT ON COLUMN admin_shipment_summary.tenant_id IS
    'Owning customer tenant, inherited from the order. Stored as its own column '
    'rather than joined at read time because the order and shipment projections '
    'arrive independently. ADR-MONO-065 D1.';

-- Every tenant-scoped read filters on tenant_id first, then sorts by the
-- existing default sort key, so the index leads with tenant_id.
CREATE INDEX idx_admin_order_summary_tenant
    ON admin_order_summary (tenant_id, received_at DESC);

CREATE INDEX idx_admin_shipment_summary_tenant
    ON admin_shipment_summary (tenant_id, shipped_at DESC);
