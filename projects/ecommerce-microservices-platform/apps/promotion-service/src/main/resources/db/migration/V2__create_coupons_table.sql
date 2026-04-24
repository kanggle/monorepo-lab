CREATE TABLE coupons (
    coupon_id       VARCHAR(36)     NOT NULL,
    promotion_id    VARCHAR(36)     NOT NULL,
    user_id         VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    issued_at       TIMESTAMP       NOT NULL,
    used_at         TIMESTAMP,
    expired_at      TIMESTAMP,
    expires_at      TIMESTAMP,
    order_id        VARCHAR(36),
    CONSTRAINT pk_coupons PRIMARY KEY (coupon_id),
    CONSTRAINT fk_coupons_promotion FOREIGN KEY (promotion_id) REFERENCES promotions (promotion_id)
);

CREATE INDEX idx_coupons_user_id ON coupons (user_id);
CREATE INDEX idx_coupons_promotion_id ON coupons (promotion_id);
CREATE INDEX idx_coupons_status_expires ON coupons (status, expires_at);
