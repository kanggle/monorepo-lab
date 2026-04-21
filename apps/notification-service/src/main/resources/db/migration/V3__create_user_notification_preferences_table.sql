CREATE TABLE user_notification_preferences (
    user_id        VARCHAR(255) NOT NULL PRIMARY KEY,
    email_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    sms_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    push_enabled   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);
