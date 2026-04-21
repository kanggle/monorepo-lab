CREATE TABLE products (
    id          UUID          NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    price       BIGINT        NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    category_id UUID,
    created_at  TIMESTAMP   NOT NULL,
    updated_at  TIMESTAMP   NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);

CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_status ON products (status);
