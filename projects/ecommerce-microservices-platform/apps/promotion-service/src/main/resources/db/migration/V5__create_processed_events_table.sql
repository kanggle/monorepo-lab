CREATE TABLE processed_events (
    event_id    VARCHAR(255) NOT NULL,
    event_type  VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
