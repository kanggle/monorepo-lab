ALTER TABLE payments ADD COLUMN payment_key    VARCHAR(200);
ALTER TABLE payments ADD COLUMN payment_method VARCHAR(50);
ALTER TABLE payments ADD COLUMN receipt_url    VARCHAR(500);
