-- TASK-BE-038 — picking / packing / shipment schema alignment.
--
-- V3 and V4 created bootstrap tables that are missing fields the spec requires.
-- This migration ADD COLUMNs missing fields without dropping anything (safe for
-- forward compatibility). Domain-model.md sections referenced inline per table.
--
-- All ADDs are nullable so existing rows tolerate the migration; the application
-- populates them on every new insert.

-- picking_request — domain-model.md §2
-- V3 has: id, order_id, warehouse_id, status, created_at, updated_at, version
-- Missing: saga_id (cross-reference to OutboundSaga so inventory replies can
-- correlate). Stored on PickingRequest itself per §2.
ALTER TABLE picking_request
    ADD COLUMN IF NOT EXISTS saga_id UUID;

CREATE INDEX IF NOT EXISTS idx_picking_request_saga_id
    ON picking_request(saga_id);

-- One PickingRequest per Order (§2 invariant).
CREATE UNIQUE INDEX IF NOT EXISTS idx_picking_request_order_id_unique
    ON picking_request(order_id);

-- picking_confirmation — domain-model.md §3
-- V3 has: id, picking_request_id, confirmed_by, confirmed_at
-- Missing: order_id (denormalised), notes
ALTER TABLE picking_confirmation
    ADD COLUMN IF NOT EXISTS order_id UUID,
    ADD COLUMN IF NOT EXISTS notes    VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_picking_confirmation_order_id
    ON picking_confirmation(order_id);

-- picking_confirmation_line — domain-model.md §3 (PickingConfirmationLine)
-- V3 has: id, picking_confirmation_id, picking_line_id, picked_qty
-- Missing per spec: order_line_id, sku_id, lot_id, actual_location_id,
-- qty_confirmed (we add the new columns; picking_line_id and picked_qty are
-- retained for zero-downtime).
ALTER TABLE picking_confirmation_line
    ADD COLUMN IF NOT EXISTS order_line_id        UUID,
    ADD COLUMN IF NOT EXISTS sku_id               UUID,
    ADD COLUMN IF NOT EXISTS lot_id               UUID,
    ADD COLUMN IF NOT EXISTS actual_location_id   UUID,
    ADD COLUMN IF NOT EXISTS qty_confirmed        INT;

-- packing_unit — domain-model.md §4
-- V4 has: id, order_id, tracking_number, status, packed_at, created_at
-- Missing: carton_no, packing_type, weight_grams, length/width/height_mm,
-- notes, version, updated_at.
ALTER TABLE packing_unit
    ADD COLUMN IF NOT EXISTS carton_no    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS packing_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS weight_grams INT,
    ADD COLUMN IF NOT EXISTS length_mm    INT,
    ADD COLUMN IF NOT EXISTS width_mm     INT,
    ADD COLUMN IF NOT EXISTS height_mm    INT,
    ADD COLUMN IF NOT EXISTS notes        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS version      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- carton_no unique within the order (not globally) — §4 invariant.
CREATE UNIQUE INDEX IF NOT EXISTS idx_packing_unit_carton_no
    ON packing_unit(order_id, carton_no)
    WHERE carton_no IS NOT NULL;

-- packing_unit_line — domain-model.md §4 (PackingUnitLine)
-- V4 has: id, packing_unit_id, order_line_id, sku_id, lot_id, packed_qty
-- Spec field name is `qty` — keep packed_qty as the canonical column to avoid
-- a column rename (zero-downtime). The persistence adapter maps domain `qty`
-- to packed_qty.
-- No additions needed.

-- shipment — domain-model.md §5
-- V4 has: id, order_id, carrier, tracking_number, status, shipped_at, created_at
-- Missing: shipment_no (globally unique), tms_status, tms_notified_at,
-- tms_request_id, version, updated_at.
ALTER TABLE shipment
    ADD COLUMN IF NOT EXISTS shipment_no      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tms_status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS tms_notified_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tms_request_id   UUID,
    ADD COLUMN IF NOT EXISTS version          BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- shipment_no globally unique — §5 invariant.
CREATE UNIQUE INDEX IF NOT EXISTS idx_shipment_shipment_no
    ON shipment(shipment_no)
    WHERE shipment_no IS NOT NULL;

-- One shipment per order in v1 (§5 invariant; see also outbound-service-api.md
-- §4.1 which declares the 1:1 relationship).
CREATE UNIQUE INDEX IF NOT EXISTS idx_shipment_order_id_unique
    ON shipment(order_id);
