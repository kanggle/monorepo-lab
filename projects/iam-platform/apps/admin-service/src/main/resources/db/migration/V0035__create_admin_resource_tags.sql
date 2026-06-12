-- TASK-BE-355 (ADR-MONO-029) — RESOURCE_TAG access condition for non-operator
-- resources (tenants, accounts).
--
-- Admin governance tags for resources whose business data is owned by ANOTHER
-- service (account-service owns tenants + accounts; admin-service reaches them
-- only via internal HTTP). To preserve the RESOURCE_TAG anti-spoof invariant
-- (ADR-029 § D2-C — tags MUST come from trusted domain data, never the request)
-- WITHOUT a synchronous cross-service call in the authorization hot path, the
-- governance tags live here, admin-service-local — exactly as operator tags live
-- on admin_operators.tags. A generic (resource_type, resource_id) table so a
-- single table + repository serves every non-operator resource.
--
-- Seed / admin-SQL only: there is no tag-set API (mirrors operators). NULL / empty
-- tags = the resource carries no tags = un-gated under deny-if-present. Net-zero:
-- the gate is opt-in (no forbidden/required tag configured ⇒ no gate), and a
-- resource with no row here resolves to an empty tag set.

CREATE TABLE admin_resource_tags (
    resource_type VARCHAR(32)  NOT NULL COMMENT 'e.g. TENANT, ACCOUNT',
    resource_id   VARCHAR(64)  NOT NULL COMMENT 'the target resource id (tenant_id / account id)',
    tags          VARCHAR(512) NULL     COMMENT 'comma-separated resource tags (e.g. "protected"); NULL/empty = untagged',
    PRIMARY KEY (resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
