CREATE TABLE user_profiles (
    id                UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    email             VARCHAR(255) NOT NULL,
    name              VARCHAR(50)  NOT NULL,
    nickname          VARCHAR(50),
    phone             VARCHAR(20),
    profile_image_url VARCHAR(500),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_profiles PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_user_id UNIQUE (user_id)
);
