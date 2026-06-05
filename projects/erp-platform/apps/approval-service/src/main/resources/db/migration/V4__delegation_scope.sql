-- erp-platform approval-service v2.3 — per-request delegation scoping
-- (TASK-ERP-BE-017, architecture.md § v2.3 amendment). Pure-additive +
-- backward-compatible: a scope dimension on delegation_grant. Existing rows
-- backfill to scope='GLOBAL' (= today's blanket A→D behavior). MySQL 8 (InnoDB,
-- utf8mb4) — H2 forbidden (Testcontainers MySQL is the authoritative gate; the
-- CHECK constraints below are NOT exercised by the Docker-free :check slice).

-- ---------------------------------------------------------------------------
-- scope: GLOBAL (default, blanket A→D) | REQUEST (narrowed to one approval
-- request via scope_request_id). §16 — the @Enumerated(STRING) VARCHAR fits,
-- but the value set + the scope↔scope_request_id coherence are DB-enforced.
-- ---------------------------------------------------------------------------
ALTER TABLE delegation_grant
    ADD COLUMN scope            VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
    ADD COLUMN scope_request_id VARCHAR(64) NULL;

-- value set pinned (a future value inserted without migration → rejected).
ALTER TABLE delegation_grant
    ADD CONSTRAINT ck_delegation_grant_scope
        CHECK (scope IN ('GLOBAL','REQUEST'));

-- coherence: GLOBAL ⟺ scope_request_id NULL; REQUEST ⟺ scope_request_id NOT NULL.
ALTER TABLE delegation_grant
    ADD CONSTRAINT ck_delegation_grant_scope_req
        CHECK ((scope = 'GLOBAL' AND scope_request_id IS NULL)
            OR (scope = 'REQUEST' AND scope_request_id IS NOT NULL));

-- request-scoped lookup (transition-time resolution narrows by scope_request_id).
CREATE INDEX idx_delegation_scope_request
    ON delegation_grant (tenant_id, delegator_id, delegate_id, scope, scope_request_id);
