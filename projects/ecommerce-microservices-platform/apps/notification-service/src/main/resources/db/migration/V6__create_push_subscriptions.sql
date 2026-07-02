-- TASK-BE-464 — Web Push (VAPID) subscription registry.
--
-- Stores per-browser Web Push subscriptions so the PUSH channel can deliver to a
-- concrete endpoint (unlike email, a push recipient is not an address but a browser
-- subscription: endpoint URL + the p256dh/auth key pair). A user may hold many
-- subscriptions (one per browser/device).
--
-- tenant_id follows the M1 pattern of the other notification tables (V5): every row
-- carries the owning tenant; HTTP register/unregister/read surfaces scope by it.
-- The send path (Kafka thread, no HTTP TenantContext) looks subscriptions up by the
-- globally-unique user_id from the event, so it stays tenant-correct without a context.

CREATE TABLE push_subscriptions (
    id           VARCHAR(36)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,
    user_id      VARCHAR(255) NOT NULL,
    endpoint     TEXT         NOT NULL,
    p256dh       VARCHAR(255) NOT NULL,
    auth         VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_push_subscriptions PRIMARY KEY (id),
    -- A browser subscription endpoint is unique within a tenant; re-registering the
    -- same endpoint updates its keys (upsert) rather than creating a duplicate row.
    CONSTRAINT uq_push_subscriptions_tenant_endpoint UNIQUE (tenant_id, endpoint)
);

-- Send-path lookup key: resolve all of a user's active subscriptions by user_id.
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
