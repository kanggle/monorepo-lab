-- Drop the existing unique constraint that prevents re-review after soft delete
ALTER TABLE reviews DROP CONSTRAINT uq_reviews_user_product;

-- Add partial unique index: only ACTIVE reviews enforce uniqueness per user+product
CREATE UNIQUE INDEX uq_reviews_user_product_active
    ON reviews (user_id, product_id)
    WHERE status = 'ACTIVE';
