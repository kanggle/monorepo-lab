-- finance-platform account-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Hexagonal aggregate roots: Account, Balance, Hold, Transaction.
-- fintech F1–F8: integer minor-unit money (BIGINT) + ISO currency CHAR(3);
-- append-only audit_log / account_status_history (no UPDATE/DELETE path);
-- transactional outbox; idempotency_keys; operator review queue (no auto-clear).
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id.
-- TASK-FIN-BE-001.

-- ---------------------------------------------------------------------------
-- accounts — aggregate root, state machine. owner_ref is regulated PII (F7)
-- stored AES-256-GCM encrypted (Base64 envelope) — never plaintext.
-- ---------------------------------------------------------------------------
CREATE TABLE accounts (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    owner_ref       VARCHAR(512) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    kyc_level       VARCHAR(10)  NOT NULL,
    currency        CHAR(3)      NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_accounts_status CHECK (status IN
        ('PENDING_KYC','ACTIVE','RESTRICTED','FROZEN','CLOSED')),
    CONSTRAINT ck_accounts_kyc CHECK (kyc_level IN ('NONE','BASIC','FULL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_accounts_tenant_status ON accounts (tenant_id, status);

-- ---------------------------------------------------------------------------
-- account_status_history — append-only (F6). No UPDATE/DELETE path: the
-- adapter only inserts; written in the same Tx as the accounts.status change.
-- ---------------------------------------------------------------------------
CREATE TABLE account_status_history (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    account_id        VARCHAR(36) NOT NULL,
    tenant_id         VARCHAR(64) NOT NULL,
    from_status       VARCHAR(20) NOT NULL,
    to_status         VARCHAR(20) NOT NULL,
    actor_type        VARCHAR(20) NOT NULL,
    actor_account_id  VARCHAR(64),
    reason            VARCHAR(256),
    occurred_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_ash_actor_type CHECK (actor_type IN
        ('HOLDER','OPERATOR','COMPLIANCE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_ash_tenant_account_occurred
    ON account_status_history (tenant_id, account_id, occurred_at);

-- ---------------------------------------------------------------------------
-- balances — per (account_id, currency). available = ledger − held (F2),
-- never negative (enforced in the domain VO + application single writer).
-- Money columns BIGINT minor units + currency CHAR(3).
-- ---------------------------------------------------------------------------
CREATE TABLE balances (
    id              VARCHAR(36)  NOT NULL,
    account_id      VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    currency        CHAR(3)      NOT NULL,
    ledger_minor    BIGINT       NOT NULL DEFAULT 0,
    held_minor      BIGINT       NOT NULL DEFAULT 0,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_balances_account_currency UNIQUE (tenant_id, account_id, currency),
    CONSTRAINT ck_balances_non_negative
        CHECK (ledger_minor >= 0 AND held_minor >= 0 AND ledger_minor - held_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_balances_tenant_account ON balances (tenant_id, account_id);

-- ---------------------------------------------------------------------------
-- holds — fund holds against available balance.
-- ---------------------------------------------------------------------------
CREATE TABLE holds (
    id              VARCHAR(36)  NOT NULL,
    account_id      VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    amount_minor    BIGINT       NOT NULL,
    currency        CHAR(3)      NOT NULL,
    captured_minor  BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(10)  NOT NULL,
    reason          VARCHAR(256),
    expires_at      DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    settled_at      DATETIME(6),
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_holds_status CHECK (status IN
        ('ACTIVE','CAPTURED','RELEASED','EXPIRED')),
    CONSTRAINT ck_holds_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT ck_holds_captured CHECK (captured_minor >= 0 AND captured_minor <= amount_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_holds_tenant_account ON holds (tenant_id, account_id);
CREATE INDEX idx_holds_status_expires ON holds (status, expires_at);

-- ---------------------------------------------------------------------------
-- transactions — aggregate root, state machine. SETTLED/COMPLETED immutable
-- (F3): correction is a new REVERSAL row referencing reversal_of_transaction_id.
-- ---------------------------------------------------------------------------
CREATE TABLE transactions (
    id                          VARCHAR(36)  NOT NULL,
    tenant_id                   VARCHAR(64)  NOT NULL,
    account_id                  VARCHAR(36)  NOT NULL,
    type                        VARCHAR(20)  NOT NULL,
    status                      VARCHAR(20)  NOT NULL,
    amount_minor                BIGINT       NOT NULL,
    currency                    CHAR(3)      NOT NULL,
    counterparty_account_id     VARCHAR(36),
    hold_id                     VARCHAR(36),
    reversal_of_transaction_id  VARCHAR(36),
    failure_code                VARCHAR(64),
    reason                      VARCHAR(256),
    created_at                  DATETIME(6)  NOT NULL,
    settled_at                  DATETIME(6),
    version                     BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_txn_type CHECK (type IN
        ('TOPUP','WITHDRAW','TRANSFER','HOLD','CAPTURE','RELEASE','REVERSAL')),
    CONSTRAINT ck_txn_status CHECK (status IN
        ('REQUESTED','VALIDATED','AUTHORIZED','SETTLED','COMPLETED','FAILED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_txn_tenant_account_created
    ON transactions (tenant_id, account_id, created_at);
CREATE INDEX idx_txn_tenant_status ON transactions (tenant_id, status);

-- ---------------------------------------------------------------------------
-- audit_log — append-only application audit trail (F6). No UPDATE/DELETE
-- path; written in the same Tx as the business state change.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         VARCHAR(64) NOT NULL,
    aggregate_type    VARCHAR(40) NOT NULL,
    aggregate_id      VARCHAR(36) NOT NULL,
    action            VARCHAR(40) NOT NULL,
    actor_account_id  VARCHAR(64),
    actor_type        VARCHAR(20) NOT NULL,
    before_state      JSON,
    after_state       JSON,
    reason            VARCHAR(256),
    occurred_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_audit_actor_type CHECK (actor_type IN
        ('HOLDER','OPERATOR','COMPLIANCE','SYSTEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_audit_tenant_aggregate
    ON audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);

-- ---------------------------------------------------------------------------
-- compliance_review_queue — operator queue for sanction hits / discrepancies
-- (F4/F8). Never auto-cleared; v1 only inserts OPEN rows (no resolve path).
-- ---------------------------------------------------------------------------
CREATE TABLE compliance_review_queue (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    account_id      VARCHAR(36)  NOT NULL,
    transaction_id  VARCHAR(36),
    review_type     VARCHAR(40)  NOT NULL,
    screening_ref   VARCHAR(128),
    status          VARCHAR(20)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_crq_status CHECK (status IN ('OPEN','RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_crq_tenant_status ON compliance_review_queue (tenant_id, status);

-- ---------------------------------------------------------------------------
-- reconciliation_discrepancy — F8 forward-declared model. v1 has no external
-- settlement source; discrepancies (when a v2 source exists) enter the
-- operator queue and are NEVER auto-closed. Modelled here so v2 does not
-- re-derive the policy.
-- ---------------------------------------------------------------------------
CREATE TABLE reconciliation_discrepancy (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    account_id      VARCHAR(36),
    expected_minor  BIGINT       NOT NULL,
    actual_minor    BIGINT       NOT NULL,
    currency        CHAR(3)      NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    detected_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_recon_status CHECK (status IN ('OPEN','UNDER_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_recon_tenant_status ON reconciliation_discrepancy (tenant_id, status);

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
-- Keep field names + types in sync with libs/java-messaging.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    published_at    DATETIME(6),
    status          VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);

-- processed_events — required by libs/java-messaging ProcessedEventJpaEntity.
CREATE TABLE processed_events (
    event_id        VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- idempotency_keys — F1 persistent fallback (Redis is the primary). Key =
-- (idempotency_key, endpoint, tenant_id). Fail-CLOSED tertiary layer.
-- ---------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    idempotency_key  VARCHAR(80)  NOT NULL,
    endpoint         VARCHAR(120) NOT NULL,
    tenant_id        VARCHAR(64)  NOT NULL,
    payload_hash     VARCHAR(64)  NOT NULL,
    response_status  INT          NOT NULL,
    response_body    TEXT,
    created_at       DATETIME(6)  NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (idempotency_key, endpoint, tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
