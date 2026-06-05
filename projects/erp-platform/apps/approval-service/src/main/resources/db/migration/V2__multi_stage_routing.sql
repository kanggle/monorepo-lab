-- erp-platform approval-service v2.0 — multi-stage routing + IN_REVIEW
-- (TASK-ERP-BE-012, architecture.md § v2.0 amendment). Additive +
-- backward-compatible: every existing single-approver request becomes a 1-stage
-- route (current_stage_index=0, total_stages=1, one approval_route_stage row).
-- MySQL 8 (InnoDB, utf8mb4) — H2 forbidden (Testcontainers MySQL is the gate).

-- ---------------------------------------------------------------------------
-- approval_request — track the route position. current_stage_index (0-based) is
-- the stage currently pending; total_stages is the route length. Defaults keep
-- every pre-existing request a 1-stage route (no IN_REVIEW). The status CHECK
-- gains IN_REVIEW (non-terminal). approver_id is retained = the CURRENT stage's
-- approver (read back-compat; it follows the advancing stage).
-- ---------------------------------------------------------------------------
ALTER TABLE approval_request
    ADD COLUMN current_stage_index INT NOT NULL DEFAULT 0,
    ADD COLUMN total_stages        INT NOT NULL DEFAULT 1;

ALTER TABLE approval_request
    DROP CHECK ck_approval_status;
ALTER TABLE approval_request
    ADD CONSTRAINT ck_approval_status CHECK (status IN
        ('DRAFT','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','WITHDRAWN'));

-- ---------------------------------------------------------------------------
-- approval_route_stage — the ordered 1~N stages of a request's route. One row
-- per stage; unique (request_id, stage_index). Created at request-create time;
-- fixed for the request lifetime (delegation is v2.1, no in-place mutation).
-- ---------------------------------------------------------------------------
CREATE TABLE approval_route_stage (
    id           VARCHAR(64) NOT NULL,
    tenant_id    VARCHAR(64) NOT NULL,
    request_id   VARCHAR(48) NOT NULL,
    stage_index  INT         NOT NULL,
    approver_id  VARCHAR(64) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_route_stage_request_index UNIQUE (request_id, stage_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_route_stage_request ON approval_route_stage (request_id);

-- Backfill: every existing request becomes a 1-stage route at stage 0 with its
-- existing denormalized approver_id. total_stages already defaults to 1 and
-- current_stage_index to 0 (so existing requests behave exactly as v1.0).
INSERT INTO approval_route_stage (id, tenant_id, request_id, stage_index, approver_id, created_at)
SELECT CONCAT('ars-', id), tenant_id, id, 0, approver_id, created_at
FROM approval_request;

-- ---------------------------------------------------------------------------
-- approval_action — record the stage a transition pertains to (0-based; the
-- stage at which the approve/reject/submit/withdraw happened). Nullable for
-- pre-v2 rows; new transitions always populate it.
-- ---------------------------------------------------------------------------
ALTER TABLE approval_action
    ADD COLUMN stage INT NULL;
