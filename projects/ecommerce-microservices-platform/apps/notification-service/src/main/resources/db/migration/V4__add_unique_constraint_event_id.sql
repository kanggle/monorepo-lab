-- Add unique constraint on event_id for idempotent notification processing.
-- Uses a partial unique index to allow NULL values (existing records may have NULL event_id).
CREATE UNIQUE INDEX uq_notifications_event_id ON notifications(event_id) WHERE event_id IS NOT NULL;
