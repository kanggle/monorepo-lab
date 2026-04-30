-- TASK-BE-030: bulk-lock idempotency store.
--
-- Stores the (operator, idempotency-key) → canonical response body mapping so
-- that identical retries return the same response without re-executing the
-- batch. The request-hash column lets us detect payload drift under the same
-- key and respond 409 IDEMPOTENCY_KEY_CONFLICT.
--
-- Retention policy (TTL) is intentionally out-of-scope for this task; a
-- separate cleanup job will prune rows older than the contractual replay
-- window. Rows are append-only from the application's perspective.

CREATE TABLE admin_bulk_lock_idempotency (
    operator_id     BIGINT        NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL,
    request_hash    CHAR(64)      NOT NULL,
    response_body   TEXT          NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (operator_id, idempotency_key),
    CONSTRAINT fk_admin_bulk_lock_idemp_operator
        FOREIGN KEY (operator_id) REFERENCES admin_operators(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
