CREATE TABLE notification_templates (
    template_id  VARCHAR(255)  NOT NULL PRIMARY KEY,
    type         VARCHAR(50)  NOT NULL,
    channel      VARCHAR(20)  NOT NULL,
    subject      VARCHAR(500) NOT NULL,
    body         TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT uq_template_type_channel UNIQUE (type, channel)
);
