-- TASK-INT-026: the coupon applied at placement and the discount it granted.
-- total_price stays the amount the customer pays (net of the discount), so payment-service
-- keeps building the PENDING payment from OrderPlaced.totalPrice unchanged.
-- Existing rows: no coupon, zero discount (the DEFAULT backfills them).
ALTER TABLE orders ADD COLUMN coupon_id VARCHAR(36);
ALTER TABLE orders ADD COLUMN discount_amount BIGINT NOT NULL DEFAULT 0;
