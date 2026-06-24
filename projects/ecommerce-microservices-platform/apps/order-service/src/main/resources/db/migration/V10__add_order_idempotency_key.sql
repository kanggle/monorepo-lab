-- TASK-BE-430: order-placement idempotency.
-- Client-supplied Idempotency-Key persisted on the order so a re-submit/retry of
-- the same checkout returns the original order instead of creating a duplicate.
-- Nullable: existing rows and key-less placements stay NULL (backward compatible).
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64);

-- Unique per (tenant, user, key). SQL treats NULLs as distinct, so unlimited
-- NULL-key rows are allowed (no partial-index WHERE needed — H2 + Postgres compatible);
-- only a non-null key collides for the same tenant+user → duplicate placement blocked.
CREATE UNIQUE INDEX uq_orders_idempotency
    ON orders (tenant_id, user_id, idempotency_key);
