CREATE TABLE batch_job_execution_history (
    id              BIGSERIAL       PRIMARY KEY,
    job_name        VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL,
    finished_at     TIMESTAMPTZ,
    error_message   TEXT
);

CREATE INDEX idx_batch_job_execution_history_job_name ON batch_job_execution_history (job_name);
CREATE INDEX idx_batch_job_execution_history_status ON batch_job_execution_history (status);
