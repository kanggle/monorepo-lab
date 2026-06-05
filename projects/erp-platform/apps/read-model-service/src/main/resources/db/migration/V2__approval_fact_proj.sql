-- erp-platform read-model-service — approval-fact projection (TASK-ERP-BE-010).
-- Extends the integrated read model to project APPROVAL FACTS: the latest state
-- of each approval request, consumed from erp.approval.{submitted,approved,
-- rejected,withdrawn}.v1 (approval-service). Closes the approval-service →
-- read-model event loop (mirrors the masterdata → read-model loop). Read-only,
-- no re-emission (E5 terminal).
--
-- LATEST-STATE upsert keyed on approval_request_id (= aggregateId): ONE row per
-- request, NOT the transition history. The authoritative full history stays with
-- approval-service (GET /api/erp/approval/requests/{id} is the source of record).
-- TERMINAL-ONCE: once status is terminal (APPROVED/REJECTED/WITHDRAWN) a later
-- transition never reverts to SUBMITTED (enforced in the domain projection).
-- submitted_at / finalized_at / last_reason are nullable: ABSENT when not
-- applicable (out-of-order terminal-before-submitted → submitted_at NULL, no
-- fabrication — E5).

CREATE TABLE approval_fact_proj (
    approval_request_id VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    subject_type        VARCHAR(16),
    subject_id          VARCHAR(64),
    approver_id         VARCHAR(64),
    submitter_id        VARCHAR(64),
    submitted_at        DATETIME(6),
    finalized_at        DATETIME(6),
    last_reason         VARCHAR(512),
    last_event_at       DATETIME(6)  NOT NULL,
    last_event_id       VARCHAR(64)  NOT NULL,
    PRIMARY KEY (approval_request_id),
    CONSTRAINT ck_approval_fact_proj_status
        CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
    CONSTRAINT ck_approval_fact_proj_subject_type
        CHECK (subject_type IS NULL OR subject_type IN ('DEPARTMENT','EMPLOYEE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- List filters: status + subject + route-role; the org_scope read filter joins
-- on (subject_type, subject_id) so index those together.
CREATE INDEX idx_approval_fact_proj_status ON approval_fact_proj (status);
CREATE INDEX idx_approval_fact_proj_subject ON approval_fact_proj (subject_type, subject_id);
CREATE INDEX idx_approval_fact_proj_approver ON approval_fact_proj (approver_id);
CREATE INDEX idx_approval_fact_proj_submitter ON approval_fact_proj (submitter_id);
CREATE INDEX idx_approval_fact_proj_last_event ON approval_fact_proj (last_event_at);
