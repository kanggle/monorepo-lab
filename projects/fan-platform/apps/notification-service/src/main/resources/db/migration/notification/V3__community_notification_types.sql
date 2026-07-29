-- TASK-FAN-BE-026: consume the community.* interaction events
-- (community.comment.added.v1 / community.reaction.added.v1) and record
-- REPLY / MENTION / REACTION_BADGE notifications.
--
-- Community-sourced notifications have NO originating membership aggregate; they
-- correlate to a post instead. Three schema changes follow from that:
--
--  1. membership_id becomes NULLABLE. Membership-sourced rows (WELCOME /
--     CANCELLATION / EXPIRY_REMINDER) keep populating it exactly as before;
--     community-sourced rows leave it NULL. No CHECK enforcing
--     "exactly one of (membership_id, post_id)" is added — that invariant is
--     owned by the use case (one origin per consumer), and a DB-level
--     mutual-exclusion constraint is not in this task's scope.
--  2. post_id is added, NULLABLE — the correlating aggregate id for a
--     community-sourced notification; NULL for membership-sourced rows.
--  3. ck_notification_type is extended with the three new values (same
--     DROP/ADD CONSTRAINT shape as V2).
--
-- §16 (feedback_spring_boot_diagnostic_patterns): a CHECK allow-list change is a
-- migration; a Docker-free :check slice will NOT catch an INSERT of a new value
-- against the old constraint, so the Testcontainers IT is the authoritative gate.

ALTER TABLE notifications ALTER COLUMN membership_id DROP NOT NULL;

ALTER TABLE notifications ADD COLUMN post_id VARCHAR(36);

ALTER TABLE notifications DROP CONSTRAINT ck_notification_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('WELCOME', 'CANCELLATION', 'EXPIRY_REMINDER',
                    'REPLY', 'MENTION', 'REACTION_BADGE'));

-- ---------------------------------------------------------------------------
-- Idempotency guard widened from (source_event_id) to
-- (source_event_id, account_id, type).
--
-- WHY: one community.comment.added event can legitimately fan out to MORE THAN
-- ONE recipient — a REPLY to the post author plus a MENTION per mentioned
-- account. Under the V1 single-column UNIQUE those sibling rows would collide,
-- so the fan-out could never be written. The composite key preserves the
-- secondary-guard semantics exactly (architecture.md § Idempotency): a duplicate
-- delivery of the same eventId regenerates the SAME (event, recipient, type)
-- tuples, so it still collides. The primary guard remains the processed_events
-- table keyed on eventId.
--
-- Membership events are unaffected: one event → one recipient → one type, so the
-- composite key degenerates to the old behaviour for them.
-- ---------------------------------------------------------------------------
ALTER TABLE notifications DROP CONSTRAINT uq_notification_source_event;
ALTER TABLE notifications ADD CONSTRAINT uq_notification_source_event
    UNIQUE (source_event_id, account_id, type);

-- Community-sourced notifications are looked up / debugged by post; the inbox
-- read path itself stays (tenant_id, account_id, created_at DESC).
CREATE INDEX idx_notifications_post ON notifications (tenant_id, post_id);
