-- FK ON DELETE 정책 추가 (H2: 문장 분리)
ALTER TABLE products DROP CONSTRAINT fk_products_category;
ALTER TABLE products ADD CONSTRAINT fk_products_category
    FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;

ALTER TABLE product_variants DROP CONSTRAINT fk_product_variants_product;
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_product
    FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE;

-- 동일 상품 내 option_name 중복 방지
ALTER TABLE product_variants
    ADD CONSTRAINT uq_product_variants_option UNIQUE (product_id, option_name);
