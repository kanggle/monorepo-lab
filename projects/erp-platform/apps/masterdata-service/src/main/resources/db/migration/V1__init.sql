-- erp-platform masterdata-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Hexagonal aggregate roots: Department, Employee, JobGrade, CostCenter,
-- BusinessPartner. erp E1 (reference integrity, hierarchical cycle-free) +
-- E2 (effective-dating, append-only audit_log) + E6 (authorization matrix +
-- data scope) + E7 (internal-only) + E8 (audit on every mutation).
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id.
-- TASK-ERP-BE-001.

-- ---------------------------------------------------------------------------
-- departments — hierarchical aggregate root (parent_id self-ref).
-- effective_from / effective_to give point-in-time reads (E2). Logical retire
-- only (E1) — physical delete blocked at the application layer.
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    parent_id       VARCHAR(36),
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_departments_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_departments_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_departments_tenant_status ON departments (tenant_id, status);
CREATE INDEX idx_departments_parent ON departments (parent_id, tenant_id, status);
CREATE INDEX idx_departments_effective ON departments (tenant_id, effective_from, effective_to);

-- ---------------------------------------------------------------------------
-- employees — organization attributes. References department, cost_center,
-- job_grade — reference integrity enforced at the application layer (E1).
-- ---------------------------------------------------------------------------
CREATE TABLE employees (
    id                VARCHAR(36)  NOT NULL,
    tenant_id         VARCHAR(64)  NOT NULL,
    employee_number   VARCHAR(64)  NOT NULL,
    name              VARCHAR(256) NOT NULL,
    department_id     VARCHAR(36)  NOT NULL,
    cost_center_id    VARCHAR(36)  NOT NULL,
    job_grade_id      VARCHAR(36)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    effective_from    DATE         NOT NULL,
    effective_to      DATE,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_employees_tenant_number UNIQUE (tenant_id, employee_number),
    CONSTRAINT ck_employees_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_employees_tenant_status ON employees (tenant_id, status);
CREATE INDEX idx_employees_department ON employees (department_id, tenant_id, status);
CREATE INDEX idx_employees_cost_center ON employees (cost_center_id, tenant_id, status);
CREATE INDEX idx_employees_job_grade ON employees (job_grade_id, tenant_id, status);
CREATE INDEX idx_employees_effective ON employees (tenant_id, effective_from, effective_to);

-- ---------------------------------------------------------------------------
-- job_grades — salary-grade ordering. Logical retire only.
-- ---------------------------------------------------------------------------
CREATE TABLE job_grades (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    display_order   INT          NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_job_grades_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_job_grades_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_job_grades_tenant_status_order ON job_grades (tenant_id, status, display_order);

-- ---------------------------------------------------------------------------
-- cost_centers — references one department per effective revision.
-- ---------------------------------------------------------------------------
CREATE TABLE cost_centers (
    id              VARCHAR(36)  NOT NULL,
    tenant_id       VARCHAR(64)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    department_id   VARCHAR(36)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE         NOT NULL,
    effective_to    DATE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_cost_centers_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_cost_centers_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cost_centers_tenant_status ON cost_centers (tenant_id, status);
CREATE INDEX idx_cost_centers_department ON cost_centers (department_id, tenant_id, status);

-- ---------------------------------------------------------------------------
-- business_partners — external counterparty (customer / supplier / both).
-- payment_terms embedded (term_days + method).
-- ---------------------------------------------------------------------------
CREATE TABLE business_partners (
    id                  VARCHAR(36)  NOT NULL,
    tenant_id           VARCHAR(64)  NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    name                VARCHAR(256) NOT NULL,
    partner_type        VARCHAR(16)  NOT NULL,
    term_days           INT,
    method              VARCHAR(32),
    status              VARCHAR(16)  NOT NULL,
    effective_from      DATE         NOT NULL,
    effective_to        DATE,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_business_partners_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_business_partners_status CHECK (status IN ('ACTIVE','RETIRED')),
    CONSTRAINT ck_business_partners_type CHECK (partner_type IN ('CUSTOMER','SUPPLIER','BOTH')),
    CONSTRAINT ck_business_partners_method CHECK (method IS NULL OR method IN
        ('BANK_TRANSFER','CREDIT_CARD','CASH','CHECK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_business_partners_tenant_status ON business_partners (tenant_id, status);
CREATE INDEX idx_business_partners_type ON business_partners (tenant_id, partner_type, status);

-- ---------------------------------------------------------------------------
-- audit_log — append-only application audit trail (E2 / E8). No UPDATE/DELETE
-- path; written in the same Tx as the master mutation. Application-layer
-- guard via {@link AuditLogRepository#append} — the only exposed write method.
-- before_state / after_state are JSON snapshots of the prior / new effective
-- revision (architecture.md § Outbox + audit_log invariants).
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id         VARCHAR(64) NOT NULL,
    aggregate_type    VARCHAR(40) NOT NULL,
    aggregate_id      VARCHAR(36) NOT NULL,
    action            VARCHAR(40) NOT NULL,
    actor             VARCHAR(128) NOT NULL,
    before_state      JSON,
    after_state       JSON,
    reason            VARCHAR(256),
    occurred_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_audit_tenant_aggregate
    ON audit_log (tenant_id, aggregate_type, aggregate_id, occurred_at);

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
