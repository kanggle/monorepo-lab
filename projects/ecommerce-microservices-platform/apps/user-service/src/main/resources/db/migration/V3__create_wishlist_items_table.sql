CREATE TABLE wishlist_items (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    product_id UUID        NOT NULL,
    added_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_wishlist_items PRIMARY KEY (id),
    CONSTRAINT fk_wishlist_items_user_id FOREIGN KEY (user_id) REFERENCES user_profiles (user_id),
    CONSTRAINT uq_wishlist_items_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_wishlist_items_user_id ON wishlist_items (user_id);
