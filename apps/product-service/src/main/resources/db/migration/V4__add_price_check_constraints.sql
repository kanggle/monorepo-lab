ALTER TABLE products
    ADD CONSTRAINT chk_products_price CHECK (price >= 0);

ALTER TABLE product_variants
    ADD CONSTRAINT chk_product_variants_additional_price CHECK (additional_price >= 0);
