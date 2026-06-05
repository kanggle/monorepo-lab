-- erp-platform read-model-service — delegation-fact projection (TASK-ERP-BE-015).
-- Extends the integrated read model to project DELEGATION FACTS: the latest state
-- of each delegation grant (ACTIVE/REVOKED), consumed from
-- erp.approval.delegated.v1 (grant create) + erp.approval.delegation.revoked.v1
-- (grant revoke, NEW producer leg this task). Closes the approval-service →
-- read-model delegation event loop (mirrors the approval-fact loop, V2). Read-only,
-- no re-emission (E5 terminal).
--
-- LATEST-STATE upsert keyed on grant_id (= aggregateId): ONE row per grant, NOT
-- the audit history. The authoritative grant state + history stay with
-- approval-service. STICKY-TERMINAL REVOKED: once status is REVOKED a later
-- delegated event never reverts to ACTIVE (enforced in the domain projection).
-- valid_from / valid_to / reason / revoked_at are nullable: ABSENT when not
-- applicable (out-of-order revoke-before-grant → valid_from/valid_to NULL, no
-- fabrication — E5; the revoke payload carries no validity window).

CREATE TABLE delegation_fact_proj (
    grant_id      VARCHAR(64)  NOT NULL,
    delegator_id  VARCHAR(64),
    delegate_id   VARCHAR(64),
    valid_from    DATETIME(6),
    valid_to      DATETIME(6),
    status        VARCHAR(32)  NOT NULL,
    reason        VARCHAR(512),
    revoked_at    DATETIME(6),
    last_event_at DATETIME(6)  NOT NULL,
    last_event_id VARCHAR(64)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'erp',
    PRIMARY KEY (grant_id),
    CONSTRAINT ck_delegation_fact_proj_status
        CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- List filters: delegator / delegate / status; the activeAt filter ranges over
-- (valid_from, valid_to) so index those together. last_event_at is the list sort.
CREATE INDEX idx_delegation_fact_proj_delegator ON delegation_fact_proj (delegator_id);
CREATE INDEX idx_delegation_fact_proj_delegate ON delegation_fact_proj (delegate_id);
CREATE INDEX idx_delegation_fact_proj_status ON delegation_fact_proj (status);
CREATE INDEX idx_delegation_fact_proj_validity ON delegation_fact_proj (valid_from, valid_to);
CREATE INDEX idx_delegation_fact_proj_last_event ON delegation_fact_proj (last_event_at);
