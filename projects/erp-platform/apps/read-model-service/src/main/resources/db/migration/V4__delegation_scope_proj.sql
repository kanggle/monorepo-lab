-- erp-platform read-model-service — delegation scope projection (TASK-ERP-BE-018).
-- Adds the grant-time scoping projected from erp.approval.delegated.v1 (the
-- producer-only forward fields added by TASK-ERP-BE-017): scope (GLOBAL = blanket
-- delegation; REQUEST = narrowed to one approvalRequestId) + scope_request_id (the
-- target approvalRequestId, present only when scope=REQUEST).
--
-- BOTH columns are NULLABLE (unlike approval-service where scope is NOT NULL): an
-- out-of-order revoke-before-grant row legitimately has scope=NULL (the revoke
-- payload carries no scope — never fabricated, E5; a later delegated event fills it
-- WITHOUT reverting the REVOKED status). scope is grant-time IMMUTABLE — projected
-- from the delegated event and preserved across a later revoke (same handling as
-- valid_from / valid_to).
--
-- §16: the scope VALUE SET is DB-pinned by ck_delegation_fact_proj_scope so a
-- future producer value (emitted without a migration) is rejected at the DB rather
-- than silently projected — but NULL is allowed (revoke-only row). NO coherence
-- CHECK on scope <-> scope_request_id: the producer (TASK-ERP-BE-017) already
-- guarantees the pairing (GLOBAL => scope_request_id NULL; REQUEST => set;
-- revoke-only => both NULL), and the read-model only projects producer-validated
-- data (E5).

ALTER TABLE delegation_fact_proj
    ADD COLUMN scope            VARCHAR(16) NULL,
    ADD COLUMN scope_request_id VARCHAR(64) NULL;

ALTER TABLE delegation_fact_proj
    ADD CONSTRAINT ck_delegation_fact_proj_scope
        CHECK (scope IS NULL OR scope IN ('GLOBAL','REQUEST'));
