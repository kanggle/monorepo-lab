-- TASK-BE-372 (ADR-MONO-030 Step 4, outer tenant axis — M1;
-- ADR-MONO-031 Phase 5a notification console-absorption precondition).
--
-- Row-level tenant_id on the 3 independently-queried notification-service tables
-- (notifications / notification_templates / user_notification_preferences). Each is
-- queried on its own (unlike a parent-loaded child collection), so every table needs
-- its own tenant_id column + scoped reads.
--
-- Zero-downtime 3-step per table: ADD nullable -> backfill 'ecommerce' (default-tenant,
-- D8 net-zero) -> SET NOT NULL. All pre-existing rows belong to the single implicit
-- store, mapped to default tenant 'ecommerce'.

-- ---- notifications ----------------------------------------------------------
ALTER TABLE notifications ADD COLUMN tenant_id VARCHAR(64);
UPDATE notifications SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE notifications ALTER COLUMN tenant_id SET NOT NULL;
-- Backs the consumer "my notifications" list (findByUserIdOrderByCreatedAtDesc, now
-- tenant-scoped). Lead with tenant_id (every read is tenant-scoped), then user_id (the
-- owner guard) and created_at (the DESC ordering key).
CREATE INDEX idx_notifications_tenant_user_created ON notifications (tenant_id, user_id, created_at);

-- ---- notification_templates -------------------------------------------------
ALTER TABLE notification_templates ADD COLUMN tenant_id VARCHAR(64);
UPDATE notification_templates SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE notification_templates ALTER COLUMN tenant_id SET NOT NULL;
-- Template uniqueness becomes tenant-scoped: each tenant owns its own template per
-- (type, channel). Drop the global (type, channel) uniqueness and re-add it as
-- (tenant_id, type, channel) so a second tenant can create its own ORDER_PLACED/EMAIL
-- template without colliding with tenant 'ecommerce'.
ALTER TABLE notification_templates DROP CONSTRAINT uq_template_type_channel;
ALTER TABLE notification_templates
    ADD CONSTRAINT uq_template_tenant_type_channel UNIQUE (tenant_id, type, channel);

-- ---- user_notification_preferences ------------------------------------------
-- PK stays user_id (globally unique). tenant_id is added so preference reads scope by
-- tenant in addition to the user_id key (defensive — same user id across tenants).
ALTER TABLE user_notification_preferences ADD COLUMN tenant_id VARCHAR(64);
UPDATE user_notification_preferences SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE user_notification_preferences ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_user_pref_tenant_user ON user_notification_preferences (tenant_id, user_id);
