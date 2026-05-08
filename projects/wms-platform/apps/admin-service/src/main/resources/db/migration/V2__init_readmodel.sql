-- admin-service read-model tables (BE-046 read-side projection).
--
-- Authoritative reference:
--   specs/services/admin-service/architecture.md § Read-Model Projection Pattern
--   specs/services/admin-service/domain-model.md § 5-13 (15 read-side tables)
--   specs/services/admin-service/idempotency.md § 2 (Kafka 30d eventId dedupe + last_event_at LWW)
--   specs/contracts/events/admin-events.md § Consumed Events (18 source topics)
--
-- Schema-level invariants:
--   - Upsert tables (*_ref / *_summary / inventory_snapshot / throughput_*) carry
--     last_event_at + version. Projection writes ON CONFLICT DO UPDATE with a
--     last_event_at-guarded WHERE clause (LWW per idempotency.md § 2.5/2.6).
--   - Append-only tables (admin_adjustment_audit / admin_alert_log) use the
--     source eventId as the PK so a duplicate insert silently fails (extra
--     safety net beyond admin_event_dedupe). DB role grants on the application
--     role MUST exclude UPDATE / DELETE on these tables in production —
--     enforced by ops procedure, not by Flyway (which runs as a privileged
--     role). The exception is the alert acknowledge path, which mutates only
--     the (acknowledged_at, acknowledged_by) columns on admin_alert_log
--     (architecture.md § 1.6 Justification).
--   - admin_inventory_snapshot composite PK uses a sentinel UUID
--     '00000000-...' for the lot_id slot when the row is non-LOT-tracked. This
--     mirrors the admin_setting (key, warehouse_id) pattern from V1 — keeps
--     the PK NON NULL and JPA composite-id handling simple. The CHECK
--     constraint is informational; the adapter translates the sentinel back to
--     null at the domain boundary.
--   - All JSONB columns on this migration map to JPA entities annotated with
--     @JdbcTypeCode(SqlTypes.JSON). JsonbColumnRegressionGuardTest enforces
--     this at build time (TASK-SCM-INT-001b root cause #2 + TASK-SCM-BE-005 +
--     TASK-BE-043 regression-guard learning).
--   - No FK constraints between read-model tables. Projections are
--     independent and replayable; cross-row consistency is enforced in the
--     source services (domain-model.md § Entity Relationship Diagram).

