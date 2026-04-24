CREATE TABLE orders (
    order_id    VARCHAR(36)  NOT NULL,
    user_id     VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    total_price BIGINT       NOT NULL,
    recipient   VARCHAR(255) NOT NULL,
    phone       VARCHAR(50)  NOT NULL,
    zip_code    VARCHAR(20)  NOT NULL,
    address1    VARCHAR(255) NOT NULL,
    address2    VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
);

CREATE TABLE order_items (
    id           VARCHAR(36)  NOT NULL,
    order_id     VARCHAR(36)  NOT NULL,
    product_id   VARCHAR(255) NOT NULL,
    variant_id   VARCHAR(255) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    option_name  VARCHAR(255),
    quantity     INT          NOT NULL,
    unit_price   BIGINT       NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
