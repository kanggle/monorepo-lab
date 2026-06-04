-- erp-platform read-model-service initial schema (MySQL 8, InnoDB, utf8mb4).
-- Integrated read model — denormalized projection of masterdata-service's
-- organization master facts (department / employee / job grade / cost center).
-- Each consumer upserts its OWN single projection table keyed by aggregateId;
-- the employee org-view is assembled at READ time by joining the four tables
-- (department path resolved by walking parent_id, depth-bounded). RETIRED is a
-- logical status, NEVER a delete (erp E2 / read-model-subscriptions.md). The
-- single source of record for every projected field is masterdata-service (E5).
-- TASK-ERP-BE-007.

-- ---------------------------------------------------------------------------
-- department_proj — department projection. parent_id self-reference drives the
-- read-time ancestry walk for the org-view department path. PARENT_MOVED
-- upserts the new parent_id (no fan-out re-stamp; path is read-time).
-- ---------------------------------------------------------------------------
CREATE TABLE department_proj (
    id              VARCHAR(36)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    parent_id       VARCHAR(36),
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE,
    effective_to    DATE,
    last_event_at   DATETIME(6)  NOT NULL,
    last_event_id   VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_department_proj_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_department_proj_parent ON department_proj (parent_id);
CREATE INDEX idx_department_proj_status ON department_proj (status);

-- ---------------------------------------------------------------------------
-- cost_center_proj — cost center projection (references a department).
-- ---------------------------------------------------------------------------
CREATE TABLE cost_center_proj (
    id              VARCHAR(36)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    department_id   VARCHAR(36),
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE,
    effective_to    DATE,
    last_event_at   DATETIME(6)  NOT NULL,
    last_event_id   VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_cost_center_proj_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cost_center_proj_status ON cost_center_proj (status);

-- ---------------------------------------------------------------------------
-- job_grade_proj — job grade projection (salary-grade ordering).
-- ---------------------------------------------------------------------------
CREATE TABLE job_grade_proj (
    id              VARCHAR(36)  NOT NULL,
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE,
    effective_to    DATE,
    last_event_at   DATETIME(6)  NOT NULL,
    last_event_id   VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_job_grade_proj_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_job_grade_proj_status_order ON job_grade_proj (status, display_order);

-- ---------------------------------------------------------------------------
-- employee_proj — employee projection. References department / cost_center /
-- job_grade; the references are resolved at READ time (an unconsumed reference
-- resolves to null + meta.unresolved, never fabricated — E5).
-- ---------------------------------------------------------------------------
CREATE TABLE employee_proj (
    id              VARCHAR(36)  NOT NULL,
    employee_number VARCHAR(64)  NOT NULL,
    name            VARCHAR(256) NOT NULL,
    department_id   VARCHAR(36),
    cost_center_id  VARCHAR(36),
    job_grade_id    VARCHAR(36),
    status          VARCHAR(16)  NOT NULL,
    effective_from  DATE,
    effective_to    DATE,
    last_event_at   DATETIME(6)  NOT NULL,
    last_event_id   VARCHAR(64)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_employee_proj_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_employee_proj_status ON employee_proj (status);
CREATE INDEX idx_employee_proj_department ON employee_proj (department_id, status);

-- ---------------------------------------------------------------------------
-- processed_events — consumer idempotency dedupe store (T8). Keyed on the
-- envelope eventId; a duplicate eventId is skipped without mutation so
-- re-delivery leaves the projection byte-identical. This is the read-model's
-- processing provenance (there is no audit_log — read-only, E5). Distinct from
-- libs/java-messaging's ProcessedEventJpaEntity (whose outbox auto-config is
-- excluded — read-model is no-outbox).
-- ---------------------------------------------------------------------------
CREATE TABLE processed_events (
    event_id        VARCHAR(64)  NOT NULL,
    topic           VARCHAR(200) NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    processed_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_processed_events_topic ON processed_events (topic, processed_at);
