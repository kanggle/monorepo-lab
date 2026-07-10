-- TASK-BE-491 (ADR-MONO-047 § D1/D2/D3/D4/D7 — org-node tenant hierarchy):
-- `org_node` is a DATA-LESS grouping node that sits ABOVE `tenant`. A company is
-- an org_node; a service is a tenant. The tree GROUPS tenants — it never NESTS
-- them, so `tenant_id` remains the single flat isolation key (M1) and every
-- row-isolation guard is byte-unchanged. AWS Organizations OU->Account /
-- GCP Folder->Project parity.
--
-- ENTITLEMENT CEILING (D2-A, deny-only): each node carries a MAXIMUM set of
-- domains. The effective ceiling at a tenant is the INTERSECTION down the chain
-- root -> ... -> tenant.org_node. It can only NARROW what a tenant may subscribe
-- to / resolve; it NEVER grants.
--
-- WHY TWO COLUMNS INSTEAD OF ONE `entitlement_ceiling`:
--   ceiling_mode = 'UNBOUNDED'          -> no ceiling; the intersection IDENTITY.
--   ceiling_mode = 'BOUNDED', domains=''-> the EMPTY set; nothing permitted.
-- These two are OPPOSITES, and a single nullable column invites conflating them.
-- Note also that UNBOUNDED must NOT be encoded as "the set of all domains known
-- today" — a domain added later would then be silently excluded from every
-- legacy node. `ceiling_mode` makes the identity element un-conflatable at the
-- storage layer. (The ADR D1 sketch's single `entitlement_ceiling` field IS this
-- pair; the split is an encoding, not a re-decision.)
--
-- Engine/charset mirror tenants (V0009) + tenant_domain_subscription (V0019).
CREATE TABLE org_node (
    id              VARCHAR(36)  NOT NULL,
    parent_id       VARCHAR(36)  NULL,
    name            VARCHAR(100) NOT NULL,
    ceiling_mode    VARCHAR(16)  NOT NULL,
    ceiling_domains VARCHAR(255) NOT NULL DEFAULT '',
    depth           INT          NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_org_node_parent  FOREIGN KEY (parent_id) REFERENCES org_node (id),
    -- D4: root = depth 1, cap 5 (AWS OU parity). The cap is a runaway/cycle guardrail.
    CONSTRAINT ck_org_node_depth   CHECK (depth BETWEEN 1 AND 5),
    -- D4: trivial self-cycle. Longer cycles are unrepresentable through the
    -- application (which walks the ancestor chain on every write) and are also
    -- structurally prevented by depth + the parent FK.
    CONSTRAINT ck_org_node_selfref CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_org_node_mode    CHECK (ceiling_mode IN ('UNBOUNDED', 'BOUNDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_org_node_parent ON org_node (parent_id);

-- D1 + D7: the grouping link. NULLABLE by design.
--
-- NULL = "ungrouped singleton" = the tenant has an UNBOUNDED effective ceiling =
-- byte-identical pre-ADR-047 behaviour. This is what makes the backfill
-- (TASK-BE-493) a behavioural no-op and what keeps a LAZY (never-run) migration
-- legal. Do NOT promote this column to NOT NULL.
ALTER TABLE tenants
    ADD COLUMN org_node_id VARCHAR(36) NULL,
    ADD CONSTRAINT fk_tenants_org_node FOREIGN KEY (org_node_id) REFERENCES org_node (id);

CREATE INDEX idx_tenants_org_node ON tenants (org_node_id);
