CREATE TABLE product_variants (
    id               UUID         NOT NULL,
    product_id       UUID         NOT NULL,
    option_name      VARCHAR(100) NOT NULL,
    stock            INT          NOT NULL DEFAULT 0,
    additional_price BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_variants PRIMARY KEY (id),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_product_variants_stock CHECK (stock >= 0)
);

CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
