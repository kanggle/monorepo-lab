-- TASK-BE-536 — PATCH /api/admin/products/{productId}/stock has no client key, no
-- dedupe, and no natural-key guard. AdjustStockService.adjust only validated
-- quantity != 0 and then unconditionally called Inventory.adjustStock(quantity),
-- which ACCUMULATES. A replayed request adjusts stock twice and publishes a second
-- StockChangedPayload.
--
-- Unlike a natural key, "the same delta requested twice" cannot be rejected outright:
-- a genuine "+10 received twice" warehouse event is real (Edge Case), and is
-- byte-identical on the wire to a retry of the first request. Only the client knows
-- which it is — hence a required Idempotency-Key, scoped to the variant whose balance
-- actually changes (not the productId path segment, which a single product can share
-- across many variants).
--
-- One row per accepted stock-adjustment request. UNIQUE (variant_id, idempotency_key) is:
--   * the replay key — a same-key retry finds this row and returns without
--     re-adjusting the inventory or re-publishing StockChanged; and
--   * the CONCURRENCY backstop — two simultaneous duplicates both miss the SELECT,
--     but only one INSERT can commit. The loser gets DataIntegrityViolationException
--     -> 409 IDEMPOTENCY_KEY_CONFLICT.
--
-- NO TTL, deliberately (mirrors payment_refund_request, TASK-BE-535): an expiring
-- record re-opens the double-adjustment window for a client whose retry policy
-- outlives it. Rows are small and bounded by admin stock-adjustment volume.
--
-- FAIL-CLOSED: the dedupe store is product-service's own Postgres, written in the
-- SAME transaction as the stock adjustment — not a separate Redis/lock store that
-- could be down while adjustments keep flowing.

CREATE TABLE stock_adjustment_request (
    id              BIGSERIAL    PRIMARY KEY,
    variant_id      UUID         NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    quantity        INT          NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_stock_adjustment_request_key UNIQUE (variant_id, idempotency_key)
);

CREATE INDEX idx_stock_adjustment_request_variant ON stock_adjustment_request (variant_id, created_at);
