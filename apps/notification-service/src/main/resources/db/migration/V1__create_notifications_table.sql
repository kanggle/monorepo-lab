CREATE TABLE notifications (
    notification_id  VARCHAR(255)  NOT NULL PRIMARY KEY,
    user_id          VARCHAR(255)  NOT NULL,
    channel          VARCHAR(20)  NOT NULL,
    subject          VARCHAR(500) NOT NULL,
    body             TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    event_id         VARCHAR(255),
    retry_count      INTEGER      NOT NULL DEFAULT 0,
    sent_at          TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_event_id ON notifications(event_id);
CREATE INDEX idx_notifications_status ON notifications(status);
