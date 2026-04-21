CREATE TABLE user_addresses (
    id             UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    label          VARCHAR(50)  NOT NULL,
    recipient_name VARCHAR(50)  NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    zip_code       VARCHAR(10)  NOT NULL,
    address1       VARCHAR(255) NOT NULL,
    address2       VARCHAR(255),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_addresses PRIMARY KEY (id),
    CONSTRAINT fk_user_addresses_user_id FOREIGN KEY (user_id) REFERENCES user_profiles (user_id)
);

CREATE INDEX idx_user_addresses_user_id ON user_addresses (user_id);
