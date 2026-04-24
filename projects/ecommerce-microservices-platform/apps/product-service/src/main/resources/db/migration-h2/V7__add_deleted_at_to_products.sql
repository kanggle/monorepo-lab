ALTER TABLE products ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_products_deleted_at ON products (deleted_at);
