-- TASK-BE-294: idempotency store for inbound carrier webhook deliveries.
-- Kept separate from processed_events (Kafka event dedup) so the two have independent
-- retention; carriers retry deliveries, so each delivery_id takes effect at most once.
CREATE TABLE processed_carrier_webhooks (
    delivery_id VARCHAR(255) NOT NULL,
    received_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_carrier_webhooks PRIMARY KEY (delivery_id)
);

CREATE INDEX idx_processed_carrier_webhooks_received_at ON processed_carrier_webhooks (received_at);
