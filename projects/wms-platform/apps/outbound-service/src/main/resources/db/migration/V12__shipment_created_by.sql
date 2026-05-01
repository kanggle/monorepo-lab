-- TASK-BE-040: Surface Shipment.created_by per outbound-service-api.md §4.1
-- (ShipmentResponse includes the field). Adds created_by column to shipment.

ALTER TABLE shipment
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
