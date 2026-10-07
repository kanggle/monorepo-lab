-- erp-platform masterdata-service — employee ↔ IAM account link (TASK-ERP-BE-044,
-- umbrella TASK-MONO-774 S2; ADR-MONO-080 D7 = E1). MySQL 8, InnoDB, utf8mb4.
-- Contract: specs/contracts/http/masterdata-api.md § Employee ↔ IAM account link.
--
-- Write path = HR proposal + account-owner acceptance (owner decision 2026-10-08 UTC).
-- Only the accept use case writes employees.account_id. No FK to IAM (cross-context
-- reference) and no IAM existence check at proposal time (owner decision (b)).

-- ---------------------------------------------------------------------------
-- employees.account_id — the IAM account (JWT `sub`) that logs in as this
-- employee. NULL = not linked. One account links to at most ONE employee per
-- tenant: InnoDB UNIQUE treats NULLs as distinct, so any number of unlinked
-- employees coexist under the same constraint.
-- ---------------------------------------------------------------------------
ALTER TABLE employees
    ADD COLUMN account_id VARCHAR(64) NULL AFTER job_grade_id,
    ADD CONSTRAINT uq_employees_tenant_account UNIQUE (tenant_id, account_id);

-- ---------------------------------------------------------------------------
-- employee_account_link_proposals — one proposal = one row. Non-PENDING rows
-- are immutable (the application never updates them; they are the decision
-- history).
--
-- "At most ONE PENDING proposal per employee" is enforced by the DATABASE, not
-- only by an application pre-check (two concurrent proposals would both pass a
-- read-then-insert check). MySQL has no partial index, so the generated column
-- pending_employee_id carries employee_id ONLY while status = 'PENDING' and NULL
-- otherwise; UNIQUE (tenant_id, pending_employee_id) then admits one PENDING row
-- per employee and any number of decided rows (NULLs are distinct).
-- The column is not mapped by JPA (ddl-auto=validate checks mapped columns only).
-- ---------------------------------------------------------------------------
CREATE TABLE employee_account_link_proposals (
    id                   VARCHAR(36)  NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    employee_id          VARCHAR(36)  NOT NULL,
    account_id           VARCHAR(64)  NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    proposed_by          VARCHAR(128) NOT NULL,
    proposed_at          DATETIME(6)  NOT NULL,
    reason               VARCHAR(256),
    decided_by           VARCHAR(128),
    decided_at           DATETIME(6),
    decision_reason      VARCHAR(256),
    version              BIGINT       NOT NULL DEFAULT 0,
    pending_employee_id  VARCHAR(36)
        GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN employee_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uq_link_proposals_one_pending UNIQUE (tenant_id, pending_employee_id),
    CONSTRAINT ck_link_proposals_status
        CHECK (status IN ('PENDING','ACCEPTED','DECLINED','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_link_proposals_account_status
    ON employee_account_link_proposals (tenant_id, account_id, status);
CREATE INDEX idx_link_proposals_employee
    ON employee_account_link_proposals (tenant_id, employee_id, proposed_at);
