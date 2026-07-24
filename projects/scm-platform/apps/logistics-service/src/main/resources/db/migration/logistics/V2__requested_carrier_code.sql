-- logistics-service — multi-vendor routing signal (TASK-SCM-BE-043).
-- ADR-MONO-053 §D3: CarrierRouter selects the vendor from the shipment's REQUESTED
-- carrier code (the routing INPUT carried by the outbound.shipping.confirmed seam).
--
-- Additive + NULLABLE (V1 checksum untouched — never edit V1__init.sql):
--   requested_carrier_code = the carrier requested by the upstream event (routing input).
-- Distinct from carrier_code, which is the CONFIRMED carrier resolved from the vendor
-- ack and set on DISPATCHED. A DISPATCH_FAILED dispatch never set carrier_code/vendor,
-- so requested_carrier_code is what lets :retry re-route it to the correct vendor.
ALTER TABLE dispatch
    ADD COLUMN requested_carrier_code VARCHAR(64);
