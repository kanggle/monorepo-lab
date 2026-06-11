-- TASK-FAN-BE-014: extend the notification type CHECK allow-list with
-- EXPIRY_REMINDER (consumed from fan.membership.expired.v1, newly emitted by the
-- membership-service expiry sweeper).
--
-- §16 (feedback_spring_boot_diagnostic_patterns): a CHECK allow-list change is a
-- migration; a Docker-free :check slice will NOT catch an INSERT of the new value
-- against the old constraint, so the Testcontainers IT is the authoritative gate.
ALTER TABLE notifications DROP CONSTRAINT ck_notification_type;
ALTER TABLE notifications ADD CONSTRAINT ck_notification_type
    CHECK (type IN ('WELCOME', 'CANCELLATION', 'EXPIRY_REMINDER'));
