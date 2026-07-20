-- TASK-BE-539 — realign the dedup index with the shape the code actually writes.
--
-- V4 declared UNIQUE(event_id): one row per event. NotificationSendService loops
-- NotificationChannel.values() and saves one row per channel that resolves both a
-- template and a sender, all carrying the same event_id. So a *first* delivery of a
-- two-channel event violated the index at commit-time flush, rolled the transaction
-- back, and — because the pre-check reads the same uncommitted state and
-- DataIntegrityViolationException is retryable — burned all 3 attempts and landed in
-- the DLQ with no send record.
--
-- tenant_id is part of the key because the pre-check is tenant-scoped
-- (NotificationSendService:52 -> existsByEventId(eventId, tenantId)) while V4's index
-- was global. Leaving it out would keep the pre-check and the constraint disagreeing
-- about scope, which is the same defect class as TASK-BE-540. Event ids are unique per
-- producer, not across tenants.

-- AC-5: do not assume the replacement is lossless — prove it, and fail loudly if not.
-- The old index made duplicates on (event_id) impossible, so duplicates on the wider
-- (tenant_id, event_id, channel) key should be impossible too. That is a deduction
-- about the old constraint, not an observation about this database's rows.
DO $$
DECLARE
    offending_rows BIGINT;
BEGIN
    SELECT COUNT(*) INTO offending_rows FROM (
        SELECT 1 FROM notifications
        WHERE event_id IS NOT NULL
        GROUP BY tenant_id, event_id, channel
        HAVING COUNT(*) > 1
    ) AS duplicates;

    IF offending_rows > 0 THEN
        RAISE EXCEPTION
            'V8 aborted: % (tenant_id, event_id, channel) group(s) already hold duplicate rows; '
            'the new unique index cannot be created without losing data. Reconcile these rows first.',
            offending_rows;
    END IF;
END $$;

DROP INDEX IF EXISTS uq_notifications_event_id;

CREATE UNIQUE INDEX uq_notifications_tenant_event_channel
    ON notifications (tenant_id, event_id, channel)
    WHERE event_id IS NOT NULL;
