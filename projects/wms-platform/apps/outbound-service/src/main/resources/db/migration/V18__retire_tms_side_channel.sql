-- TASK-BE-560 (ADR-MONO-053 §D8): retire the outbound-service TMS notification
-- side-channel.
--
-- Carrier dispatch was relocated to the scm logistics-service (BE-042/043/044),
-- and the platform-console recovery action was repointed to logistics :retry
-- (PC-FE-258). Nothing external calls the wms TMS path any more, so the whole
-- side-channel is removed: the after-commit push, the :retry-tms-notify recovery,
-- the SHIPPED_NOT_NOTIFIED alert state, the tms_request_dedupe table, and the
-- shipment.tms_* columns.
--
-- Additive, forward-only migration (existing V*.sql are unchanged — their Flyway
-- checksums stay intact).

-- 1. Data migration: rejoin any SHIPPED_NOT_NOTIFIED saga to the MAIN SHIPPED
--    branch — NOT straight to COMPLETED. SHIPPED_NOT_NOTIFIED meant "shipped +
--    published, TMS push failed"; the correct post-D8 home is SHIPPED, awaiting
--    (or having already received) inventory.confirmed. A row that never got
--    inventory.confirmed must still pass through stock confirmation: the saga
--    sweeper re-emits outbound.shipping.confirmed and the idempotent inventory
--    re-confirm settles it to COMPLETED. Likely 0 rows in practice (the stub
--    adapter always acked), but correct regardless. The saga state column is
--    `status` (V5). `failure_reason` is retained — it is shared with the
--    reserve-failed and sweeper-exhaustion paths, not TMS-exclusive.
UPDATE outbound_saga
   SET status = 'SHIPPED',
       failure_reason = NULL,
       updated_at = NOW()
 WHERE status = 'SHIPPED_NOT_NOTIFIED';

-- 2. Drop the TMS vendor-idempotency dedupe table (V4 draft / V13).
DROP TABLE IF EXISTS tms_request_dedupe;

-- 3. Drop the TMS notification-tracking columns on shipment (added by V11).
--    The generic, bootstrap-era `status` column (V4) is retained — it is not
--    TMS-exclusive.
ALTER TABLE shipment
    DROP COLUMN IF EXISTS tms_status,
    DROP COLUMN IF EXISTS tms_notified_at,
    DROP COLUMN IF EXISTS tms_request_id;
