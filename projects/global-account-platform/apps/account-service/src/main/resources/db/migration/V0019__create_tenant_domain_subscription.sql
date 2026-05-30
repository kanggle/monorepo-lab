-- TASK-BE-322 (ADR-MONO-019 § 3.3 step 1 — D2 entitlement authority):
-- The N:M relation between a customer tenant and a federated product/domain.
-- account-service is the source of truth for which tenants are entitled to
-- which domains. admin-service projects this into the platform-console product
-- catalog (ConsoleRegistryUseCase, ADR-019 D4) via the internal read surface.
--
-- domain_key is a product catalog key (gap | wms | scm | erp | finance);
-- NOT a tenant id. The catalog itself is fixed in admin-service
-- (ProductCatalog), so domain_key has no FK — it is a stable enum-like string.
--
-- Engine/charset mirror tenants (V0009): InnoDB + utf8mb4.
CREATE TABLE tenant_domain_subscription (
    tenant_id  VARCHAR(32) NOT NULL,
    domain_key VARCHAR(32) NOT NULL,
    status     VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, domain_key),
    CONSTRAINT fk_tds_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Backward-compatible seed (net-zero, ADR-019 step 1): each domain-slug tenant
-- subscribes to its own domain so the catalog derivation
-- (subscriptions(domain_key) ∩ activeTenants ∩ operator-scope) reproduces the
-- pre-existing `activeTenants.contains(tenantSlug)` binding byte-identically.
--
-- `gap` is intentionally NOT seeded: the `gap` product binds to ALL registered
-- tenants (ProductCatalog.bindsAllTenants=true) on the admin side and never
-- consults this table. A `gap` row here would double-count / confuse semantics.
--
-- INSERT IGNORE keeps the migration idempotent (mirrors V0016/V0017/V0018 seeds);
-- a per-row WHERE-EXISTS against tenants guards against seeding a subscription for
-- a tenant slug that was not registered in this environment.
INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
SELECT t.tenant_id, t.tenant_id, 'ACTIVE', NOW(6), NOW(6)
FROM tenants t
WHERE t.tenant_id IN ('wms', 'scm', 'erp', 'finance');
