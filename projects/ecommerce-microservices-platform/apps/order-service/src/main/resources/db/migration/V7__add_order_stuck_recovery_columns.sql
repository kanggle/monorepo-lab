ALTER TABLE orders ADD COLUMN stuck_recovery_attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN stuck_recovery_at TIMESTAMP;

CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
