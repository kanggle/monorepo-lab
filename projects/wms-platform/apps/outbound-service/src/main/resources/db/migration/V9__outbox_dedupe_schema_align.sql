-- Schema alignment for outbound_outbox and outbound_event_dedupe.
-- Adds columns declared in specs/services/outbound-service/domain-model.md §7 and §8
-- that were missing from V6__init_outbox_dedupe.sql.
--
-- V6 is Flyway-tracked and cannot be edited. This migration applies the delta only.
--
-- outbound_outbox gaps vs domain-model.md §7:
--   - aggregate_type  VARCHAR(40)  "ORDER" / "OUTBOUND_SAGA" / "SHIPMENT"
--   - event_version   VARCHAR(10)  "v1"
--   - partition_key   VARCHAR(60)  saga_id for saga events; order_id for order lifecycle events
-- Note: V6 columns status/retry_count are publisher-implementation detail columns
-- retained for the outbox publisher (TASK-BE-036). They are not in §7 because §7
-- describes the logical model; publisher-tracking columns are added here alongside
-- the logical columns.
--
-- outbound_event_dedupe gap vs domain-model.md §8:
--   - outcome  VARCHAR(30)  "APPLIED" / "IGNORED_DUPLICATE" / "FAILED"

ALTER TABLE outbound_outbox
    ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS event_version  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS partition_key  VARCHAR(60);

-- For existing rows (none expected in production at migration time; dev seed rows
-- tolerate NULL until backfilled by the publisher).
-- No NOT NULL constraint here to allow zero-downtime deployment; the publisher
-- (TASK-BE-036) will populate these on every new insert.

ALTER TABLE outbound_event_dedupe
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(30);

-- outcome is nullable for existing rows. New rows written by the EventDedupe adapter
-- (after TASK-BE-035) will always supply the value.