-- ===========================================================================
-- 1. admin_warehouse_ref  (master.warehouse.* projection)
-- ===========================================================================
CREATE TABLE admin_warehouse_ref (
    id              UUID            PRIMARY KEY,
    warehouse_code  VARCHAR(40)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    timezone        VARCHAR(40)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_admin_warehouse_ref_code
    ON admin_warehouse_ref (warehouse_code);

CREATE INDEX idx_admin_warehouse_ref_last_event_at
    ON admin_warehouse_ref (last_event_at DESC);

-- ===========================================================================
-- 2. admin_zone_ref  (master.zone.* projection)
-- ===========================================================================
CREATE TABLE admin_zone_ref (
    id              UUID            PRIMARY KEY,
    warehouse_id    UUID            NOT NULL,
    zone_code       VARCHAR(40)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    zone_type       VARCHAR(40)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_zone_ref_warehouse
    ON admin_zone_ref (warehouse_id);

CREATE INDEX idx_admin_zone_ref_last_event_at
    ON admin_zone_ref (last_event_at DESC);

-- ===========================================================================
-- 3. admin_location_ref  (master.location.* projection)
-- ===========================================================================
CREATE TABLE admin_location_ref (
    id              UUID            PRIMARY KEY,
    location_code   VARCHAR(80)     NOT NULL,
    warehouse_id    UUID            NOT NULL,
    zone_id         UUID            NULL,
    location_type   VARCHAR(40)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_location_ref_warehouse
    ON admin_location_ref (warehouse_id);

CREATE INDEX idx_admin_location_ref_zone
    ON admin_location_ref (zone_id);

CREATE INDEX idx_admin_location_ref_code
    ON admin_location_ref (location_code);

CREATE INDEX idx_admin_location_ref_last_event_at
    ON admin_location_ref (last_event_at DESC);

-- ===========================================================================
-- 4. admin_sku_ref  (master.sku.* projection)
-- ===========================================================================
CREATE TABLE admin_sku_ref (
    id              UUID            PRIMARY KEY,
    sku_code        VARCHAR(40)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    base_uom        VARCHAR(20)     NULL,
    tracking_type   VARCHAR(20)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_admin_sku_ref_code
    ON admin_sku_ref (sku_code);

CREATE INDEX idx_admin_sku_ref_last_event_at
    ON admin_sku_ref (last_event_at DESC);

-- ===========================================================================
-- 5. admin_lot_ref  (master.lot.* projection)
-- ===========================================================================
CREATE TABLE admin_lot_ref (
    id              UUID            PRIMARY KEY,
    sku_id          UUID            NOT NULL,
    lot_no          VARCHAR(80)     NOT NULL,
    expiry_date     DATE            NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_lot_ref_sku
    ON admin_lot_ref (sku_id);

CREATE INDEX idx_admin_lot_ref_lot_no
    ON admin_lot_ref (lot_no);

CREATE INDEX idx_admin_lot_ref_last_event_at
    ON admin_lot_ref (last_event_at DESC);

-- ===========================================================================
-- 6. admin_partner_ref  (master.partner.* projection)
-- ===========================================================================
CREATE TABLE admin_partner_ref (
    id              UUID            PRIMARY KEY,
    partner_code    VARCHAR(40)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    partner_type    VARCHAR(40)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_admin_partner_ref_code
    ON admin_partner_ref (partner_code);

CREATE INDEX idx_admin_partner_ref_last_event_at
    ON admin_partner_ref (last_event_at DESC);

-- ===========================================================================
-- 7. admin_asn_summary  (inbound.asn.* projection)
-- ===========================================================================
CREATE TABLE admin_asn_summary (
    asn_id                  UUID            PRIMARY KEY,
    asn_no                  VARCHAR(80)     NOT NULL,
    warehouse_id            UUID            NOT NULL,
    supplier_partner_id     UUID            NULL,
    supplier_name           VARCHAR(200)    NULL,
    status                  VARCHAR(40)     NOT NULL,
    source                  VARCHAR(40)     NULL,
    expected_arrive_date    DATE            NULL,
    line_count              INTEGER         NOT NULL DEFAULT 0,
    received_at             TIMESTAMPTZ     NULL,
    closed_at               TIMESTAMPTZ     NULL,
    last_event_at           TIMESTAMPTZ     NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_asn_summary_warehouse
    ON admin_asn_summary (warehouse_id);

CREATE INDEX idx_admin_asn_summary_status
    ON admin_asn_summary (status);

CREATE INDEX idx_admin_asn_summary_received_at
    ON admin_asn_summary (received_at DESC);

CREATE INDEX idx_admin_asn_summary_supplier
    ON admin_asn_summary (supplier_partner_id);

-- ===========================================================================
-- 8. admin_inspection_summary  (inbound.inspection.completed projection — 1:1 per ASN)
-- ===========================================================================
CREATE TABLE admin_inspection_summary (
    asn_id                      UUID            PRIMARY KEY,
    warehouse_id                UUID            NOT NULL,
    inspection_completed_at     TIMESTAMPTZ     NOT NULL,
    inspector_id                VARCHAR(120)    NULL,
    total_lines                 INTEGER         NOT NULL DEFAULT 0,
    discrepancy_count           INTEGER         NOT NULL DEFAULT 0,
    total_qty_expected          INTEGER         NOT NULL DEFAULT 0,
    total_qty_passed            INTEGER         NOT NULL DEFAULT 0,
    total_qty_damaged           INTEGER         NOT NULL DEFAULT 0,
    total_qty_short             INTEGER         NOT NULL DEFAULT 0,
    last_event_at               TIMESTAMPTZ     NOT NULL,
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_inspection_summary_warehouse
    ON admin_inspection_summary (warehouse_id);

-- ===========================================================================
-- 9. admin_order_summary  (outbound.order.* projection)
-- ===========================================================================
CREATE TABLE admin_order_summary (
    order_id                UUID            PRIMARY KEY,
    order_no                VARCHAR(80)     NOT NULL,
    warehouse_id            UUID            NOT NULL,
    customer_partner_id     UUID            NULL,
    customer_name           VARCHAR(200)    NULL,
    status                  VARCHAR(40)     NOT NULL,
    source                  VARCHAR(40)     NULL,
    required_ship_date      DATE            NULL,
    line_count              INTEGER         NOT NULL DEFAULT 0,
    saga_state              VARCHAR(40)     NULL,
    received_at             TIMESTAMPTZ     NULL,
    shipped_at              TIMESTAMPTZ     NULL,
    last_event_at           TIMESTAMPTZ     NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_order_summary_warehouse
    ON admin_order_summary (warehouse_id);

CREATE INDEX idx_admin_order_summary_status
    ON admin_order_summary (status);

CREATE INDEX idx_admin_order_summary_received_at
    ON admin_order_summary (received_at DESC);

CREATE INDEX idx_admin_order_summary_customer
    ON admin_order_summary (customer_partner_id);

-- ===========================================================================
-- 10. admin_shipment_summary  (outbound.shipping.confirmed projection)
-- ===========================================================================
CREATE TABLE admin_shipment_summary (
    shipment_id     UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL,
    order_no        VARCHAR(80)     NULL,
    warehouse_id    UUID            NOT NULL,
    shipment_no     VARCHAR(80)     NULL,
    carrier_code    VARCHAR(40)     NULL,
    tracking_no     VARCHAR(120)    NULL,
    shipped_at      TIMESTAMPTZ     NOT NULL,
    total_qty       INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_shipment_summary_order
    ON admin_shipment_summary (order_id);

CREATE INDEX idx_admin_shipment_summary_warehouse
    ON admin_shipment_summary (warehouse_id);

CREATE INDEX idx_admin_shipment_summary_shipped_at
    ON admin_shipment_summary (shipped_at DESC);

-- ===========================================================================
-- 11. admin_inventory_snapshot  (inventory.* projection — primary dashboard)
--      Composite PK (location_id, sku_id, lot_id). Sentinel UUID for null
--      lot_id matches the V1 admin_setting pattern.
-- ===========================================================================
CREATE TABLE admin_inventory_snapshot (
    location_id         UUID            NOT NULL,
    sku_id              UUID            NOT NULL,
    lot_id              UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL,
    location_code       VARCHAR(80)     NULL,
    sku_code            VARCHAR(40)     NULL,
    lot_no              VARCHAR(80)     NULL,
    available_qty       INTEGER         NOT NULL DEFAULT 0,
    reserved_qty        INTEGER         NOT NULL DEFAULT 0,
    damaged_qty         INTEGER         NOT NULL DEFAULT 0,
    on_hand_qty         INTEGER         NOT NULL DEFAULT 0,
    low_stock_flag      BOOLEAN         NOT NULL DEFAULT FALSE,
    last_adjusted_at    TIMESTAMPTZ     NULL,
    last_event_at       TIMESTAMPTZ     NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (location_id, sku_id, lot_id)
);

CREATE INDEX idx_admin_inventory_snapshot_warehouse
    ON admin_inventory_snapshot (warehouse_id);

CREATE INDEX idx_admin_inventory_snapshot_low_stock
    ON admin_inventory_snapshot (warehouse_id)
    WHERE low_stock_flag = TRUE;

CREATE INDEX idx_admin_inventory_snapshot_last_event_at
    ON admin_inventory_snapshot (last_event_at DESC);

-- ===========================================================================
-- 12. admin_adjustment_audit  (inventory.adjusted projection — append-only)
--     PK = source eventId so duplicate insert silently fails.
-- ===========================================================================
CREATE TABLE admin_adjustment_audit (
    id              UUID            PRIMARY KEY,
    location_id     UUID            NOT NULL,
    sku_id          UUID            NOT NULL,
    lot_id          UUID            NULL,
    warehouse_id    UUID            NOT NULL,
    bucket          VARCHAR(40)     NOT NULL,
    delta           INTEGER         NOT NULL,
    reason_code     VARCHAR(60)     NULL,
    reason_note     VARCHAR(500)    NULL,
    actor_id        VARCHAR(120)    NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    projected_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_adjustment_audit_warehouse
    ON admin_adjustment_audit (warehouse_id);

CREATE INDEX idx_admin_adjustment_audit_occurred_at
    ON admin_adjustment_audit (occurred_at DESC);

CREATE INDEX idx_admin_adjustment_audit_sku
    ON admin_adjustment_audit (sku_id);

CREATE INDEX idx_admin_adjustment_audit_location
    ON admin_adjustment_audit (location_id);

-- ===========================================================================
-- 13. admin_alert_log  (inventory.alert projection — append-only + ack mutation)
--     PK = source eventId. acknowledged_at + acknowledged_by are the only
--     mutable columns from the application layer (architecture.md § 1.6).
-- ===========================================================================
CREATE TABLE admin_alert_log (
    id                  UUID            PRIMARY KEY,
    alert_type          VARCHAR(40)     NOT NULL,
    warehouse_id        UUID            NULL,
    location_id         UUID            NULL,
    sku_id              UUID            NULL,
    lot_id              UUID            NULL,
    threshold_qty       INTEGER         NULL,
    actual_qty          INTEGER         NULL,
    detected_at         TIMESTAMPTZ     NOT NULL,
    acknowledged_at     TIMESTAMPTZ     NULL,
    acknowledged_by     VARCHAR(120)    NULL,
    projected_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_alert_log_type
        CHECK (alert_type IN ('LOW_STOCK', 'ANOMALY'))
);

CREATE INDEX idx_admin_alert_log_warehouse
    ON admin_alert_log (warehouse_id);

CREATE INDEX idx_admin_alert_log_detected_at
    ON admin_alert_log (detected_at DESC);

CREATE INDEX idx_admin_alert_log_unacked
    ON admin_alert_log (warehouse_id, detected_at DESC)
    WHERE acknowledged_at IS NULL;

-- ===========================================================================
-- 14. admin_throughput_inbound_daily  (inbound.putaway.completed counter)
-- ===========================================================================
CREATE TABLE admin_throughput_inbound_daily (
    date            DATE            NOT NULL,
    warehouse_id    UUID            NOT NULL,
    putaway_count   INTEGER         NOT NULL DEFAULT 0,
    qty_received    INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (date, warehouse_id)
);

CREATE INDEX idx_admin_throughput_inbound_daily_warehouse
    ON admin_throughput_inbound_daily (warehouse_id);

-- ===========================================================================
-- 15. admin_throughput_outbound_daily  (outbound.shipping.confirmed counter)
-- ===========================================================================
CREATE TABLE admin_throughput_outbound_daily (
    date            DATE            NOT NULL,
    warehouse_id    UUID            NOT NULL,
    shipment_count  INTEGER         NOT NULL DEFAULT 0,
    qty_shipped     INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (date, warehouse_id)
);

CREATE INDEX idx_admin_throughput_outbound_daily_warehouse
    ON admin_throughput_outbound_daily (warehouse_id);
