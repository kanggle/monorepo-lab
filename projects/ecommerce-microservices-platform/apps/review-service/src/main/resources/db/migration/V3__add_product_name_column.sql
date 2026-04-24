-- Add product_name column to store product name at review creation time
ALTER TABLE reviews ADD COLUMN product_name VARCHAR(255);
