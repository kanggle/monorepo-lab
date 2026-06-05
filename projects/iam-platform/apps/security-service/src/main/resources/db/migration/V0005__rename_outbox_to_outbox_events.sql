-- Corrective migration: rename 'outbox' to 'outbox_events' to match spec
-- (specs/services/security-service/architecture.md declares 'outbox_events')
RENAME TABLE outbox TO outbox_events;
