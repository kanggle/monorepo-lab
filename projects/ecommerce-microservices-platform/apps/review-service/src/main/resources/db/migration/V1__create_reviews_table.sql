CREATE TABLE reviews (
    id          UUID PRIMARY KEY,
    user_id     UUID        NOT NULL,
    product_id  UUID        NOT NULL,
    rating      INT         NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title       VARCHAR(255) NOT NULL,
    content     TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_reviews_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_reviews_product_id ON reviews (product_id);
CREATE INDEX idx_reviews_user_id ON reviews (user_id);
CREATE INDEX idx_reviews_product_id_status ON reviews (product_id, status);
