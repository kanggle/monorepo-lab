-- erp-platform approval-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Approval Workflow bounded context first increment (TASK-ERP-BE-009).
-- ApprovalRequest aggregate + single-stage route + transition action history +
-- append-only audit_log + transactional outbox + idempotency. erp E3 (state
-- machine + authorized approver + no self-approval) + E4 (idempotent transition
-- + immutable audit) + E1 (subject reference integrity) + E7 (internal-only) +
-- E8 (audit on every transition). Multi-tenant defense-in-depth: every table
-- carries tenant_id; key indexes prefix tenant_id. Separate tables from
-- masterdata-service (same erp_db instance; no shared-table JOIN).

-- ---------------------------------------------------------------------------
-- approval_request — aggregate root. status flows only through the state
-- machine (DRAFT → SUBMITTED → APPROVED|REJECTED|WITHDRAWN). @Version gives
-- optimistic locking (T5). The single-stage route (approver) is denormalized
-- onto the request; submitter is the create-time actor. No self-approval is
-- enforced in the domain (submitter ≠ approver).
-- ---------------------------------------------------------------------------
CREATE TABLE approval_request (
    id              VARCHAR(48)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    subject_type    VARCHAR(16)  NOT NULL,
    subject_id      VARCHAR(64)  NOT NULL,
    title           VARCHAR(256) NOT NULL,
    creation_reason VARCHAR(512),
    approver_id     VARCHAR(64)  NOT NULL,
    submitter_id    VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    submitted_at    DATETIME(6),
    finalized_at    DATETIME(6),
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT ck_approval_subject_type CHECK (subject_type IN ('DEPARTMENT','EMPLOYEE')),
    CONSTRAINT ck_approval_status CHECK (status IN
        ('DRAFT','SUBMITTED','APPROVED','REJECTED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_approval_tenant_status ON approval_request (tenant_id, status);
CREATE INDEX idx_approval_tenant_approver_status ON approval_request (tenant_id, approver_id, status);
CREATE INDEX idx_approval_tenant_submitter ON approval_request (tenant_id, submitter_id);

-- ---------------------------------------------------------------------------
-- approval_action — one row per accepted transition (submit / approve / reject
-- / withdraw). Drives the response `history` array. actor = JWT sub of the
-- transition actor; reason required on reject + withdraw. Append-only.
-- ---------------------------------------------------------------------------
CREATE TABLE approval_action (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id            VARCHAR(64) NOT NULL,
    approval_request_id  VARCHAR(48) NOT NULL,
    transition           VARCHAR(16) NOT NULL,
    actor                VARCHAR(128) NOT NULL,
    reason               VARCHAR(512),
    occurred_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_approval_action_transition CHECK (transition IN
        ('SUBMITTED','APPROVED','REJECTED','WITHDRAWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_approval_action_request
    ON approval_action (tenant_id, approval_request_id, occurred_at);

-- ---------------------------------------------------------------------------
-- approval_audit_log — append-only application audit trail (E2 / E4 / E8).
-- No UPDATE/DELETE path; written in the SAME Tx as the state change + outbox
-- row. before_state / after_state are JSON status snapshots. Application-layer
-- guard via ApprovalAuditLogRepository#append (the only exposed write method).
-- ---------------------------------------------------------------------------
CREATE TABLE approval_audit_log (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    event_id             VARCHAR(48) NOT NULL,
    tenant_id            VARCHAR(64) NOT NULL,
    aggregate_type       VARCHAR(40) NOT NULL,
    aggregate_id         VARCHAR(48) NOT NULL,
    action               VARCHAR(40) NOT NULL,
    actor                VARCHAR(128) NOT NULL,
    before_state         JSON,
    after_state          JSON,
    reason               VARCHAR(512),
    outcome              VARCHAR(16) NOT NULL,
    occurred_at          DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_approval_audit_event UNIQUE (event_id),
    CONSTRAINT ck_approval_audit_outcome CHECK (outcome IN ('SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_approval_audit_tenant_aggregate
    ON approval_audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
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
-- idempotency_keys — DB-PK-authoritative idempotency claim gate (FIN-BE-004
-- final form, architecture.md § Idempotency). Composite PK is the concurrency
-- gate; same key + identical payload → REPLAY; different payload → CONFLICT.
-- Key scope = (idempotency_key, endpoint, tenant_id).
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
